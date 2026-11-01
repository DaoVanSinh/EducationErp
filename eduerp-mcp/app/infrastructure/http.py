"""Shared async HTTP reading helpers with strict byte limits."""

from __future__ import annotations

import json
from typing import Any

import httpx

from app.shared.json_bounds import validate_json

MAX_UPSTREAM_BYTES = 2 * 1024 * 1024  # 2 MiB


async def read_bounded_json(
    response: httpx.Response, max_bytes: int = MAX_UPSTREAM_BYTES
) -> Any:
    """Read an upstream httpx response stream with a hard byte ceiling and JSON depth validation."""
    chunks: list[bytes] = []
    seen = 0
    async for chunk in response.aiter_bytes():
        seen += len(chunk)
        if seen > max_bytes:
            raise ValueError("Upstream response exceeded size limit")
        chunks.append(chunk)
    raw = b"".join(chunks)
    if not raw:
        return {}
    parsed = json.loads(raw.decode("utf-8"))
    return validate_json(parsed)
