"""Async HTTP client to EduERP Spring Boot backend on behalf of an authenticated account."""

from __future__ import annotations

import time
import uuid
from typing import Any
from uuid import UUID

import httpx
import jwt

from app.infrastructure.http import read_bounded_json
from app.integrations.eduerp.constants import EduErpHttpConstants
from app.shared.base.markers import helper, integration


class EduErpBackendClient:
    """Execute authenticated HTTP requests against EduERP Spring Boot backend.

    Mints a short-lived HS256 `access_token` cookie + `XSRF-TOKEN` cookie/header for the
    caller's verified `account_id`, ensuring Spring Boot also runs its own
    CookieAuthenticationFilter, @RequirePermission, and branch/personal data scoping.
    Never retries write operations.
    """

    def __init__(
        self,
        base_url: str,
        jwt_secret: str,
        timeout_seconds: float = EduErpHttpConstants.DEFAULT_TIMEOUT_SECONDS,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._jwt_secret = jwt_secret
        self._timeout = timeout_seconds
        self._transport = transport

    @helper
    def mint_internal_access_jwt(self, account_id: UUID) -> str:
        now = int(time.time())
        payload = {
            "sub": str(account_id),
            "jti": str(uuid.uuid4()),
            EduErpHttpConstants.JWT_TYPE_CLAIM: EduErpHttpConstants.JWT_ACCESS_TYPE,
            "iat": now,
            "exp": now + EduErpHttpConstants.INTERNAL_TOKEN_TTL_SECONDS,
        }
        return jwt.encode(
            payload,
            self._jwt_secret.encode("utf-8"),
            algorithm=EduErpHttpConstants.JWT_ALGORITHM,
        )

    @integration
    async def request_json(
        self,
        *,
        account_id: UUID,
        method: str,
        path: str,
        params: dict[str, Any] | None = None,
        json_body: dict[str, Any] | None = None,
    ) -> tuple[int, Any]:
        """Perform a single bounded HTTP request against EduERP and return (status_code, body)."""
        normalized_path = path if path.startswith("/") else f"/{path}"
        token = self.mint_internal_access_jwt(account_id)
        csrf_token = uuid.uuid4().hex

        headers = {
            "Accept": "application/json",
            EduErpHttpConstants.CSRF_HEADER: csrf_token,
            "Cookie": (
                f"{EduErpHttpConstants.ACCESS_TOKEN_COOKIE}={token}; "
                f"{EduErpHttpConstants.CSRF_COOKIE}={csrf_token}"
            ),
        }

        clean_params = (
            {k: v for k, v in params.items() if v is not None and v != ""}
            if params
            else None
        )

        async with httpx.AsyncClient(
            base_url=self._base_url,
            timeout=self._timeout,
            transport=self._transport,
            follow_redirects=False,
        ) as client:
            async with client.stream(
                method=method.upper(),
                url=normalized_path,
                params=clean_params,
                json=json_body,
                headers=headers,
            ) as response:
                status = response.status_code
                try:
                    data = await read_bounded_json(response)
                except Exception:
                    data = {"status": status, "detail": "Non-JSON or oversized response from ERP"}
                return status, data
