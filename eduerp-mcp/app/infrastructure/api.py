"""Starlette exception handlers, request body limits, and structured access logging."""

from __future__ import annotations

import time
import uuid
from typing import Any, TypeVar

T = TypeVar("T")

from pydantic import ValidationError
from sqlalchemy.exc import SQLAlchemyError
from starlette.applications import Starlette
from starlette.exceptions import HTTPException
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.middleware.cors import CORSMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse, Response
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from app.shared.constants import AccessLogPolicy, ApiLimits, CommonErrorCodes, LogEvents
from app.shared.errors import ApiError
from app.shared.exceptions import BusinessException
from app.shared.logging_config import get_logger

_log = get_logger(AccessLogPolicy.LOGGER)


class _BodyTooLargeError(Exception):
    pass


class RequestBodyLimitMiddleware:
    """Reject HTTP bodies larger than ApiLimits.BODY_BYTES before buffering."""

    def __init__(self, app: ASGIApp, default_limit: int = ApiLimits.BODY_BYTES) -> None:
        self._app = app
        self._default_limit = default_limit

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self._app(scope, receive, send)
            return

        limit = scope.get("state", {}).get(ApiLimits.BODY_LIMIT_STATE, self._default_limit)
        headers = {k.lower(): v for k, v in scope.get("headers", [])}
        if b"content-length" in headers:
            try:
                if int(headers[b"content-length"].decode("latin-1")) > limit:
                    resp = ApiError.response(
                        413, "Payload too large", code=CommonErrorCodes.PAYLOAD_TOO_LARGE
                    )
                    await resp(scope, receive, send)
                    return
            except ValueError:
                pass

        seen = 0

        async def limited_receive() -> Message:
            nonlocal seen
            msg = await receive()
            if msg["type"] == "http.request":
                body = msg.get("body", b"")
                seen += len(body)
                if seen > limit:
                    raise _BodyTooLargeError()
            return msg

        try:
            await self._app(scope, limited_receive, send)
        except _BodyTooLargeError:
            resp = ApiError.response(
                413, "Payload too large", code=CommonErrorCodes.PAYLOAD_TOO_LARGE
            )
            await resp(scope, receive, send)


class AccessLogMiddleware(BaseHTTPMiddleware):
    """Structured request log without query strings or sensitive headers."""

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        path = request.url.path
        if path in AccessLogPolicy.SKIP_PATHS:
            return await call_next(request)

        raw_req_id = request.headers.get("x-request-id", "")
        request_id = (
            raw_req_id
            if AccessLogPolicy.REQUEST_ID_PATTERN.fullmatch(raw_req_id)
            else uuid.uuid4().hex[:16]
        )
        started = time.perf_counter()
        try:
            response = await call_next(request)
        except Exception:
            duration_ms = round(
                (time.perf_counter() - started) * AccessLogPolicy.MS_PER_SECOND,
                AccessLogPolicy.DURATION_DIGITS,
            )
            _log.error(
                LogEvents.API_REQUEST_FAILED,
                method=request.method,
                path=path,
                request_id=request_id,
                duration_ms=duration_ms,
            )
            raise

        duration_ms = round(
            (time.perf_counter() - started) * AccessLogPolicy.MS_PER_SECOND,
            AccessLogPolicy.DURATION_DIGITS,
        )
        response.headers.setdefault("X-Request-Id", request_id)
        _log.info(
            LogEvents.API_REQUEST,
            method=request.method,
            path=path,
            status_code=response.status_code,
            request_id=request_id,
            duration_ms=duration_ms,
        )
        return response


async def _handle_business_exception(_request: Request, exc: Exception) -> JSONResponse:
    return ApiError.from_exception(exc)


async def _handle_validation_error(_request: Request, exc: Exception) -> JSONResponse:
    if isinstance(exc, ValidationError):
        return ApiError.from_validation(exc.errors())  # type: ignore[arg-type]
    return ApiError.response(422, ApiLimits.INVALID_REQUEST, code=CommonErrorCodes.VALIDATION_FAILED)


async def _handle_http_exception(_request: Request, exc: Exception) -> JSONResponse:
    if isinstance(exc, HTTPException):
        detail = exc.detail if isinstance(exc.detail, str) else ApiLimits.INVALID_REQUEST
        return ApiError.from_detail(exc.status_code, detail)
    return ApiError.response(500, ApiLimits.INTERNAL_ERROR, code=CommonErrorCodes.INTERNAL_ERROR)


async def _handle_store_unavailable(_request: Request, _exc: Exception) -> JSONResponse:
    _log.error(LogEvents.STORE_UNAVAILABLE)
    return ApiError.response(503, ApiLimits.INTERNAL_ERROR, code=CommonErrorCodes.INTERNAL_ERROR)


async def _handle_unexpected(_request: Request, _exc: Exception) -> JSONResponse:
    _log.exception(LogEvents.UNEXPECTED_ERROR)
    return ApiError.response(500, ApiLimits.INTERNAL_ERROR, code=CommonErrorCodes.INTERNAL_ERROR)


def install_api_infrastructure(
    app: Starlette,
    allowed_origins: list[str] | tuple[str, ...] = ("*",),
) -> None:
    origins = [o.strip() for o in allowed_origins if o.strip()]
    allow_all = not origins or "*" in origins
    app.add_middleware(
        CORSMiddleware,
        allow_origins=[] if allow_all else origins,
        allow_origin_regex=".*" if allow_all else None,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
        allow_headers=["*"],
        expose_headers=["X-Request-Id", "X-CSRF-Token"],
    )
    app.add_middleware(AccessLogMiddleware)
    app.add_middleware(RequestBodyLimitMiddleware)
    app.add_exception_handler(BusinessException, _handle_business_exception)
    app.add_exception_handler(ValidationError, _handle_validation_error)
    app.add_exception_handler(HTTPException, _handle_http_exception)
    app.add_exception_handler(SQLAlchemyError, _handle_store_unavailable)
    app.add_exception_handler(Exception, _handle_unexpected)


def parse_model(model_cls: type[T], payload: Any) -> T:
    return model_cls.model_validate(payload)  # type: ignore[attr-defined]
