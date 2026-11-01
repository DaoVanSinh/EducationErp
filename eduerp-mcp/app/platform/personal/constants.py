"""Route constants for self-service personal MCP connector endpoints."""


class PersonalConstants:
    class Routes:
        CONNECTOR = "/api/mcp/my/connector"
        ROTATE_SECRET = "/api/mcp/my/connector/secret"
        MCPS = "/api/mcp/my/mcps"
        CONNECTIONS = "/api/mcp/my/connections"
        REVOKE_CONNECTION = "/api/mcp/my/connections/{connection_id:int}/revoke"
