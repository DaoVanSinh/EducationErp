"""Append-only audit log writer into mcp.audit."""

from __future__ import annotations

from sqlalchemy import insert

from app.infrastructure.postgres import PostgresDatabase
from app.infrastructure.schema import audit
from app.shared.base.markers import database


class AuditService:
    """Write audit records to mcp.audit table."""

    def __init__(self, db: PostgresDatabase) -> None:
        self._db = db

    @database
    def record(self, actor: str, action: str, target: str = "", detail: str = "") -> None:
        with self._db.transaction() as conn:
            conn.execute(
                insert(audit).values(
                    actor=actor[:160],
                    action=action[:96],
                    target=target[:160],
                    detail=detail[:2000],
                )
            )
