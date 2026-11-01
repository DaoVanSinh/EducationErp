"""Constants for the shared Redis cache integration."""


class CacheNamespaces:
    """Exact Redis key prefixes matching EduERP Spring Boot CacheKeyBuilder."""

    ERP_PERMISSION = "perm:account"
    ERP_JWT_BLACKLIST = "blacklist:jti"
    MCP_OAUTH_FAMILY_REVOKED = "mcp:oauth:revoked:family"
    MCP_OAUTH_CLIENT_REVOKED = "mcp:oauth:revoked:client"


class CacheLimits:
    ERP_PERMISSION_TTL_SECONDS = 600
    OAUTH_REVOCATION_TTL_SECONDS = 86400 * 30
