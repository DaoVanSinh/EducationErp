"""OAuth 2.1 Authorization Code + PKCE HTTP endpoints and RFC 8414 / 9728 metadata."""

from __future__ import annotations

import secrets
from dataclasses import dataclass
from urllib.parse import urlencode, urlsplit, urlunsplit
from uuid import UUID

from starlette.requests import Request
from starlette.responses import HTMLResponse, JSONResponse, RedirectResponse, Response
from starlette.routing import Route

from app.infrastructure.sealer import Sealer
from app.platform.audit.public import AuditService
from app.platform.oauth.consent_view import OAuthConsentRenderer
from app.platform.oauth.constants import OAuthConstants
from app.platform.oauth.crypto import OAuthCrypto
from app.platform.oauth.store import OAuthAppNaming, OAuthStore
from app.platform.sessions.public import ErpSessionService
from app.shared.actors import Actor
from app.shared.base.markers import helper, rule
from app.shared.security.constants import SecurityConstants
from app.shared.urls import UrlRules


@dataclass(frozen=True)
class AuthorizeParams:
    client_id: str
    redirect_uri: str
    response_type: str
    state: str
    code_challenge: str
    code_challenge_method: str
    scope: str
    resource: str


class OAuthRedirectRules:
    """Validate OAuth redirect_uri against allowed hosts and HTTPS/loopback rules."""

    @staticmethod
    @rule
    def is_allowed_redirect_uri(
        redirect_uri: str,
        allowed_hosts: tuple[str, ...],
        allowed_prefixes: tuple[str, ...],
    ) -> bool:
        if not redirect_uri or "#" in redirect_uri or not UrlRules.secure(redirect_uri):
            return False
        parts = urlsplit(redirect_uri)
        host = (parts.hostname or "").lower()
        path = parts.path or "/"
        if host in ("localhost", "127.0.0.1"):
            return True
        for prefix in allowed_prefixes:
            parsed_prefix = UrlRules.host_path_prefix(prefix)
            if parsed_prefix is not None:
                p_host, p_path = parsed_prefix
                if host == p_host and path.startswith(p_path):
                    return True
        return any(host == h or host.endswith(f".{h}") for h in allowed_hosts)

    @staticmethod
    @helper
    def append_query(url: str, params: dict[str, str]) -> str:
        parts = urlsplit(url)
        existing = parts.query
        added = urlencode({k: v for k, v in params.items() if v != ""})
        query = f"{existing}&{added}" if existing else added
        return urlunsplit((parts.scheme, parts.netloc, parts.path, query, ""))


