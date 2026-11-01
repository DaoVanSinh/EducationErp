"""FastMCP TokenVerifier supporting both <client_id>.<client_secret> Bearer and OAuth 2.1 tokens."""

from __future__ import annotations

from uuid import UUID

from fastmcp.server.auth.auth import AccessToken, TokenVerifier

from app.infrastructure.sealer import Sealer
from app.platform.identity.public import IdentityService
from app.platform.oauth.constants import OAuthConstants
from app.platform.oauth.store import OAuthStore
from app.shared.base.markers import helper


class HubTokenVerifier(TokenVerifier):
    """Verify MCP HTTP Bearer tokens for both IDEs and OAuth 2.1 clients (Claude / ChatGPT)."""

    def __init__(
        self,
        *,
        sealer: Sealer,
        store: OAuthStore,
        identity: IdentityService,
    ) -> None:
        super().__init__()
        self._sealer = sealer
        self._store = store
        self._identity = identity

    @helper
    def _verify_bearer_secret_token(self, token: str) -> AccessToken | None:
        if not token.startswith(OAuthConstants.CLIENT_ID_PREFIX) or "." not in token:
            return None
        client_id, _, client_secret = token.partition(".")
        if not client_secret.startswith(OAuthConstants.CLIENT_SECRET_PREFIX):
            return None
        account_id = self._store.verify_client_credentials(client_id, client_secret)
        if account_id is None:
            return None
        account = self._identity.get_active_account(account_id)
        if account is None:
            return None
        return AccessToken(
            token=token,
            client_id=client_id,
            scopes=[OAuthConstants.DEFAULT_SCOPE],
            expires_at=None,
            claims={
                "account_id": str(account.account_id),
                "email": account.email,
                "full_name": account.full_name,
                "client_id": client_id,
                "auth_mode": OAuthConstants.AuthModes.BEARER_SECRET,
            },
        )

    @helper
    def _verify_oauth_access_token(self, token: str) -> AccessToken | None:
        data = self._sealer.unseal(token, OAuthConstants.SEAL_KIND_ACCESS_TOKEN)
        if data is None:
            return None

        family_id = str(data.get("fam") or "")
        raw_sub = str(data.get("sub") or "")
        client_id = str(data.get("cid") or "")
        if not (family_id and raw_sub and client_id):
            return None
        try:
            account_id = UUID(raw_sub)
        except ValueError:
            return None

        if not self._store.is_family_active(family_id):
            return None

        account = self._identity.get_active_account(account_id)
        if account is None:
            return None

        scope_str = str(data.get("scp") or OAuthConstants.DEFAULT_SCOPE)
        scopes = [s for s in scope_str.split() if s] or [OAuthConstants.DEFAULT_SCOPE]

        return AccessToken(
            token=token,
            client_id=client_id,
            scopes=scopes,
            expires_at=int(data["exp"]),
            claims={
                "account_id": str(account.account_id),
                "email": account.email,
                "full_name": account.full_name,
                "client_id": client_id,
                "family_id": family_id,
                "auth_mode": OAuthConstants.AuthModes.OAUTH2,
            },
        )

    async def verify_token(self, token: str) -> AccessToken | None:
        if not token:
            return None
        bearer_result = self._verify_bearer_secret_token(token)
        if bearer_result is not None:
            return bearer_result
        return self._verify_oauth_access_token(token)
