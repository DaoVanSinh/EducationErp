"""HMAC-SHA256 stateless token sealer for OAuth codes, access tokens, and refresh tokens."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
from typing import Any

from app.shared.clock import Clock


class Sealer:
    """Sign and verify compact URL-safe JSON payloads with an installation secret."""

    def __init__(self, secret: str) -> None:
        if not secret or len(secret) < 16:
            raise ValueError("Sealer secret must be at least 16 characters")
        self._key = secret.encode("utf-8")

    def seal(self, data: dict[str, Any], ttl: int) -> str:
        payload = {**data, "exp": int(Clock.epoch()) + int(ttl)}
        raw = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")
        body = base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")
        sig = hmac.new(self._key, body.encode("ascii"), hashlib.sha256).digest()
        sig_b64 = base64.urlsafe_b64encode(sig).rstrip(b"=").decode("ascii")
        return f"{body}.{sig_b64}"

    def unseal(self, token: str, expected_kind: str) -> dict[str, Any] | None:
        if not token or "." not in token:
            return None
        body, _, sig_b64 = token.partition(".")
        if not body or not sig_b64:
            return None
        expected_sig = hmac.new(self._key, body.encode("ascii"), hashlib.sha256).digest()
        expected_b64 = base64.urlsafe_b64encode(expected_sig).rstrip(b"=").decode("ascii")
        if not hmac.compare_digest(sig_b64, expected_b64):
            return None
        try:
            padded = body + "=" * (-len(body) % 4)
            raw = base64.urlsafe_b64decode(padded.encode("ascii"))
            data = json.loads(raw.decode("utf-8"))
        except Exception:
            return None
        if not isinstance(data, dict):
            return None
        if data.get("k") != expected_kind:
            return None
        exp = data.get("exp")
        if not isinstance(exp, int) or exp <= int(Clock.epoch()):
            return None
        return data
