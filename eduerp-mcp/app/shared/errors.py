"""Shared HTTP error construction."""

from __future__ import annotations

import re
from collections.abc import Mapping
from typing import Any

from pydantic.alias_generators import to_camel
from starlette.responses import JSONResponse

from app.shared.base.markers import helper, rule
from app.shared.constants import CommonContextKeys, CommonErrorCodes, CommonErrorMessages
from app.shared.exceptions import BusinessException


class ErrorCodeRules:
    """Rules for resolving error codes."""

    _STATUS_CODES = {
        400: CommonErrorCodes.BAD_REQUEST,
        401: CommonErrorCodes.UNAUTHORIZED,
        403: CommonErrorCodes.FORBIDDEN,
        404: CommonErrorCodes.NOT_FOUND,
        409: CommonErrorCodes.CONFLICT,
        410: CommonErrorCodes.GONE,
        413: CommonErrorCodes.PAYLOAD_TOO_LARGE,
        415: CommonErrorCodes.UNSUPPORTED_MEDIA_TYPE,
        422: CommonErrorCodes.VALIDATION_FAILED,
        429: CommonErrorCodes.TOO_MANY_REQUESTS,
    }

    _CODE_SHAPE = re.compile(r"^[A-Z0-9_]+$|^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")

    @staticmethod
    @rule
    def for_status(status_code: int) -> str:
        return ErrorCodeRules._STATUS_CODES.get(status_code, CommonErrorCodes.INTERNAL_ERROR)

    @staticmethod
    @rule
    def is_code(detail: str) -> bool:
        return bool(ErrorCodeRules._CODE_SHAPE.match(detail))


class ApiError:
    """Build standard error responses with adapter-owned headers."""

    @staticmethod
    @helper
    def response(
        status_code: int,
        message: str,
        *,
        code: str | None = None,
        headers: Mapping[str, str] | None = None,
        context: dict[str, Any] | None = None,
    ) -> JSONResponse:
        error: str | dict[str, Any] = (
            message if code is None else {"code": code, "message": message}
        )
        if context is not None and isinstance(error, dict):
            error["context"] = context
        return JSONResponse({"error": error}, status_code=status_code, headers=headers)

    @staticmethod
    @helper
    def from_detail(status_code: int, detail: str) -> JSONResponse:
        code = detail if ErrorCodeRules.is_code(detail) else ErrorCodeRules.for_status(status_code)
        return ApiError.response(status_code, detail, code=code)

    @staticmethod
    @helper
    def from_exception(exc: Exception) -> JSONResponse:
        if isinstance(exc, BusinessException):
            return ApiError.response(
                exc.status_code, exc.message, code=exc.error_code, context=exc.context
            )
        return ApiError.response(
            500, CommonErrorMessages.INTERNAL_ERROR, code=CommonErrorCodes.INTERNAL_ERROR
        )

    @staticmethod
    @helper
    def from_validation(errors: list[dict[str, Any]]) -> JSONResponse:
        fields = [
            {
                CommonContextKeys.FIELD: ApiError._field_path(error.get("loc", ())),
                CommonContextKeys.MESSAGE: error.get("msg", ""),
                CommonContextKeys.TYPE: error.get("type", ""),
            }
            for error in errors
        ]
        return ApiError.response(
            422,
            CommonErrorMessages.VALIDATION_FAILED,
            code=CommonErrorCodes.VALIDATION_FAILED,
            context={CommonContextKeys.FIELDS: fields},
        )

    @staticmethod
    @helper
    def _field_path(loc: tuple[Any, ...]) -> str:
        parts: list[str] = []
        for segment in loc:
            if segment in ("body", "query", "path"):
                continue
            if isinstance(segment, int):
                parts.append(f"[{segment}]")
            else:
                camel = to_camel(str(segment))
                parts.append(f".{camel}" if parts else camel)
        return "".join(parts)
