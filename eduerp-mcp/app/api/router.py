"""Starlette application factory mounting OAuth 2.1, personal API, health probes, and FastMCP."""

from __future__ import annotations

import httpx
from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Mount, Route

from app.core.config import Settings
from app.core.container import ServiceContainer, build_container
from app.core.lifespan import build_lifespan
from app.infrastructure.api import install_api_infrastructure


def create_app(
    cfg: Settings | None = None,
    *,
    container: ServiceContainer | None = None,
    erp_transport: httpx.AsyncBaseTransport | None = None,
) -> Starlette:
    settings = cfg or Settings()
    svc = container or build_container(settings, erp_transport=erp_transport)

    async def livez(_request: Request) -> JSONResponse:
        return JSONResponse({"status": "ok", "service": "eduerp-mcp"})

    async def healthz(_request: Request) -> JSONResponse:
        db_ok = svc.db.ping()
        redis_ok = svc.cache.ping()
        status = 200 if db_ok else 503
        return JSONResponse(
            {
                "status": "ok" if db_ok else "degraded",
                "database": "up" if db_ok else "down",
                "redis": "up" if redis_ok else "degraded",
            },
            status_code=status,
        )

    routes = [
        Route("/livez", livez, methods=["GET"]),
        Route("/healthz", healthz, methods=["GET"]),
        *svc.oauth_http.routes(),
        *svc.personal_http.routes(),
        Mount(f"/mcp/{svc.eduerp_mcp_spec.route_prefix}", app=svc.eduerp_mcp_http_app),
    ]

    app = Starlette(
        debug=False,
        routes=routes,
        lifespan=build_lifespan(svc),
    )
    app.state.container = svc
    install_api_infrastructure(app, settings.allowed_origins)
    return app
