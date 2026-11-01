"""Starlette JSON handlers for /api/mcp/my/* consumed by the EduERP React frontend."""

from __future__ import annotations

from typing import Any

from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Route

from app.platform.audit.public import AuditService
from app.platform.catalog.public import McpCatalogService
from app.platform.oauth.public import ConnectionRecord, ConnectorRecord, OAuthConstants, OAuthStore
from app.platform.personal.constants import PersonalConstants
from app.platform.sessions.public import ErpSessionService
from app.shared.base.markers import helper


class PersonalMcpHttpEndpoints:
    """Self-service endpoints allowing any authenticated EduERP user to manage their MCP connector."""

    def __init__(
        self,
        *,
        public_url: str,
        sessions: ErpSessionService,
        oauth_store: OAuthStore,
        catalog: McpCatalogService,
        audit: AuditService,
    ) -> None:
        self._public_url = public_url.rstrip("/")
        self._sessions = sessions
        self._store = oauth_store
        self._catalog = catalog
        self._audit = audit

    @helper
    def routes(self) -> list[Route]:
        return [
            Route(PersonalConstants.Routes.CONNECTOR, self.get_connector, methods=["GET"]),
            Route(PersonalConstants.Routes.ROTATE_SECRET, self.rotate_secret, methods=["POST"]),
            Route(PersonalConstants.Routes.MCPS, self.list_mcps, methods=["GET"]),
            Route(PersonalConstants.Routes.CONNECTIONS, self.list_connections, methods=["GET"]),
            Route(
                PersonalConstants.Routes.REVOKE_CONNECTION,
                self.revoke_connection,
                methods=["POST"],
            ),
        ]

    @helper
    def _serialize_connector(self, rec: ConnectorRecord) -> dict[str, Any]:
        return {
            "clientId": rec.client_id,
            "hasSecret": rec.has_secret,
            "secretLast4": rec.secret_last4,
            "enabled": rec.enabled,
            "createdAt": rec.created_at.isoformat(),
            "updatedAt": rec.updated_at.isoformat(),
            "lastUsedAt": rec.last_used_at.isoformat() if rec.last_used_at else None,
            "secretRotatedAt": (
                rec.secret_rotated_at.isoformat() if rec.secret_rotated_at else None
            ),
            "oauthAuthorizeUrl": f"{self._public_url}{OAuthConstants.Routes.AUTHORIZE}",
            "oauthTokenUrl": f"{self._public_url}{OAuthConstants.Routes.TOKEN}",
            "mcpBaseUrl": f"{self._public_url}/mcp/eduerp",
        }

    @staticmethod
    @helper
    def _serialize_connection(rec: ConnectionRecord) -> dict[str, Any]:
        return {
            "id": rec.id,
            "clientId": rec.client_id,
            "appKey": rec.app_key,
            "appName": rec.app_name,
            "appOrigin": rec.app_origin,
            "redirectUri": rec.redirect_uri,
            "scope": rec.scope,
            "createdAt": rec.created_at.isoformat(),
            "updatedAt": rec.updated_at.isoformat(),
            "lastUsedAt": rec.last_used_at.isoformat() if rec.last_used_at else None,
            "revokedAt": rec.revoked_at.isoformat() if rec.revoked_at else None,
        }

    async def get_connector(self, request: Request) -> JSONResponse:
        actor = self._sessions.require_actor(request)
        assert actor.principal_id is not None
        connector = self._store.ensure_connector(actor.principal_id)
        return JSONResponse(self._serialize_connector(connector))

    async def rotate_secret(self, request: Request) -> JSONResponse:
        actor = self._sessions.require_actor(request)
        self._sessions.verify_double_submit_csrf(request)
        assert actor.principal_id is not None
        connector, raw_secret = self._store.rotate_secret(actor.principal_id)
        self._audit.record(
            actor=actor.name,
            action=OAuthConstants.AuditActions.ROTATE_SECRET,
            target=connector.client_id,
            detail="Rotated personal MCP client secret",
        )
        return JSONResponse(
            {
                "connector": self._serialize_connector(connector),
                "clientSecret": raw_secret,
                "bearerToken": f"{connector.client_id}.{raw_secret}",
            }
        )

    async def list_mcps(self, request: Request) -> JSONResponse:
        self._sessions.require_actor(request)
        items = self._catalog.list_enabled()
        return JSONResponse(
            {
                "items": [
                    {
                        "id": item.id,
                        "code": item.code,
                        "name": item.name,
                        "description": item.description,
                        "routePrefix": item.route_prefix,
                        "mcpUrl": f"{self._public_url}/mcp/{item.route_prefix}",
                        "enabled": item.enabled,
                    }
                    for item in items
                ]
            }
        )

    async def list_connections(self, request: Request) -> JSONResponse:
        actor = self._sessions.require_actor(request)
        assert actor.principal_id is not None
        rows = self._store.list_connections(actor.principal_id)
        return JSONResponse({"items": [self._serialize_connection(r) for r in rows]})

    async def revoke_connection(self, request: Request) -> JSONResponse:
        actor = self._sessions.require_actor(request)
        self._sessions.verify_double_submit_csrf(request)
        assert actor.principal_id is not None
        connection_id = int(request.path_params["connection_id"])
        updated = self._store.revoke_connection(actor.principal_id, connection_id)
        self._audit.record(
            actor=actor.name,
            action=OAuthConstants.AuditActions.REVOKE_CONNECTION,
            target=updated.app_key,
            detail=f"Revoked connection #{connection_id} ({updated.app_name})",
        )
        return JSONResponse(self._serialize_connection(updated))
