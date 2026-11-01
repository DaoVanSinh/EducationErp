"""Permission-aware execution service for EduERP MCP tools."""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any
from urllib.parse import quote
from uuid import UUID

from fastmcp.server.dependencies import get_access_token

from app.integrations.eduerp.public import EduErpBackendClient
from app.modules.eduerp.constants import EduErpModuleConstants
from app.modules.eduerp.registry import EDUERP_ENDPOINTS, ENDPOINTS_BY_ID, EndpointSpec
from app.runtime.contracts import AuditWriter, PermissionAuthorizer
from app.shared.base.markers import helper, rule, use_case
from app.shared.exceptions import AuthenticationError, BadRequestError, Forbidden, NotFound

_SAFE_PATH_PARAM = re.compile(r"^[A-Za-z0-9._-]{1,128}$")


@dataclass(frozen=True)
class CallerContext:
    account_id: UUID
    email: str
    full_name: str
    client_id: str
    auth_mode: str


class EduErpMcpToolService:
    """Enforce live EduERP RBAC permissions from shared Redis/PostgreSQL on every MCP tool call."""

    def __init__(
        self,
        *,
        permissions: PermissionAuthorizer,
        erp_client: EduErpBackendClient,
        audit: AuditWriter,
    ) -> None:
        self._permissions = permissions
        self._erp_client = erp_client
        self._audit = audit

    @staticmethod
    @helper
    def resolve_caller(override: CallerContext | None = None) -> CallerContext:
        if override is not None:
            return override
        token = get_access_token()
        if token is None or not token.claims:
            raise AuthenticationError(
                message="Không tìm thấy thông tin xác thực MCP hợp lệ."
            )
        raw_account_id = token.claims.get("account_id")
        if not raw_account_id:
            raise AuthenticationError(
                message="Token MCP không gắn với tài khoản EduERP hợp lệ."
            )
        return CallerContext(
            account_id=UUID(str(raw_account_id)),
            email=str(token.claims.get("email") or ""),
            full_name=str(token.claims.get("full_name") or ""),
            client_id=str(token.claims.get("client_id") or token.client_id or ""),
            auth_mode=str(token.claims.get("auth_mode") or "oauth2"),
        )

    @staticmethod
    @rule
    def interpolate_path(spec: EndpointSpec, path_params: dict[str, Any] | None) -> str:
        params = path_params or {}
        path = spec.path_template
        for key in spec.path_params:
            raw_val = str(params.get(key, "")).strip()
            if not raw_val or not _SAFE_PATH_PARAM.fullmatch(raw_val):
                raise BadRequestError(
                    message=f"Tham số đường dẫn '{key}' bị thiếu hoặc không hợp lệ."
                )
            path = path.replace(f"{{{key}}}", quote(raw_val, safe=""))
        return path

    @staticmethod
    @helper
    def serialize_spec(spec: EndpointSpec, *, allowed: bool) -> dict[str, Any]:
        return {
            "endpointId": spec.endpoint_id,
            "domain": spec.domain,
            "method": spec.method,
            "pathTemplate": spec.path_template,
            "requiredPermission": {
                "resource": spec.resource,
                "action": spec.action,
                "minScope": spec.min_scope,
            },
            "allowedForCaller": allowed,
            "summary": spec.summary,
            "pathParams": list(spec.path_params),
            "queryParams": list(spec.query_params),
            "bodyFields": list(spec.body_fields),
        }

    @use_case
    def get_my_context(self, caller: CallerContext | None = None) -> dict[str, Any]:
        ctx = self.resolve_caller(caller)
        effective = self._permissions.get_effective_permissions(ctx.account_id)
        accessible = [
            spec.endpoint_id
            for spec in EDUERP_ENDPOINTS
            if self._permissions.has_permission(
                ctx.account_id, spec.resource, spec.action, spec.min_scope
            )
        ]
        return {
            "account": {
                "accountId": str(ctx.account_id),
                "email": ctx.email,
                "fullName": ctx.full_name,
                "clientId": ctx.client_id,
                "authMode": ctx.auth_mode,
            },
            "effectivePermissions": [
                {"resource": p.resource, "action": p.action, "scope": p.scope}
                for p in effective
            ],
            "accessibleEndpointCount": len(accessible),
            "accessibleEndpoints": accessible,
        }

    @use_case
    def search_endpoints(
        self,
        *,
        query: str = "",
        domain: str = "",
        method: str = "",
        caller: CallerContext | None = None,
    ) -> dict[str, Any]:
        ctx = self.resolve_caller(caller)
        q_lower = query.strip().lower()
        dom_lower = domain.strip().lower()
        meth_upper = method.strip().upper()

        matched: list[dict[str, Any]] = []
        for spec in EDUERP_ENDPOINTS:
            if dom_lower and spec.domain.lower() != dom_lower:
                continue
            if meth_upper and spec.method != meth_upper:
                continue
            if q_lower:
                haystack = f"{spec.endpoint_id} {spec.domain} {spec.summary} {spec.resource}".lower()
                if q_lower not in haystack:
                    continue
            allowed = self._permissions.has_permission(
                ctx.account_id, spec.resource, spec.action, spec.min_scope
            )
            if allowed:
                matched.append(self.serialize_spec(spec, allowed=True))

        return {
            "total": len(matched),
            "items": matched,
        }

    @use_case
    def describe_endpoint(
        self,
        endpoint_id: str,
        *,
        caller: CallerContext | None = None,
    ) -> dict[str, Any]:
        ctx = self.resolve_caller(caller)
        spec = ENDPOINTS_BY_ID.get(endpoint_id.strip())
        if spec is None:
            raise NotFound(message=f"Không tìm thấy endpoint '{endpoint_id}'.")
        allowed = self._permissions.has_permission(
            ctx.account_id, spec.resource, spec.action, spec.min_scope
        )
        return self.serialize_spec(spec, allowed=allowed)

    @use_case
    async def execute_read(
        self,
        endpoint_id: str,
        *,
        path_params: dict[str, Any] | None = None,
        query_params: dict[str, Any] | None = None,
        caller: CallerContext | None = None,
    ) -> dict[str, Any]:
        ctx = self.resolve_caller(caller)
        spec = ENDPOINTS_BY_ID.get(endpoint_id.strip())
        if spec is None:
            raise NotFound(message=f"Không tìm thấy endpoint '{endpoint_id}'.")
        if spec.method != "GET":
            raise BadRequestError(
                message=f"Endpoint '{endpoint_id}' là thao tác ghi ({spec.method}); hãy dùng công cụ eduerp_write."
            )

        granted = self._permissions.require_permission(
            ctx.account_id, spec.resource, spec.action, spec.min_scope
        )
        path = self.interpolate_path(spec, path_params)
        allowed_query = {
            k: v
            for k, v in (query_params or {}).items()
            if not spec.query_params or k in spec.query_params
        }
        status, data = await self._erp_client.request_json(
            account_id=ctx.account_id,
            method="GET",
            path=path,
            params=allowed_query,
        )
        return {
            "endpointId": spec.endpoint_id,
            "grantedPermission": {
                "resource": granted.resource,
                "action": granted.action,
                "scope": granted.scope,
            },
            "statusCode": status,
            "data": data,
        }

    @use_case
    async def execute_write(
        self,
        endpoint_id: str,
        *,
        path_params: dict[str, Any] | None = None,
        body: dict[str, Any] | None = None,
        caller: CallerContext | None = None,
    ) -> dict[str, Any]:
        ctx = self.resolve_caller(caller)
        spec = ENDPOINTS_BY_ID.get(endpoint_id.strip())
        if spec is None:
            raise NotFound(message=f"Không tìm thấy endpoint '{endpoint_id}'.")
        if spec.method not in ("POST", "PATCH"):
            raise BadRequestError(
                message=f"Endpoint '{endpoint_id}' là thao tác đọc (GET); hãy dùng công cụ eduerp_read."
            )

        granted = self._permissions.require_permission(
            ctx.account_id, spec.resource, spec.action, spec.min_scope
        )
        path = self.interpolate_path(spec, path_params)
        payload = body or {}
        if not isinstance(payload, dict):
            raise BadRequestError(message="Tham số 'body' phải là một JSON object.")

        status, data = await self._erp_client.request_json(
            account_id=ctx.account_id,
            method=spec.method,
            path=path,
            json_body=payload,
        )
        self._audit.record(
            actor=ctx.email or str(ctx.account_id),
            action=f"{EduErpModuleConstants.AuditPrefixes.WRITE}{spec.endpoint_id}",
            target=path,
            detail=f"status={status} scope={granted.scope}",
        )
        if status >= 400 and status == 403:
            raise Forbidden(message="Backend EduERP từ chối thao tác do giới hạn phạm vi dữ liệu.")
        return {
            "endpointId": spec.endpoint_id,
            "grantedPermission": {
                "resource": granted.resource,
                "action": granted.action,
                "scope": granted.scope,
            },
            "statusCode": status,
            "data": data,
        }
