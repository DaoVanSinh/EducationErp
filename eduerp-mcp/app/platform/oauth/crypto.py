"""Cryptographic helpers for OAuth client secrets and PKCE S256 verification."""

from __future__ import annotations

import base64
import hashlib
import hmac
import secrets

from app.platform.oauth.constants import OAuthConstants
from app.shared.base.markers import helper, rule

PBKDF2_ITERATIONS = 210_000


class OAuthCrypto:
    """Client ID / secret generation, PBKDF2 hashing, and PKCE S256 validation."""

    @staticmethod
    @helper
    def generate_client_id() -> str:
        return f"{OAuthConstants.CLIENT_ID_PREFIX}{secrets.token_urlsafe(18)}"

    @staticmethod
    @helper
    def generate_client_secret() -> str:
        return f"{OAuthConstants.CLIENT_SECRET_PREFIX}{secrets.token_urlsafe(32)}"

    @staticmethod
    @helper
    def hash_secret(secret: str) -> str:
        salt = secrets.token_hex(16)
        dk = hashlib.pbkdf2_hmac(
            "sha256",
            secret.encode("utf-8"),
            salt.encode("ascii"),
            PBKDF2_ITERATIONS,
        )
        return f"pbkdf2_sha256${PBKDF2_ITERATIONS}${salt}${dk.hex()}"

    @staticmethod
    @rule
    def verify_secret(secret: str, stored_hash: str) -> bool:
        if not secret or not stored_hash:
            return False
        parts = stored_hash.split("$")
        if len(parts) != 4 or parts[0] != "pbkdf2_sha256":
            return False
        try:
            iterations = int(parts[1])
            salt = parts[2]
            expected_hex = parts[3]
        except ValueError:
            return False
        dk = hashlib.pbkdf2_hmac(
            "sha256",
            secret.encode("utf-8"),
            salt.encode("ascii"),
            iterations,
        )
        return hmac.compare_digest(dk.hex(), expected_hex)

    @staticmethod
    @helper
    def hash_code(code: str) -> str:
        return hashlib.sha256(code.encode("utf-8")).hexdigest()

    @staticmethod
    @rule
    def verify_pkce_s256(code_verifier: str, code_challenge: str) -> bool:
        if not code_verifier or not code_challenge:
            return False
        if not (43 <= len(code_verifier) <= 128):
            return False
        digest = hashlib.sha256(code_verifier.encode("ascii")).digest()
        computed = base64.urlsafe_b64encode(digest).rstrip(b"=").decode("ascii")
        return hmac.compare_digest(computed, code_challenge)
