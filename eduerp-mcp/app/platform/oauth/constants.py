"""Constants for OAuth 2.1 + PKCE and Bearer Token authentication."""


class OAuthConstants:
    CLIENT_ID_PREFIX = "mcp_"
    CLIENT_SECRET_PREFIX = "sec_"

    SEAL_KIND_AUTH_CODE = "oauth_code"
    SEAL_KIND_ACCESS_TOKEN = "oauth_access"
    SEAL_KIND_REFRESH_TOKEN = "oauth_refresh"

    AUTH_CODE_TTL_SECONDS = 300
    ACCESS_TOKEN_TTL_SECONDS = 3600
    REFRESH_TOKEN_TTL_SECONDS = 86400 * 30

    DEFAULT_SCOPE = "mcp"
    CODE_CHALLENGE_METHOD_S256 = "S256"

    GRANT_AUTHORIZATION_CODE = "authorization_code"
    GRANT_REFRESH_TOKEN = "refresh_token"

    class AuthModes:
        BEARER_SECRET = "bearer_secret"
        OAUTH2 = "oauth2"

    class RevokeReasons:
        REFRESH_REPLAY = "refresh_replay"
        USER_REVOKED = "user_revoked"
        CONNECTION_REVOKED = "connection_revoked"
        OAUTH_REVOKE_ENDPOINT = "oauth_revoke_endpoint"

    class AuditActions:
        AUTHORIZE = "oauth.authorize"
        ROTATE_SECRET = "connector.rotate_secret"
        REVOKE_CONNECTION = "connector.revoke_connection"

    class OAuthErrors:
        INVALID_GRANT = "invalid_grant"
        INVALID_CLIENT = "invalid_client"
        UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type"
        ACCESS_DENIED = "access_denied"

    class Decisions:
        FIELD = "decision"
        APPROVE = "approve"
        DENY = "access_denied"

    class Templates:
        DIRECTORY = "templates"
        CONSENT = "consent.html"
        LOGO_FILE = "logo.png"
        LOGO_MEDIA_TYPE = "image/png"

    class ConsentCopy:
        TITLE = "Cấp quyền kết nối AI — DS Academy"
        HEADING = "Cho phép kết nối?"
        INTRO = "Ứng dụng AI dùng connector"
        REQUEST = "xin quyền dùng"
        ACCOUNT = "thay cho tài khoản"
        NOTE = (
            "Ứng dụng chỉ dùng được các tác vụ bạn đã được cấp; "
            "quyền RBAC được kiểm tra trực tiếp ở mỗi lần gọi."
        )
        APPROVE = "Cho phép kết nối"
        DENY = "Từ chối"
        ERROR_TITLE = "Không thể kết nối MCP — DS Academy"
        ERROR_HEADING = "Yêu cầu kết nối không hợp lệ"
        BRAND_NAME = "DS Academy"
        MCP_TITLE = "DS Academy MCP"
        BADGE_PROTOCOL = "OAuth 2.1 · PKCE"
        LABEL_MCP = "Dịch vụ MCP"
        LABEL_ACCOUNT = "Tài khoản thực thi"
        FOOTER_LEFT = "DS ACADEMY · MCP GATEWAY"
        FOOTER_RIGHT = "Kết nối một lần. Làm việc ở mọi nơi."

    IDE_APP_KEY = "ide-bearer-token"
    IDE_APP_NAME = "IDE / CLI (Bearer Token)"

    KNOWN_APP_NAMES: dict[str, str] = {
        "claude.ai": "Claude (Anthropic)",
        "anthropic.com": "Claude (Anthropic)",
        "chatgpt.com": "ChatGPT (OpenAI)",
        "chat.openai.com": "ChatGPT (OpenAI)",
        "openai.com": "ChatGPT (OpenAI)",
        "localhost": "Local MCP Client",
        "127.0.0.1": "Local MCP Client",
    }

    DEFAULT_REDIRECT_HOSTS = (
        "claude.ai",
        "anthropic.com",
        "chatgpt.com",
        "chat.openai.com",
        "openai.com",
        "localhost",
        "127.0.0.1",
    )

    DEFAULT_REDIRECT_PREFIXES = (
        "claude.ai/api/mcp/auth_callback",
        "chatgpt.com/connector_platform_oauth_redirect",
        "chat.openai.com/connector_platform_oauth_redirect",
    )

    class Routes:
        LOGO = "/oauth/logo.png"
        AUTHORIZE = "/oauth/authorize"
        TOKEN = "/oauth/token"
        REVOKE = "/oauth/revoke"
        WELL_KNOWN_AUTH_SERVER = "/.well-known/oauth-authorization-server"
        WELL_KNOWN_PROTECTED_RESOURCE = "/.well-known/oauth-protected-resource"
        LOGIN_REDIRECT_PATH = "/login"
