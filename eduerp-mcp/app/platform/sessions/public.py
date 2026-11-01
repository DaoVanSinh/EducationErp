"""Verify EduERP `access_token` cookie, Redis `blacklist:jti:<jti>`, and CSRF tokens."""

from __future__ import annotations

import hmac
from uuid import UUID

import jwt
from starlette.requests import Request

from app.infrastructure.sealer import Sealer
from app.integrations.cache.public import SharedRedisCache
from app.platform.identity.public import IdentityService
from app.platform.sessions.constants import SessionConstants
from app.shared.actors import Actor
from app.shared.base.markers import facade, helper, rule
from app.shared.constants import ActorKind
from app.shared.exceptions import AuthenticationError, PermissionDeniedError


class ErpSessionService:
    """Authenticate requests carrying EduERP's `access_token` cookie."""

    def __init__(
        self,
        *,
        jwt_secret: str,
        sealer: Sealer,
        cache: SharedRedisCache,
        identity: IdentityService,
    ) -> None:
        self._jwt_secret = jwt_secret.encode("utf-8")
        self._sealer = sealer
        self._cache = cache
        self._identity = identity

    @helper
    def _decode_access_jwt(self, token: str) -> tuple[UUID, str] | None:
        if not token:
            return None
        try:
            payload = jwt.decode(
                token,
                self._jwt_secret,
                algorithms=[SessionConstants.JWT_ALGORITHM],
                options={"require": ["sub", "jti", "exp"]},
            )
        except Exception:
            return None

        if payload.get(SessionConstants.JWT_TYPE_CLAIM) != SessionConstants.JWT_ACCESS_TYPE:
            return None

        raw_sub = payload.get("sub")
        raw_jti = payload.get("jti")
        if not raw_sub or not raw_jti:
            return None
        try:
            return UUID(str(raw_sub)), str(raw_jti)
        except ValueError:
            return None

    @facade
    def current_actor(self, request: Request) -> Actor | None:
        """Return the authenticated EduERP Actor from the `access_token` cookie, or None."""
        raw_token = request.cookies.get(SessionConstants.ACCESS_TOKEN_COOKIE, "")
        decoded = self._decode_access_jwt(raw_token)
        if decoded is None:
            return None
        account_id, jti = decoded
        if self._cache.is_erp_jti_blacklisted(jti):
            return None
        account = self._identity.get_active_account(account_id)
        if account is None:
            return None
        return Actor(
            kind=ActorKind.ERP,
            name=account.email,
            principal_id=account.account_id,
            email=account.email,
            full_name=account.full_name,
            home_branch_id=account.home_branch_id,
            jti=jti,
        )

    @facade
    def require_actor(self, request: Request) -> Actor:
        actor = self.current_actor(request)
        if actor is None or actor.principal_id is None:
            raise AuthenticationError(
                message="Phiên đăng nhập không hợp lệ hoặc đã hết hạn.",
                error_code=SessionConstants.ErrorCodes.UNAUTHENTICATED,
            )
        return actor

    @rule
    def verify_double_submit_csrf(self, request: Request) -> None:
        """Verify EduERP's XSRF-TOKEN cookie matches X-XSRF-TOKEN header on JSON mutations."""
        cookie_token = request.cookies.get(SessionConstants.CSRF_COOKIE, "")
        header_token = request.headers.get(SessionConstants.CSRF_HEADER, "")
        if not cookie_token or not header_token or not hmac.compare_digest(cookie_token, header_token):
            raise PermissionDeniedError(
                message="Xác thực CSRF không hợp lệ. Vui lòng tải lại trang.",
                error_code=SessionConstants.ErrorCodes.CSRF_INVALID,
            )

    @facade
    def mint_consent_csrf(self, account_id: UUID) -> str:
        """Mint a short-lived signed CSRF token bound to account_id for the HTML OAuth consent form."""
        return self._sealer.seal(
            {"k": SessionConstants.CSRF_SEAL_KIND, "sub": str(account_id)},
            ttl=SessionConstants.CSRF_SEAL_TTL_SECONDS,
        )

    @facade
    def verify_consent_csrf(self, account_id: UUID, token: str) -> bool:
        data = self._sealer.unseal(token, SessionConstants.CSRF_SEAL_KIND)
        return data is not None and data.get("sub") == str(account_id)
