"""Constants for the EduERP HTTP backend client."""


class EduErpHttpConstants:
    ACCESS_TOKEN_COOKIE = "access_token"
    CSRF_COOKIE = "XSRF-TOKEN"
    CSRF_HEADER = "X-XSRF-TOKEN"
    JWT_ALGORITHM = "HS256"
    JWT_TYPE_CLAIM = "typ"
    JWT_ACCESS_TYPE = "access"
    INTERNAL_TOKEN_TTL_SECONDS = 60
    DEFAULT_TIMEOUT_SECONDS = 15.0