class OAuthHttpEndpoints:
    """Starlette handlers for /oauth/authorize, /oauth/token, /oauth/revoke, and /.well-known/*."""

    def __init__(
        self,
        *,
        public_url: str,
        allowed_redirect_hosts: tuple[str, ...],
        allowed_redirect_prefixes: tuple[str, ...],
        sealer: Sealer,
        store: OAuthStore,
        sessions: ErpSessionService,
        audit: AuditService,
    ) -> None:
        self._public_url = public_url.rstrip("/")
        self._allowed_hosts = allowed_redirect_hosts
        self._allowed_prefixes = allowed_redirect_prefixes
        self._sealer = sealer
        self._store = store
        self._sessions = sessions
        self._audit = audit

    @helper
    def routes(self) -> list[Route]:
        return [
            Route(
                OAuthConstants.Routes.WELL_KNOWN_AUTH_SERVER,
                self.authorization_server_metadata,
                methods=["GET"],
            ),
            Route(
                OAuthConstants.Routes.WELL_KNOWN_PROTECTED_RESOURCE,
                self.protected_resource_metadata,
                methods=["GET"],
            ),
            Route(
                OAuthConstants.Routes.LOGO,
                self.logo_get,
                methods=["GET"],
            ),
            Route(
                OAuthConstants.Routes.AUTHORIZE,
                self.authorize_get,
                methods=["GET"],
            ),
            Route(
                OAuthConstants.Routes.AUTHORIZE,
                self.authorize_post,
                methods=["POST"],
            ),
            Route(
                OAuthConstants.Routes.TOKEN,
                self.token_post,
                methods=["POST"],
            ),
            Route(
                OAuthConstants.Routes.REVOKE,
                self.revoke_post,
                methods=["POST"],
            ),
        ]

    async def logo_get(self, _request: Request) -> Response:
        return Response(
            OAuthConsentRenderer.logo_bytes(),
            media_type=OAuthConstants.Templates.LOGO_MEDIA_TYPE,
            headers=SecurityConstants.SECURITY_HEADERS,
        )

    async def authorization_server_metadata(self, _request: Request) -> JSONResponse:
        return JSONResponse(
            {
                "issuer": self._public_url,
                "authorization_endpoint": f"{self._public_url}{OAuthConstants.Routes.AUTHORIZE}",
                "token_endpoint": f"{self._public_url}{OAuthConstants.Routes.TOKEN}",
                "revocation_endpoint": f"{self._public_url}{OAuthConstants.Routes.REVOKE}",
                "response_types_supported": ["code"],
                "grant_types_supported": [
                    OAuthConstants.GRANT_AUTHORIZATION_CODE,
                    OAuthConstants.GRANT_REFRESH_TOKEN,
                ],
                "token_endpoint_auth_methods_supported": ["client_secret_post", "none"],
                "code_challenge_methods_supported": [OAuthConstants.CODE_CHALLENGE_METHOD_S256],
                "scopes_supported": [OAuthConstants.DEFAULT_SCOPE],
            }
        )

    async def protected_resource_metadata(self, _request: Request) -> JSONResponse:
        return JSONResponse(
            {
                "resource": f"{self._public_url}/mcp/eduerp",
                "authorization_servers": [self._public_url],
                "bearer_methods_supported": ["header"],
                "scopes_supported": [OAuthConstants.DEFAULT_SCOPE],
            }
        )

    @helper
    def _extract_authorize_params(self, source: dict[str, str]) -> AuthorizeParams:
        return AuthorizeParams(
            client_id=source.get("client_id", "").strip(),
            redirect_uri=source.get("redirect_uri", "").strip(),
            response_type=source.get("response_type", "code").strip(),
            state=source.get("state", "").strip(),
            code_challenge=source.get("code_challenge", "").strip(),
            code_challenge_method=source.get(
                "code_challenge_method", OAuthConstants.CODE_CHALLENGE_METHOD_S256
            ).strip(),
            scope=source.get("scope", OAuthConstants.DEFAULT_SCOPE).strip()
            or OAuthConstants.DEFAULT_SCOPE,
            resource=source.get("resource", "").strip(),
        )

    @rule
    def _validate_authorize_request(
        self, actor: Actor, params: AuthorizeParams
    ) -> str | None:
        if not params.client_id or not params.redirect_uri:
            return "Thiếu client_id hoặc redirect_uri."
        if not OAuthRedirectRules.is_allowed_redirect_uri(
            params.redirect_uri, self._allowed_hosts, self._allowed_prefixes
        ):
            return "Địa chỉ redirect_uri không nằm trong danh sách ứng dụng AI được phép."
        if params.response_type != "code":
            return "Chỉ hỗ trợ response_type=code (OAuth 2.1)."
        if (
            not params.code_challenge
            or params.code_challenge_method != OAuthConstants.CODE_CHALLENGE_METHOD_S256
        ):
            return "Yêu cầu PKCE với code_challenge_method=S256."
        lookup = self._store.get_connector_by_client_id(params.client_id)
        if lookup is None:
            return "Client ID không tồn tại. Vui lòng kiểm tra lại trong trang Kết nối AI (MCP)."
        connector, _ = lookup
        if not connector.enabled or connector.account_id != actor.principal_id:
            return "Client ID này không thuộc về tài khoản DS Academy đang đăng nhập của bạn."
        return None

    @helper
    def _render_error_page(self, message: str, status_code: int = 400) -> HTMLResponse:
        return HTMLResponse(
            OAuthConsentRenderer.render_error(message),
            status_code=status_code,
            headers=SecurityConstants.SECURITY_HEADERS,
        )

    @helper
    def _render_consent_page(self, actor: Actor, params: AuthorizeParams) -> HTMLResponse:
        assert actor.principal_id is not None
        csrf = self._sessions.mint_consent_csrf(actor.principal_id)
        _, app_name, _ = OAuthAppNaming.resolve_app_metadata(params.redirect_uri)
        hidden_fields = {
            "csrf": csrf,
            "client_id": params.client_id,
            "redirect_uri": params.redirect_uri,
            "response_type": params.response_type,
            "state": params.state,
            "code_challenge": params.code_challenge,
            "code_challenge_method": params.code_challenge_method,
            "scope": params.scope,
            "resource": params.resource,
        }
        account_label = actor.full_name or actor.email or actor.name
        mcp_title = f"{OAuthConstants.ConsentCopy.MCP_TITLE} · {app_name}"
        return HTMLResponse(
            OAuthConsentRenderer.render_consent(
                client_id=params.client_id,
                account=account_label,
                mcp_title=mcp_title,
                hidden_fields=hidden_fields,
            ),
            status_code=200,
            headers=SecurityConstants.SECURITY_HEADERS,
        )

    async def authorize_get(self, request: Request) -> Response:
        actor = self._sessions.current_actor(request)
        if actor is None:
            query_str = request.url.query
            target_path = (
                f"{OAuthConstants.Routes.AUTHORIZE}?{query_str}"
                if query_str
                else OAuthConstants.Routes.AUTHORIZE
            )
            login_url = f"{OAuthConstants.Routes.LOGIN_REDIRECT_PATH}?{urlencode({'next': target_path})}"
            return RedirectResponse(login_url, status_code=302)

        params = self._extract_authorize_params(dict(request.query_params))
        err = self._validate_authorize_request(actor, params)
        if err is not None:
            return self._render_error_page(err, status_code=400)
        return self._render_consent_page(actor, params)

    async def authorize_post(self, request: Request) -> Response:
        actor = self._sessions.current_actor(request)
        if actor is None or actor.principal_id is None:
            return self._render_error_page(
                "Phiên đăng nhập đã hết hạn. Vui lòng mở lại liên kết từ ứng dụng AI.",
                status_code=401,
            )

        form = await request.form()
        form_dict = {str(k): str(v) for k, v in form.items()}
        csrf_token = form_dict.get("csrf", "")
        if not self._sessions.verify_consent_csrf(actor.principal_id, csrf_token):
            return self._render_error_page(
                "Biểu mẫu xác nhận đã hết hạn. Vui lòng thử lại.",
                status_code=403,
            )

        params = self._extract_authorize_params(form_dict)
        err = self._validate_authorize_request(actor, params)
        if err is not None:
            return self._render_error_page(err, status_code=400)

        decision = form_dict.get(
            OAuthConstants.Decisions.FIELD, OAuthConstants.Decisions.APPROVE
        )
        if decision == OAuthConstants.Decisions.DENY:
            denied_url = OAuthRedirectRules.append_query(
                params.redirect_uri,
                {
                    "error": OAuthConstants.OAuthErrors.ACCESS_DENIED,
                    "state": params.state,
                },
            )
            return RedirectResponse(denied_url, status_code=303)

        code = self._sealer.seal(
            {
                "k": OAuthConstants.SEAL_KIND_AUTH_CODE,
                "jti": secrets.token_urlsafe(16),
                "sub": str(actor.principal_id),
                "cid": params.client_id,
                "uri": params.redirect_uri,
                "cc": params.code_challenge,
                "scp": params.scope,
                "res": params.resource,
            },
            ttl=OAuthConstants.AUTH_CODE_TTL_SECONDS,
        )
        self._audit.record(
            actor=actor.name,
            action=OAuthConstants.AuditActions.AUTHORIZE,
            target=params.client_id,
            detail=params.redirect_uri,
        )
        redirect_to = OAuthRedirectRules.append_query(
            params.redirect_uri,
            {"code": code, "state": params.state},
        )
        return RedirectResponse(redirect_to, status_code=303)

    @helper
    def _issue_token_pair(
        self,
        *,
        account_id: UUID,
        client_id: str,
        family_id: str,
        refresh_jti: str,
        scope: str,
        resource: str,
    ) -> JSONResponse:
        access_token = self._sealer.seal(
            {
                "k": OAuthConstants.SEAL_KIND_ACCESS_TOKEN,
                "sub": str(account_id),
                "cid": client_id,
                "fam": family_id,
                "scp": scope,
                "res": resource,
            },
            ttl=OAuthConstants.ACCESS_TOKEN_TTL_SECONDS,
        )
        refresh_token = self._sealer.seal(
            {
                "k": OAuthConstants.SEAL_KIND_REFRESH_TOKEN,
                "sub": str(account_id),
                "cid": client_id,
                "fam": family_id,
                "jti": refresh_jti,
                "scp": scope,
                "res": resource,
            },
            ttl=OAuthConstants.REFRESH_TOKEN_TTL_SECONDS,
        )
        return JSONResponse(
            {
                "access_token": access_token,
                "token_type": "Bearer",
                "expires_in": OAuthConstants.ACCESS_TOKEN_TTL_SECONDS,
                "refresh_token": refresh_token,
                "scope": scope,
            },
            headers={"Cache-Control": "no-store", "Pragma": "no-cache"},
        )

    @rule
    def _verify_code_exchange_claims(
        self, unsealed: dict[str, object], form_dict: dict[str, str]
    ) -> JSONResponse | str:
        client_id = form_dict.get("client_id", "").strip()
        redirect_uri = form_dict.get("redirect_uri", "").strip()
        code_verifier = form_dict.get("code_verifier", "").strip()
        client_secret = form_dict.get("client_secret", "").strip()

        sealed_cid = str(unsealed.get("cid") or "")
        if client_id and client_id != sealed_cid:
            return JSONResponse({"error": OAuthConstants.OAuthErrors.INVALID_CLIENT}, status_code=400)
        if redirect_uri and redirect_uri != str(unsealed.get("uri") or ""):
            return JSONResponse({"error": OAuthConstants.OAuthErrors.INVALID_GRANT}, status_code=400)
        if not OAuthCrypto.verify_pkce_s256(code_verifier, str(unsealed.get("cc") or "")):
            return JSONResponse({"error": OAuthConstants.OAuthErrors.INVALID_GRANT}, status_code=400)

        lookup = self._store.get_connector_by_client_id(sealed_cid)
        if lookup is None or not lookup[0].enabled:
            return JSONResponse({"error": OAuthConstants.OAuthErrors.INVALID_CLIENT}, status_code=401)
        _, secret_hash = lookup
        if client_secret and not OAuthCrypto.verify_secret(client_secret, secret_hash):
            return JSONResponse({"error": OAuthConstants.OAuthErrors.INVALID_CLIENT}, status_code=401)
        return sealed_cid

    @helper
    def _handle_authorization_code_grant(self, form_dict: dict[str, str]) -> JSONResponse:
        code = form_dict.get("code", "").strip()
        unsealed = self._sealer.unseal(code, OAuthConstants.SEAL_KIND_AUTH_CODE)
        if unsealed is None:
            return JSONResponse({"error": OAuthConstants.OAuthErrors.INVALID_GRANT}, status_code=400)

        verified = self._verify_code_exchange_claims(unsealed, form_dict)
        if isinstance(verified, JSONResponse):
            return verified
        sealed_cid = verified

        account_id = UUID(str(unsealed["sub"]))
        if not self._store.consume_auth_code_once(
            code, account_id, sealed_cid, OAuthConstants.AUTH_CODE_TTL_SECONDS
        ):
            return JSONResponse({"error": OAuthConstants.OAuthErrors.INVALID_GRANT}, status_code=400)

        family_id = f"fam_{secrets.token_urlsafe(18)}"
        refresh_jti = secrets.token_urlsafe(18)
        scope = str(unsealed.get("scp") or OAuthConstants.DEFAULT_SCOPE)
        resource = str(unsealed.get("res") or "")

        self._store.create_token_family_and_connection(
            family_id=family_id,
            account_id=account_id,
            client_id=sealed_cid,
            refresh_jti=refresh_jti,
            scope=scope,
            resource=resource,
            redirect_uri=str(unsealed.get("uri") or ""),
        )
        return self._issue_token_pair(
            account_id=account_id,
            client_id=sealed_cid,
            family_id=family_id,
            refresh_jti=refresh_jti,
            scope=scope,
            resource=resource,
        )

    @helper
    def _handle_refresh_token_grant(self, form_dict: dict[str, str]) -> JSONResponse:
        raw_refresh = form_dict.get("refresh_token", "").strip()
        unsealed = self._sealer.unseal(raw_refresh, OAuthConstants.SEAL_KIND_REFRESH_TOKEN)
        if unsealed is None:
            return JSONResponse({"error": "invalid_grant"}, status_code=400)

        family_id = str(unsealed.get("fam") or "")
        expected_jti = str(unsealed.get("jti") or "")
        new_jti = secrets.token_urlsafe(18)
        rotated = self._store.rotate_refresh_token(
            family_id=family_id,
            expected_jti=expected_jti,
            new_jti=new_jti,
        )
        if rotated is None:
            return JSONResponse({"error": "invalid_grant"}, status_code=400)

        return self._issue_token_pair(
            account_id=rotated["account_id"],
            client_id=str(rotated["client_id"]),
            family_id=family_id,
            refresh_jti=new_jti,
            scope=str(rotated["scope"] or OAuthConstants.DEFAULT_SCOPE),
            resource=str(rotated["resource"] or ""),
        )

    async def token_post(self, request: Request) -> JSONResponse:
        form = await request.form()
        form_dict = {str(k): str(v) for k, v in form.items()}
        grant_type = form_dict.get("grant_type", "").strip()
        if grant_type == OAuthConstants.GRANT_AUTHORIZATION_CODE:
            return self._handle_authorization_code_grant(form_dict)
        if grant_type == OAuthConstants.GRANT_REFRESH_TOKEN:
            return self._handle_refresh_token_grant(form_dict)
        return JSONResponse({"error": "unsupported_grant_type"}, status_code=400)

    async def revoke_post(self, request: Request) -> JSONResponse:
        form = await request.form()
        token = str(form.get("token") or "").strip()
        if token:
            for kind in (
                OAuthConstants.SEAL_KIND_REFRESH_TOKEN,
                OAuthConstants.SEAL_KIND_ACCESS_TOKEN,
            ):
                data = self._sealer.unseal(token, kind)
                if data is not None and data.get("fam"):
                    self._store.revoke_family(
                        str(data["fam"]),
                        reason=OAuthConstants.RevokeReasons.OAUTH_REVOKE_ENDPOINT,
                    )
                    break
        return JSONResponse({}, status_code=200)
