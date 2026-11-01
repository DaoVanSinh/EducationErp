"""Unit and integration tests for eduerp-mcp auth, Redis RBAC, OAuth 2.1 PKCE, and MCP tools."""

from __future__ import annotations

import base64
import hashlib
import time
import uuid
from datetime import UTC, datetime
from typing import Any
from uuid import UUID

import httpx
import jwt
import pytest
from starlette.requests import Request

from app.infrastructure.sealer import Sealer
from app.integrations.eduerp.public import EduErpBackendClient, EduErpHttpConstants
from app.modules.eduerp.public import CallerContext, EduErpMcpToolService, EduErpModuleConstants
from app.platform.identity.constants import IdentityConstants
from app.platform.oauth.consent_view import OAuthConsentRenderer
from app.platform.oauth.public import (
    ConnectorRecord,
    HubTokenVerifier,
    OAuthConstants,
    OAuthCrypto,
    OAuthRedirectRules,
)
from app.platform.permissions.constants import PermissionConstants
from app.platform.permissions.public import PermissionRules, PermissionService
from app.platform.sessions.constants import SessionConstants
from app.platform.sessions.public import ErpSessionService
from app.runtime.contracts import AccountIdentityDto, EffectivePermissionDto
from app.shared.exceptions import AuthenticationError, Forbidden, PermissionDeniedError
from scripts.check_backend_boundaries import check_boundaries

TEST_JWT_SECRET = "test-only-jwt-secret-key-must-be-at-least-32-bytes-long-hs256"
TEST_SEALER_SECRET = "test-only-mcp-sealer-secret-key-at-least-32-bytes"


class FakeRedisCache:
    def __init__(self) -> None:
        self.permissions: dict[UUID, list[dict[str, str]]] = {}
        self.blacklisted_jtis: set[str] = set()
        self.revoked_families: set[str] = set()

    def is_erp_jti_blacklisted(self, jti: str) -> bool:
        return jti in self.blacklisted_jtis

    def get_erp_permissions(self, account_id: UUID) -> list[dict[str, str]] | None:
        return self.permissions.get(account_id)

    def set_erp_permissions(
        self, account_id: UUID, permissions: list[dict[str, str]], ttl_seconds: int = 600
    ) -> None:
        self.permissions[account_id] = permissions

    def mark_oauth_family_revoked(self, family_id: str, ttl_seconds: int = 86400) -> None:
        self.revoked_families.add(family_id)

    def is_oauth_family_revoked(self, family_id: str) -> bool:
        return family_id in self.revoked_families


class FakeIdentityService:
    def __init__(self, accounts: dict[UUID, AccountIdentityDto]) -> None:
        self._accounts = accounts

    def get_active_account(self, account_id: UUID) -> AccountIdentityDto | None:
        acc = self._accounts.get(account_id)
        if acc is None or acc.status != IdentityConstants.STATUS_ACTIVE:
            return None
        return acc


class FakeOAuthStore:
    def __init__(self, account_id: UUID, client_id: str, raw_secret: str) -> None:
        self.account_id = account_id
        self.client_id = client_id
        self.secret_hash = OAuthCrypto.hash_secret(raw_secret)
        self.active_families: set[str] = {"fam_valid"}

    def verify_client_credentials(self, client_id: str, client_secret: str) -> UUID | None:
        if client_id == self.client_id and OAuthCrypto.verify_secret(
            client_secret, self.secret_hash
        ):
            return self.account_id
        return None

    def is_family_active(self, family_id: str) -> bool:
        return family_id in self.active_families

    def get_connector_by_client_id(self, client_id: str) -> tuple[ConnectorRecord, str] | None:
        if client_id != self.client_id:
            return None
        now = datetime.now(UTC)
        return (
            ConnectorRecord(
                account_id=self.account_id,
                client_id=self.client_id,
                has_secret=True,
                secret_last4="abcd",
                enabled=True,
                created_at=now,
                updated_at=now,
                last_used_at=now,
                secret_rotated_at=now,
            ),
            self.secret_hash,
        )


class FakeAuditWriter:
    def __init__(self) -> None:
        self.events: list[tuple[str, str, str, str]] = []

    def record(self, actor: str, action: str, target: str = "", detail: str = "") -> None:
        self.events.append((actor, action, target, detail))


