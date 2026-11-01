"""Request-scoped HTTP security helpers."""

from urllib.parse import urlsplit

from starlette.requests import Request
from starlette.responses import PlainTextResponse, Response

from app.shared.base.markers import helper
from app.shared.security.constants import SecurityConstants


class RequestSecurity:
    """Bound request reads and apply browser origin policy without shared mutable state."""

    @staticmethod
    @helper
    async def read_limited_body(request: Request, limit: int) -> bytes | None:
        """Read once, rejecting declared or streamed oversize before buffering the rest."""
        try:
            declared = int(request.headers.get("content-length", ""))
        except ValueError:
            declared = 0
        if declared > limit:
            return None
        chunks: list[bytes] = []
        size = 0
        async for chunk in request.stream():
            size += len(chunk)
            if size > limit:
                return None
            chunks.append(chunk)
        return b"".join(chunks)

    @staticmethod
    @helper
    def attempt_key(request: Request) -> str:
        """Use the ASGI client address; forwarded headers are the server's trust decision."""
        return request.client.host if request.client is not None else "unknown"

    @staticmethod
    @helper
    def is_loopback(request: Request) -> bool:
        """Whether the request addresses one of the allowed loopback hosts."""
        return request.headers.get("host", "").rsplit(":", 1)[0] in SecurityConstants.LOOPBACK

    @staticmethod
    @helper
    def check_origin(request: Request, expected: str) -> Response | None:
        """403 when the browser says the form came from another site; requests without Origin pass."""
        origin = request.headers.get("origin")
        if origin is None or origin == expected:
            return None
        if RequestSecurity.is_loopback(request) and urlsplit(origin).netloc == request.headers.get(
            "host"
        ):
            return None
        return PlainTextResponse(
            "Forbidden", status_code=403, headers=SecurityConstants.SECURITY_HEADERS
        )
