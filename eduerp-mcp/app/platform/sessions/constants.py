"""Constants for EduERP session & CSRF verification."""


class SessionConstants:
    ACCESS_TOKEN_COOKIE = "access_token"
    CSRF_COOKIE = "XSRF-TOKEN"
    CSRF_HEADER = "x-xsrf-token"
    JWT_ALGORITHM = "HS256"
    JWT_TYPE_CLAIM = "typ"
    JWT_ACCESS_TYPE = "access"
    CSRF_SEAL_KIND = "consent_csrf"
    CSRF_SEAL_TTL_SECONDS = 900

    class ErrorCodes:
        UNAUTHENTICATED = "identity.unauthenticated"
        CSRF_INVALID = "identity.csrf_invalid"
