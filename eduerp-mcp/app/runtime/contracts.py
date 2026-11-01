"""Runtime contracts shared between platform and business modules without upward imports."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Any, Protocol
from uuid import UUID


@dataclass(frozen=True)
class EffectivePermissionDto:
    """Single effective permission resolved from shared Redis or PostgreSQL."""

    resource: str
    action: str
    scope: str  # "PERSONAL" | "BRANCH" | "ORGANIZATION"


@dataclass(frozen=True)
class AccountIdentityDto:
    """Authenticated EduERP account profile."""

    account_id: UUID
    email: str
    full_name: str
    status: str
    home_branch_id: UUID | None
    role_codes: tuple[str, ...] = ()


class PermissionAuthorizer(Protocol):
    """Protocol for checking live EduERP RBAC permissions for an account."""

    def get_effective_permissions(self, account_id: UUID) -> list[EffectivePermissionDto]: ...

    def require_permission(
        self,
        account_id: UUID,
        resource: str,
        action: str,
        min_scope: str = "PERSONAL",
    ) -> EffectivePermissionDto: ...

    def has_permission(
        self,
        account_id: UUID,
        resource: str,
        action: str,
        min_scope: str = "PERSONAL",
    ) -> bool: ...


class AuditWriter(Protocol):
    """Protocol for recording MCP tool mutations and OAuth events."""

    def record(self, actor: str, action: str, target: str = "", detail: str = "") -> None: ...


@dataclass(frozen=True)
class McpServerSpec:
    """Descriptor for mounting an MCP server instance."""

    code: str
    name: str
    route_prefix: str
    build_http_app: Callable[[], Any]