def _make_request(cookies: dict[str, str], headers: dict[str, str] | None = None) -> Request:
    cookie_header = "; ".join(f"{k}={v}" for k, v in cookies.items())
    raw_headers = [(b"cookie", cookie_header.encode("latin-1"))]
    for k, v in (headers or {}).items():
        raw_headers.append((k.lower().encode("latin-1"), v.encode("latin-1")))
    scope = {
        "type": "http",
        "method": "POST",
        "path": "/api/mcp/my/connector/secret",
        "headers": raw_headers,
        "query_string": b"",
    }
    return Request(scope)


def test_backend_boundaries_pass() -> None:
    assert check_boundaries() == []


def test_sealer_roundtrip_and_tamper_rejection() -> None:
    sealer = Sealer(TEST_SEALER_SECRET)
    token = sealer.seal({"k": "test_kind", "sub": "user-1"}, ttl=60)
    unsealed = sealer.unseal(token, "test_kind")
    assert unsealed is not None
    assert unsealed["sub"] == "user-1"
    assert sealer.unseal(token, "wrong_kind") is None
    assert sealer.unseal(token + "tamper", "test_kind") is None


def test_permission_scope_merging_and_redis_enforcement() -> None:
    account_id = uuid.uuid4()
    cache = FakeRedisCache()
    cache.set_erp_permissions(
        account_id,
        [
            {"resource": "STUDENT", "action": "READ", "scope": "PERSONAL"},
            {"resource": "STUDENT", "action": "READ", "scope": "BRANCH"},
            {"resource": "COURSE", "action": "READ", "scope": "ORGANIZATION"},
        ],
    )
    svc = PermissionService(db=None, cache=cache)  # type: ignore[arg-type]

    effective = svc.get_effective_permissions(account_id)
    by_res = {(p.resource, p.action): p.scope for p in effective}
    assert by_res[("STUDENT", "READ")] == PermissionConstants.Scopes.BRANCH
    assert by_res[("COURSE", "READ")] == PermissionConstants.Scopes.ORGANIZATION

    # Has BRANCH scope on STUDENT:READ -> satisfies PERSONAL and BRANCH, but not ORGANIZATION
    assert svc.has_permission(account_id, "STUDENT", "READ", "PERSONAL") is True
    assert svc.has_permission(account_id, "STUDENT", "READ", "BRANCH") is True
    assert svc.has_permission(account_id, "STUDENT", "READ", "ORGANIZATION") is False

    with pytest.raises(Forbidden):
        svc.require_permission(account_id, "STUDENT", "READ", "ORGANIZATION")

    with pytest.raises(Forbidden):
        svc.require_permission(account_id, "INVOICE", "CREATE", "BRANCH")


def test_erp_session_service_jwt_cookie_blacklist_and_csrf() -> None:
    account_id = uuid.uuid4()
    jti = str(uuid.uuid4())
    cache = FakeRedisCache()
    identity = FakeIdentityService(
        {
            account_id: AccountIdentityDto(
                account_id=account_id,
                email="teacher@eduerp.vn",
                full_name="Nguyen Van A",
                status=IdentityConstants.STATUS_ACTIVE,
                home_branch_id=None,
                role_codes=("TEACHER",),
            )
        }
    )
    sessions = ErpSessionService(
        jwt_secret=TEST_JWT_SECRET,
        sealer=Sealer(TEST_SEALER_SECRET),
        cache=cache,  # type: ignore[arg-type]
        identity=identity,  # type: ignore[arg-type]
    )

    now = int(time.time())
    access_jwt = jwt.encode(
        {
            "sub": str(account_id),
            "jti": jti,
            SessionConstants.JWT_TYPE_CLAIM: SessionConstants.JWT_ACCESS_TYPE,
            "iat": now,
            "exp": now + 900,
        },
        TEST_JWT_SECRET.encode("utf-8"),
        algorithm=SessionConstants.JWT_ALGORITHM,
    )

    req = _make_request(
        cookies={
            SessionConstants.ACCESS_TOKEN_COOKIE: access_jwt,
            SessionConstants.CSRF_COOKIE: "csrf-123",
        },
        headers={SessionConstants.CSRF_HEADER: "csrf-123"},
    )
    actor = sessions.require_actor(req)
    assert actor.principal_id == account_id
    assert actor.email == "teacher@eduerp.vn"
    sessions.verify_double_submit_csrf(req)

    # Mismatched CSRF header raises PermissionDeniedError
    bad_csrf_req = _make_request(
        cookies={
            SessionConstants.ACCESS_TOKEN_COOKIE: access_jwt,
            SessionConstants.CSRF_COOKIE: "csrf-123",
        },
        headers={SessionConstants.CSRF_HEADER: "wrong"},
    )
    with pytest.raises(PermissionDeniedError):
        sessions.verify_double_submit_csrf(bad_csrf_req)

    # Blacklisted JTI in shared Redis is rejected immediately
    cache.blacklisted_jtis.add(jti)
    with pytest.raises(AuthenticationError):
        sessions.require_actor(req)


