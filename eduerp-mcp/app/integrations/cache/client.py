"""Shared Redis client wrapper with graceful degradation when Redis is unavailable."""

from __future__ import annotations

import json
from typing import Any
from uuid import UUID

import redis

from app.integrations.cache.constants import CacheLimits, CacheNamespaces
from app.shared.base.markers import helper, integration
from app.shared.logging_config import get_logger

_log = get_logger("integrations.cache")


class CacheKeyBuilder:
    """Build Redis keys identical to com.eduerp.integrations.cache.CacheKeyBuilder."""

    @staticmethod
    @helper
    def key(namespace: str, identifier: str | UUID) -> str:
        return f"{namespace}:{identifier}"


class SharedRedisCache:
    """Synchronous/fast Redis client sharing EduERP's Redis instance."""

    def __init__(self, redis_url: str) -> None:
        self._client = redis.Redis.from_url(
            redis_url,
            decode_responses=True,
            socket_connect_timeout=2.0,
            socket_timeout=2.0,
        )

    @integration
    def ping(self) -> bool:
        try:
            return bool(self._client.ping())
        except Exception:
            return False

    @integration
    def is_erp_jti_blacklisted(self, jti: str) -> bool:
        """Check `blacklist:jti:<jti>` written by EduERP TokenBlacklistService."""
        if not jti:
            return False
        key = CacheKeyBuilder.key(CacheNamespaces.ERP_JWT_BLACKLIST, jti)
        try:
            return bool(self._client.exists(key))
        except Exception:
            _log.warning("Redis unavailable while checking JWT blacklist")
            return False

    @integration
    def get_erp_permissions(self, account_id: UUID) -> list[dict[str, str]] | None:
        """Read `perm:account:<accountId>` cached by EduERP PermissionCacheService."""
        key = CacheKeyBuilder.key(CacheNamespaces.ERP_PERMISSION, account_id)
        try:
            raw = self._client.get(key)
        except Exception:
            _log.warning("Redis unavailable while reading ERP permissions")
            return None
        if not raw:
            return None
        try:
            parsed = json.loads(str(raw))
            if isinstance(parsed, list):
                return parsed
        except Exception:
            _log.warning("Malformed permission cache entry in Redis, ignoring")
        return None

    @integration
    def set_erp_permissions(
        self,
        account_id: UUID,
        permissions: list[dict[str, str]],
        ttl_seconds: int = CacheLimits.ERP_PERMISSION_TTL_SECONDS,
    ) -> None:
        """Populate `perm:account:<accountId>` using the exact JSON format expected by EduERP."""
        key = CacheKeyBuilder.key(CacheNamespaces.ERP_PERMISSION, account_id)
        try:
            payload = json.dumps(permissions, separators=(",", ":"))
            self._client.setex(key, ttl_seconds, payload)
        except Exception:
            _log.warning("Redis unavailable while writing ERP permissions")

    @integration
    def mark_oauth_family_revoked(
        self,
        family_id: str,
        ttl_seconds: int = CacheLimits.OAUTH_REVOCATION_TTL_SECONDS,
    ) -> None:
        key = CacheKeyBuilder.key(CacheNamespaces.MCP_OAUTH_FAMILY_REVOKED, family_id)
        try:
            self._client.setex(key, ttl_seconds, "1")
        except Exception:
            _log.warning("Redis unavailable while marking OAuth family revoked")

    @integration
    def is_oauth_family_revoked(self, family_id: str) -> bool:
        key = CacheKeyBuilder.key(CacheNamespaces.MCP_OAUTH_FAMILY_REVOKED, family_id)
        try:
            return bool(self._client.exists(key))
        except Exception:
            return False

    @integration
    def close(self) -> None:
        try:
            self._client.close()
        except Exception:
            pass
