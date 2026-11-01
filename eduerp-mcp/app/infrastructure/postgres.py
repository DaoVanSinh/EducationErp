"""PostgreSQL connection pool and schema initialization for eduerp-mcp."""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager

from sqlalchemy import Connection, Engine, create_engine, text

from app.infrastructure.schema import mcp_metadata


class PostgresDatabase:
    """Thin wrapper around SQLAlchemy Core Engine sharing EduERP's PostgreSQL instance."""

    def __init__(self, database_url: str) -> None:
        normalized = self._normalize_url(database_url)
        self._engine: Engine = create_engine(
            normalized,
            pool_pre_ping=True,
            pool_size=5,
            max_overflow=10,
            future=True,
        )

    @staticmethod
    def _normalize_url(url: str) -> str:
        if url.startswith("postgresql://"):
            return "postgresql+psycopg://" + url[len("postgresql://") :]
        if url.startswith("postgres://"):
            return "postgresql+psycopg://" + url[len("postgres://") :]
        return url

    @property
    def engine(self) -> Engine:
        return self._engine

    @contextmanager
    def connect(self) -> Iterator[Connection]:
        with self._engine.connect() as conn:
            yield conn

    @contextmanager
    def transaction(self) -> Iterator[Connection]:
        with self._engine.begin() as conn:
            yield conn

    def ping(self) -> bool:
        try:
            with self._engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            return True
        except Exception:
            return False

    def ensure_mcp_schema(self) -> None:
        """Idempotently ensure mcp schema, tables, and default eduerp instance row exist."""
        with self._engine.begin() as conn:
            conn.execute(text("CREATE SCHEMA IF NOT EXISTS mcp"))
            mcp_metadata.create_all(conn, checkfirst=True)
            conn.execute(
                text(
                    """
                    INSERT INTO mcp.mcp_instances (code, name, description, module_code, route_prefix, enabled)
                    VALUES (
                        'eduerp',
                        'EduERP Core MCP',
                        'Tra cứu và thao tác nghiệp vụ EduERP (Cơ sở, Khóa học, Lớp học, Giáo viên, Học viên, Ghi danh, Hóa đơn, Bảng lương) theo phân quyền RBAC trực tiếp.',
                        'eduerp',
                        'eduerp',
                        TRUE
                    )
                    ON CONFLICT (code) DO NOTHING
                    """
                )
            )

    def close(self) -> None:
        self._engine.dispose()