async def test_oauth_pkce_and_hub_token_verifier_modes() -> None:
    verifier_str = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    challenge = (
        base64.urlsafe_b64encode(hashlib.sha256(verifier_str.encode("ascii")).digest())
        .rstrip(b"=")
        .decode("ascii")
    )
    assert OAuthCrypto.verify_pkce_s256(verifier_str, challenge) is True
    assert OAuthCrypto.verify_pkce_s256("wrong" * 10, challenge) is False

    # Claude and ChatGPT redirect URIs are allowed
    assert OAuthRedirectRules.is_allowed_redirect_uri(
        "https://claude.ai/api/mcp/auth_callback",
        OAuthConstants.DEFAULT_REDIRECT_HOSTS,
        OAuthConstants.DEFAULT_REDIRECT_PREFIXES,
    )
    assert OAuthRedirectRules.is_allowed_redirect_uri(
        "https://chatgpt.com/connector_platform_oauth_redirect",
        OAuthConstants.DEFAULT_REDIRECT_HOSTS,
        OAuthConstants.DEFAULT_REDIRECT_PREFIXES,
    )
    assert not OAuthRedirectRules.is_allowed_redirect_uri(
        "https://evil.example.com/callback",
        OAuthConstants.DEFAULT_REDIRECT_HOSTS,
        OAuthConstants.DEFAULT_REDIRECT_PREFIXES,
    )

    account_id = uuid.uuid4()
    client_id = OAuthCrypto.generate_client_id()
    raw_secret = OAuthCrypto.generate_client_secret()
    sealer = Sealer(TEST_SEALER_SECRET)
    store = FakeOAuthStore(account_id, client_id, raw_secret)
    identity = FakeIdentityService(
        {
            account_id: AccountIdentityDto(
                account_id=account_id,
                email="admin@eduerp.vn",
                full_name="System Admin",
                status=IdentityConstants.STATUS_ACTIVE,
                home_branch_id=None,
                role_codes=("ADMIN",),
            )
        }
    )
    token_verifier = HubTokenVerifier(
        sealer=sealer,
        store=store,  # type: ignore[arg-type]
        identity=identity,  # type: ignore[arg-type]
    )

    # Mode 1: Direct IDE Bearer Token `<client_id>.<client_secret>`
    bearer_token = f"{client_id}.{raw_secret}"
    verified_ide = await token_verifier.verify_token(bearer_token)
    assert verified_ide is not None
    assert verified_ide.claims["account_id"] == str(account_id)
    assert verified_ide.claims["auth_mode"] == "bearer_secret"

    # Mode 2: Sealed OAuth 2.1 Access Token
    oauth_access = sealer.seal(
        {
            "k": OAuthConstants.SEAL_KIND_ACCESS_TOKEN,
            "sub": str(account_id),
            "cid": client_id,
            "fam": "fam_valid",
            "scp": OAuthConstants.DEFAULT_SCOPE,
        },
        ttl=3600,
    )
    verified_oauth = await token_verifier.verify_token(oauth_access)
    assert verified_oauth is not None
    assert verified_oauth.claims["account_id"] == str(account_id)
    assert verified_oauth.claims["auth_mode"] == "oauth2"


