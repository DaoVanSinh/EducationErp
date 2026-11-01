"""URL safety decisions shared by every configured origin and redirect."""

from urllib.parse import urlsplit

from app.shared.base.markers import rule


class UrlRules:
    """Accept only https URLs, or http on loopback for local development."""

    LOCAL_HOSTS = frozenset({"localhost", "127.0.0.1"})
    HTTPS = "https"
    HTTP = "http"
    CONTROL_LOW = 32

    @staticmethod
    @rule
    def secure(url: str) -> bool:
        """An absolute https URL (http only for loopback) without credentials or controls."""
        if "\\" in url or any(char.isspace() or ord(char) < UrlRules.CONTROL_LOW for char in url):
            return False
        try:
            parts = urlsplit(url)
            port = parts.port
        except ValueError:
            return False
        if parts.username is not None or (port is not None and port < 1):
            return False
        host = parts.hostname or ""
        return bool(host) and (
            parts.scheme == UrlRules.HTTPS
            or (parts.scheme == UrlRules.HTTP and host in UrlRules.LOCAL_HOSTS)
        )

    @staticmethod
    @rule
    def origin(url: str) -> bool:
        """A secure URL that names only an origin: no path, query or fragment."""
        if "?" in url or "#" in url or not UrlRules.secure(url):
            return False
        parts = urlsplit(url)
        return parts.path in ("", "/") and not parts.query and not parts.fragment

    @staticmethod
    @rule
    def host_path_prefix(value: str) -> tuple[str, str] | None:
        """Split a ``host/path/`` prefix (no scheme, port or credentials); None when malformed."""
        host, separator, path = value.strip().lower().partition("/")
        if not host or not separator or ":" in host or "@" in host:
            return None
        return host, "/" + path
