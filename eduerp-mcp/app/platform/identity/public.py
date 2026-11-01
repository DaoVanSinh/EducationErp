"""Read active EduERP account identity and role codes from shared PostgreSQL."""

from __future__ import annotations

from uuid import UUID

from sqlalchemy import select

from app.infrastructure.postgres import PostgresDatabase
from app.infrastructure.schema import erp_account_roles, erp_accounts, erp_roles
from app.platform.identity.constants import IdentityConstants
from app.runtime.contracts import AccountIdentityDto
from app.shared.base.markers import database


class IdentityService:
    """Resolve EduERP account profiles from public.accounts."""

    def __init__(self, db: PostgresDatabase) -> None:
        self._db = db

    @database
    def get_active_account(self, account_id: UUID) -> AccountIdentityDto | None:
        stmt = select(
            erp_accounts.c.id,
            erp_accounts.c.email,
            erp_accounts.c.full_name,
            erp_accounts.c.status,
            erp_accounts.c.home_branch_id,
        ).where(erp_accounts.c.id == account_id)

        roles_stmt = (
            select(erp_roles.c.code)
            .select_from(
                erp_account_roles.join(
                    erp_roles, erp_account_roles.c.role_id == erp_roles.c.id
                )
            )
            .where(erp_account_roles.c.account_id == account_id)
        )

        with self._db.connect() as conn:
            row = conn.execute(stmt).mappings().first()
            if row is None or str(row["status"]) != IdentityConstants.STATUS_ACTIVE:
                return None
            role_rows = conn.execute(roles_stmt).scalars().all()

        return AccountIdentityDto(
            account_id=row["id"],
            email=str(row["email"]),
            full_name=str(row["full_name"]),
            status=str(row["status"]),
            home_branch_id=row["home_branch_id"],
            role_codes=tuple(str(r) for r in role_rows),
        )
