"""Dependency injection container assembling infrastructure, integrations, platform, and modules."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import httpx

from app.core.config import Settings
from app.infrastructure.postgres import PostgresDatabase
from app.infrastructure.sealer import Sealer
from app.integrations.cache.public import SharedRedisCache
from app.integrations.eduerp.public import EduErpBackendClient
from app.modules.eduerp.public import EduErpMcpToolService, build_eduerp_mcp_spec
from app.platform.audit.public import AuditService
from app.platform.catalog.public import McpCatalogService
from app.platform.identity.public import IdentityService
from app.platform.oauth.public import (
    HubTokenVerifier,
    OAuthHttpEndpoints,
    OAuthStore,
)
from app.platform.permissions.public import PermissionService
from app.platform.personal.public import PersonalMcpHttpEndpoints
from app.platform.sessions.public import ErpSessionService
from app.runtime.contracts import McpServerSpec


@dataclass
class ServiceContainer:
    settings: Settings
    db: PostgresDatabase
    cache: SharedRedisCache
    sealer: Sealer
    audit: AuditService
    catalog: McpCatalogService
    identity: IdentityService
    permissions: PermissionService
    sessions: ErpSessionService
    oauth_store: OAuthStore
    token_verifier: HubTokenVerifier
    oauth_http: OAuthHttpEndpoints
    personal_http: PersonalMcpHttpEndpoints
    erp_client: EduErpBackendClient
    eduerp_mcp_spec: McpServerSpec
    eduerp_tool_service: EduErpMcpToolService
    eduerp_mcp_http_app: Any

    def close(self) -> None:
        self.cache.close()
        self.db.close()


def build_container(
    cfg: Settings,
    *,
    erp_transport: httpx.AsyncBaseTransport | None = None,
) -> ServiceContainer:
    db = PostgresDatabase(cfg.database_url)
    cache = SharedRedisCache(cfg.redis_url)
    sealer = Sealer(cfg.mcp_sealer_secret)

    audit = AuditService(db)
    catalog = McpCatalogService(db)
    identity = IdentityService(db)
    permissions = PermissionService(db, cache)
    sessions = ErpSessionService(
        jwt_secret=cfg.identity_jwt_secret,
        sealer=sealer,
        cache=cache,
        identity=identity,
    )
    oauth_store = OAuthStore(db, cache)
    token_verifier = HubTokenVerifier(
        sealer=sealer,
        store=oauth_store,
        identity=identity,
    )
    oauth_http = OAuthHttpEndpoints(
        public_url=cfg.public_url,
        allowed_redirect_hosts=tuple(cfg.allowed_redirect_hosts),
        allowed_redirect_prefixes=tuple(cfg.allowed_redirect_prefixes),
        sealer=sealer,
        store=oauth_store,
        sessions=sessions,
        audit=audit,
    )
    personal_http = PersonalMcpHttpEndpoints(
        public_url=cfg.public_url,
        sessions=sessions,
        oauth_store=oauth_store,
        catalog=catalog,
        audit=audit,
    )
    erp_client = EduErpBackendClient(
        base_url=cfg.eduerp_api_base_url,
        jwt_secret=cfg.identity_jwt_secret,
        transport=erp_transport,
    )
    eduerp_spec, eduerp_tool_service = build_eduerp_mcp_spec(
        token_verifier=token_verifier,
        permissions=permissions,
        erp_client=erp_client,
        audit=audit,
    )
    eduerp_http_app = eduerp_spec.build_http_app()

    return ServiceContainer(
        settings=cfg,
        db=db,
        cache=cache,
        sealer=sealer,
        audit=audit,
        catalog=catalog,
        identity=identity,
        permissions=permissions,
        sessions=sessions,
        oauth_store=oauth_store,
        token_verifier=token_verifier,
        oauth_http=oauth_http,
        personal_http=personal_http,
        erp_client=erp_client,
        eduerp_mcp_spec=eduerp_spec,
        eduerp_tool_service=eduerp_tool_service,
        eduerp_mcp_http_app=eduerp_http_app,
    )
