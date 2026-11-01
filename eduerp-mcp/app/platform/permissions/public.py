"""Resolve and verify EduERP effective permissions from shared Redis and shared PostgreSQL."""

from __future__ import annotations

from uuid import UUID

from sqlalchemy import select, union

from app.infrastructure.postgres import PostgresDatabase
from app.infrastructure.schema import (
    erp_account_groups,
    erp_account_roles,
    erp_group_permission_groups,
    erp_permission_group_items,
    erp_permissions,
    erp_role_permission_groups,
)
from app.integrations.cache.public import SharedRedisCache
from app.platform.permissions.constants import PermissionConstants
from app.runtime.contracts import EffectivePermissionDto
from app.shared.base.markers import database, facade, rule
from app.shared.exceptions import Forbidden


class PermissionRules:
    """Pure rules matching com.eduerp.modules.access.internal.rules.EffectivePermissionCalculator."""

    @staticmethod
    @rule
    def scope_rank(scope: str) -> int:
        return PermissionConstants.Scopes.RANK.get(scope.upper(), -1)

    @staticmethod
    @rule
    def covers_scope(granted_scope: str, required_min_scope: str) -> bool:
        granted_rank = PermissionRules.scope_rank(granted_scope)
        required_rank = PermissionRules.scope_rank(required_min_scope)
        return granted_rank >= 0 and required_rank >= 0 and granted_rank >= required_rank

    @staticmethod
    @rule
    def merge_broadest(rows: list[tuple[str, str, str]]) -> list[EffectivePermissionDto]:
        by_key: dict[tuple[str, str], str] = {}
        for resource, action, scope in rows:
            res_u = resource.upper()
            act_u = action.upper()
            scp_u = scope.upper()
            if PermissionRules.scope_rank(scp_u) < 0:
                continue
            key = (res_u, act_u)
            existing = by_key.get(key)
            if existing is None or PermissionRules.scope_rank(scp_u) > PermissionRules.scope_rank(existing):
                by_key[key] = scp_u
        return [
            EffectivePermissionDto(resource=res, action=act, scope=scp)
            for (res, act), scp in sorted(by_key.items())
        ]


class PermissionService:
    """Read live effective permissions from shared Redis `perm:account:<id>` with DB fallback."""

    def __init__(self, db: PostgresDatabase, cache: SharedRedisCache) -> None:
        self._db = db
        self._cache = cache

    @database
    def _load_from_postgres(self, account_id: UUID) -> list[EffectivePermissionDto]:
        role_pg_ids = (
            select(erp_role_permission_groups.c.permission_group_id)
            .select_from(
                erp_account_roles.join(
                    erp_role_permission_groups,
                    erp_account_roles.c.role_id == erp_role_permission_groups.c.role_id,
                )
            )
            .where(erp_account_roles.c.account_id == account_id)
        )
        group_pg_ids = (
            select(erp_group_permission_groups.c.permission_group_id)
            .select_from(
                erp_account_groups.join(
                    erp_group_permission_groups,
                    erp_account_groups.c.group_id == erp_group_permission_groups.c.group_id,
                )
            )
            .where(erp_account_groups.c.account_id == account_id)
        )
        all_pg_ids = union(role_pg_ids, group_pg_ids).subquery()

        stmt = (
            select(
                erp_permissions.c.resource,
                erp_permissions.c.action,
                erp_permission_group_items.c.scope,
            )
            .select_from(
                erp_permission_group_items.join(
                    erp_permissions,
                    erp_permission_group_items.c.permission_id == erp_permissions.c.id,
                ).join(
                    all_pg_ids,
                    erp_permission_group_items.c.permission_group_id
                    == all_pg_ids.c.permission_group_id,
                )
            )
        )

        with self._db.connect() as conn:
            raw_rows = conn.execute(stmt).all()

        triples = [(str(r[0]), str(r[1]), str(r[2])) for r in raw_rows]
        return PermissionRules.merge_broadest(triples)

    @facade
    def get_effective_permissions(self, account_id: UUID) -> list[EffectivePermissionDto]:
        cached = self._cache.get_erp_permissions(account_id)
        if cached is not None:
            triples = [
                (
                    str(item.get("resource", "")),
                    str(item.get("action", "")),
                    str(item.get("scope", "")),
                )
                for item in cached
                if isinstance(item, dict)
            ]
            return PermissionRules.merge_broadest(triples)

        computed = self._load_from_postgres(account_id)
        self._cache.set_erp_permissions(
            account_id,
            [
                {"resource": p.resource, "action": p.action, "scope": p.scope}
                for p in computed
            ],
        )
        return computed

    @facade
    def has_permission(
        self,
        account_id: UUID,
        resource: str,
        action: str,
        min_scope: str = PermissionConstants.Scopes.PERSONAL,
    ) -> bool:
        target_res = resource.upper()
        target_act = action.upper()
        for perm in self.get_effective_permissions(account_id):
            if perm.resource == target_res and perm.action == target_act:
                return PermissionRules.covers_scope(perm.scope, min_scope)
        return False

    @facade
    def require_permission(
        self,
        account_id: UUID,
        resource: str,
        action: str,
        min_scope: str = PermissionConstants.Scopes.PERSONAL,
    ) -> EffectivePermissionDto:
        target_res = resource.upper()
        target_act = action.upper()
        for perm in self.get_effective_permissions(account_id):
            if perm.resource == target_res and perm.action == target_act:
                if PermissionRules.covers_scope(perm.scope, min_scope):
                    return perm
                raise Forbidden(
                    message=(
                        f"Phạm vi quyền không đủ cho {target_res}:{target_act} "
                        f"(hiện có: {perm.scope}, yêu cầu tối thiểu: {min_scope.upper()})."
                    ),
                    error_code=PermissionConstants.ErrorCodes.PERMISSION_DENIED,
                )
        raise Forbidden(
            message=f"Tài khoản không có quyền {target_res}:{target_act}.",
            error_code=PermissionConstants.ErrorCodes.PERMISSION_DENIED,
        )
