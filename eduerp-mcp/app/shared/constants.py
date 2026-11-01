"""Common constants for the application."""

from __future__ import annotations

import re
from typing import Final, Literal, TypeAlias


class ActorKind:
    """Server-created actor kinds shared by transport and command adapters."""

    Type: TypeAlias = Literal["erp", "system"]
    ERP: Final[Type] = "erp"
    SYSTEM: Final[Type] = "system"


class Environment:
    DEV = "dev"
    STG = "stg"
    PROD = "prod"


class CommonErrorCodes:
    BAD_REQUEST = "http.bad_request"
    UNAUTHORIZED = "http.unauthorized"
    FORBIDDEN = "http.forbidden"
    NOT_FOUND = "http.not_found"
    CONFLICT = "http.conflict"
    PAYLOAD_TOO_LARGE = "http.payload_too_large"
    UNSUPPORTED_MEDIA_TYPE = "http.unsupported_media_type"
    VALIDATION_FAILED = "http.validation_failed"
    TOO_MANY_REQUESTS = "http.too_many_requests"
    GONE = "http.gone"
    INTERNAL_ERROR = "http.internal_error"


class CommonErrorMessages:
    VALIDATION_FAILED = "Request validation failed"
    INTERNAL_ERROR = "Internal server error"
    NOT_FOUND = "Resource not found"
    UNAUTHORIZED = "Unauthorized access"
    FORBIDDEN = "Access forbidden"


class CommonContextKeys:
    FIELDS = "fields"
    FIELD = "field"
    MESSAGE = "message"
    TYPE = "type"


class ApiLimits:
    """Limits and safe fallback messages for the common HTTP contract."""

    BODY_BYTES = 256 * 1024
    BODY_LIMIT_STATE = "api_body_limit"
    INVALID_REQUEST = "Request validation failed"
    INTERNAL_ERROR = "Service temporarily unavailable"


class LogEvents:
    """Stable event names of the request and error log lines."""

    API_REQUEST = "API request"
    API_REQUEST_FAILED = "API request failed"
    BUSINESS_ERROR = "Business error"
    INVALID_REQUEST = "Invalid request"
    HTTP_ERROR = "HTTP error"
    STORE_UNAVAILABLE = "Store unavailable"
    UNEXPECTED_ERROR = "Unexpected error"


class AccessLogPolicy:
    """What the access log records, skips and accepts from a caller."""

    LOGGER = "api_access"
    SKIP_PATHS = frozenset({"/livez", "/healthz"})
    REQUEST_ID_HEADER = b"x-request-id"
    REQUEST_ID_PATTERN = re.compile(r"[A-Za-z0-9._:-]{1,128}")
    UNKNOWN_CLIENT = "unknown"
    SERVER_ERROR = 500
    CLIENT_CLOSED = 499
    CLIENT_ERROR = 400
    DURATION_DIGITS = 2
    MS_PER_SECOND = 1000