async def test_mcp_tool_service_live_permission_and_upstream_forwarding() -> None:
    account_id = uuid.uuid4()
    cache = FakeRedisCache()
    cache.set_erp_permissions(
        account_id,
        [
            {
                "resource": EduErpModuleConstants.Resources.COURSE,
                "action": EduErpModuleConstants.Actions.READ,
                "scope": EduErpModuleConstants.Scopes.BRANCH,
            },
            {
                "resource": EduErpModuleConstants.Resources.STUDENT,
                "action": EduErpModuleConstants.Actions.CREATE,
                "scope": EduErpModuleConstants.Scopes.BRANCH,
            },
        ],
    )
    permissions = PermissionService(db=None, cache=cache)  # type: ignore[arg-type]
    audit = FakeAuditWriter()

    captured_requests: list[httpx.Request] = []

    async def mock_handler(request: httpx.Request) -> httpx.Response:
        captured_requests.append(request)
        assert EduErpHttpConstants.ACCESS_TOKEN_COOKIE in request.headers.get("cookie", "")
        assert request.headers.get(EduErpHttpConstants.CSRF_HEADER.lower())
        return httpx.Response(200, json={"items": [{"id": "c-1", "name": "IELTS Foundation"}]})

    erp_client = EduErpBackendClient(
        base_url="http://app:8080",
        jwt_secret=TEST_JWT_SECRET,
        transport=httpx.MockTransport(mock_handler),
    )
    tool_service = EduErpMcpToolService(
        permissions=permissions,
        erp_client=erp_client,
        audit=audit,
    )
    caller = CallerContext(
        account_id=account_id,
        email="counselor@eduerp.vn",
        full_name="Admissions Counselor",
        client_id="mcp_test",
        auth_mode="oauth2",
    )

    # 1. Context reflects live permissions
    ctx_data = tool_service.get_my_context(caller)
    assert ctx_data["accessibleEndpointCount"] == 2
    assert set(ctx_data["accessibleEndpoints"]) == {"courses.list", "students.create"}

    # 2. Search returns only permitted endpoints
    search_res = tool_service.search_endpoints(caller=caller)
    assert search_res["total"] == 2

    # 3. Permitted read succeeds and forwards internal JWT to EduERP
    read_res = await tool_service.execute_read(
        "courses.list",
        query_params={"page": 0, "size": 10},
        caller=caller,
    )
    assert read_res["statusCode"] == 200
    assert len(captured_requests) == 1
    assert captured_requests[0].url.path == "/api/courses/courses"

    # 4. Unpermitted read (e.g. accounts.list requires ACCOUNT:READ ORGANIZATION) is blocked before HTTP
    with pytest.raises(Forbidden):
        await tool_service.execute_read("accounts.list", caller=caller)
    assert len(captured_requests) == 1

    # 5. Permitted write succeeds and records an audit log
    write_res = await tool_service.execute_write(
        "students.create",
        body={"studentCode": "HV001", "fullName": "Tran Thi B", "branchId": str(uuid.uuid4())},
        caller=caller,
    )
    assert write_res["statusCode"] == 200
    assert len(audit.events) == 1
    assert audit.events[0][1] == "mcp.write.students.create"


def test_oauth_consent_template_and_logo_render() -> None:
    logo = OAuthConsentRenderer.logo_bytes()
    assert logo.startswith(b"\x89PNG\r\n\x1a\n")

    consent_html = OAuthConsentRenderer.render_consent(
        client_id="mcp_demo_123",
        account="Nguyễn Văn A",
        mcp_title="DS Academy MCP · Claude (Anthropic)",
        hidden_fields={"csrf": "csrf_token_abc", "client_id": "mcp_demo_123"},
    )
    assert "DS Academy" in consent_html
    assert "mcp_demo_123" in consent_html
    assert "Nguyễn Văn A" in consent_html
    assert 'name="csrf" value="csrf_token_abc"' in consent_html
    assert 'src="/oauth/logo.png?v=2"' in consent_html
    assert "orangeWaveBack" in consent_html

    err_html = OAuthConsentRenderer.render_error("Yêu cầu không hợp lệ <script>")
    assert "Yêu cầu không hợp lệ &lt;script&gt;" in err_html
    assert 'class="err"' in err_html
    assert "<form" not in err_html
