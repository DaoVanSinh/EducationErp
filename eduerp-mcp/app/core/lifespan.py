"""Application lifespan management combining DB schema check and FastMCP session lifespan."""

from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import AsyncExitStack, asynccontextmanager

from starlette.applications import Starlette

from app.core.container import ServiceContainer


def build_lifespan(container: ServiceContainer):
    @asynccontextmanager
    async def lifespan(_app: Starlette) -> AsyncIterator[None]:
        if container.settings.auto_ensure_schema:
            container.db.ensure_mcp_schema()
        async with AsyncExitStack() as stack:
            mcp_lifespan = getattr(container.eduerp_mcp_http_app, "lifespan", None)
            if callable(mcp_lifespan):
                await stack.enter_async_context(mcp_lifespan(container.eduerp_mcp_http_app))
            try:
                yield
            finally:
                container.close()

    return lifespan
