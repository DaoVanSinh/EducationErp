"""FastMCP server assembly for the EduERP module."""

from __future__ import annotations

from typing import Any

from fastmcp import FastMCP
from fastmcp.server.auth.auth import TokenVerifier

from app.integrations.eduerp.public import EduErpBackendClient
from app.modules.eduerp.constants import EduErpModuleConstants
from app.modules.eduerp.service import EduErpMcpToolService
from app.runtime.contracts import AuditWriter, McpServerSpec, PermissionAuthorizer


def build_eduerp_mcp_spec(
    *,
    token_verifier: TokenVerifier,
    permissions: PermissionAuthorizer,
    erp_client: EduErpBackendClient,
    audit: AuditWriter,
) -> tuple[McpServerSpec, EduErpMcpToolService]:
    """Create the FastMCP server and tool service for `/mcp/eduerp`."""
    service = EduErpMcpToolService(
        permissions=permissions,
        erp_client=erp_client,
        audit=audit,
    )

    mcp = FastMCP(
        name=EduErpModuleConstants.SERVER_NAME,
        instructions=(
            "Cổng kết nối MCP chính thức của EduERP. Mọi lời gọi công cụ đều được xác minh "
            "phân quyền RBAC trực tiếp (Resource, Action, Scope: PERSONAL < BRANCH < ORGANIZATION) "
            "từ Redis/PostgreSQL chung của EduERP trước khi thực thi."
        ),
        auth=token_verifier,
    )

    @mcp.tool(
        name=EduErpModuleConstants.Tools.MY_CONTEXT,
        description=(
            "Xem thông tin tài khoản EduERP hiện tại, danh sách quyền hạn hiệu lực (Resource, Action, Scope) "
            "được đọc trực tiếp từ hệ thống phân quyền EduERP, và danh sách các endpoint được phép gọi."
        ),
    )
    async def eduerp_my_context() -> dict[str, Any]:
        return service.get_my_context()

    @mcp.tool(
        name=EduErpModuleConstants.Tools.SEARCH_ENDPOINTS,
        description=(
            "Tìm kiếm danh sách các nghiệp vụ/endpoint EduERP mà tài khoản của bạn ĐƯỢC PHÉP truy cập "
            "theo cấu hình phân quyền RBAC hiện tại."
        ),
    )
    async def eduerp_search_endpoints(
        query: str = "",
        domain: str = "",
        method: str = "",
    ) -> dict[str, Any]:
        return service.search_endpoints(query=query, domain=domain, method=method)

    @mcp.tool(
        name=EduErpModuleConstants.Tools.DESCRIBE_ENDPOINT,
        description=(
            "Xem chi tiết thông số kỹ thuật, tham số đường dẫn, tham số truy vấn, các trường dữ liệu "
            "và quyền RBAC yêu cầu của một endpoint EduERP."
        ),
    )
    async def eduerp_describe_endpoint(endpoint_id: str) -> dict[str, Any]:
        return service.describe_endpoint(endpoint_id)

    @mcp.tool(
        name=EduErpModuleConstants.Tools.READ,
        description=(
            "Thực thi truy vấn đọc dữ liệu (GET) trên EduERP sau khi xác minh trực tiếp quyền hạn "
            "(Resource, Action, Scope) của tài khoản từ Redis/Database chung."
        ),
    )
    async def eduerp_read(
        endpoint_id: str,
        path_params: dict[str, Any] | None = None,
        query_params: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        return await service.execute_read(
            endpoint_id,
            path_params=path_params,
            query_params=query_params,
        )

    @mcp.tool(
        name=EduErpModuleConstants.Tools.WRITE,
        description=(
            "Thực thi thao tác tạo mới hoặc cập nhật nghiệp vụ (POST/PATCH) trên EduERP sau khi xác minh "
            "trực tiếp quyền hạn RBAC của tài khoản và ghi nhật ký kiểm toán (Audit Log)."
        ),
    )
    async def eduerp_write(
        endpoint_id: str,
        body: dict[str, Any],
        path_params: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        return await service.execute_write(
            endpoint_id,
            path_params=path_params,
            body=body,
        )

    def _build_http_app() -> Any:
        return mcp.http_app(path="/", stateless_http=True)

    spec = McpServerSpec(
        code=EduErpModuleConstants.MODULE_CODE,
        name=EduErpModuleConstants.SERVER_NAME,
        route_prefix=EduErpModuleConstants.ROUTE_PREFIX,
        build_http_app=_build_http_app,
    )
    return spec, service
