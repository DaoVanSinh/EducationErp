"""Shared browser security policies and trusted loopback host names."""


class SecurityConstants:
    """Stable headers and host policy; no request-scoped state."""

    SECURITY_HEADERS = {
        "Content-Security-Policy": (
            "default-src 'none'; style-src 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'"
        ),
        "X-Frame-Options": "DENY",
        "Referrer-Policy": "same-origin",
        "Cache-Control": "no-store",
    }
    ADMIN_HEADERS = {
        **SECURITY_HEADERS,
        "Content-Security-Policy": (
            "default-src 'none'; style-src 'unsafe-inline'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'"
        ),
    }

    LOOPBACK = frozenset({"localhost", "127.0.0.1", "[::1]"})
