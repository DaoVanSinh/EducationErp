"""Read enabled MCP instances from mcp.mcp_instances."""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import select

from app.infrastructure.postgres import PostgresDatabase
from app.infrastructure.schema import mcp_instances
from app.shared.base.markers import database


@dataclass(frozen=True)
class McpCatalogItem:
    id: int
    code: str
    name: str
    description: str
    module_code: str
    route_prefix: str
    enabled: bool


class McpCatalogService:
    """List MCP instances registered in mcp.mcp_instances."""

    def __init__(self, db: PostgresDatabase) -> None:
        self._db = db

    @database
    def list_enabled(self) -> list[McpCatalogItem]:
        stmt = (
            select(
                mcp_instances.c.id,
                mcp_instances.c.code,
                mcp_instances.c.name,
                mcp_instances.c.description,
                mcp_instances.c.module_code,
                mcp_instances.c.route_prefix,
                mcp_instances.c.enabled,
            )
            .where(mcp_instances.c.enabled.is_(True))
            .order_by(mcp_instances.c.id.asc())
        )
        with self._db.connect() as conn:
            rows = conn.execute(stmt).mappings().all()
        return [
            McpCatalogItem(
                id=int(r["id"]),
                code=str(r["code"]),
                name=str(r["name"]),
                description=str(r["description"]),
                module_code=str(r["module_code"]),
                route_prefix=str(r["route_prefix"]),
                enabled=bool(r["enabled"]),
            )
            for r in rows
        ]
