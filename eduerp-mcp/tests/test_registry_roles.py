"""What each system role can reach through MCP. Permissions below are the seeded role permissions."""

from __future__ import annotations

import json
import uuid

import httpx
import pytest

from app.integrations.eduerp.public import EduErpBackendClient
from app.modules.eduerp.public import CallerContext, EduErpMcpToolService
from app.modules.eduerp.registry import EDUERP_ENDPOINTS
from app.platform.permissions.public import PermissionRules

TEST_JWT_SECRET = "test-only-jwt-secret-key-must-be-at-least-32-bytes-long-hs256"

_STUDENT = {
    ("ACCOUNT", "READ"): "PERSONAL",
    ("ACCOUNT", "UPDATE"): "PERSONAL",
    ("LEARNING", "READ"): "PERSONAL",
    ("PRACTICE", "CREATE"): "PERSONAL",
}
_TEACHER = {
    ("ACCOUNT", "READ"): "PERSONAL",
    ("ACCOUNT", "UPDATE"): "PERSONAL",
    ("ASSIGNMENT", "CREATE"): "PERSONAL",
    ("ASSIGNMENT", "READ"): "PERSONAL",
    ("ASSIGNMENT", "UPDATE"): "PERSONAL",
    ("ATTENDANCE", "READ"): "PERSONAL",
    ("ATTENDANCE", "UPDATE"): "PERSONAL",
    ("GRADE", "READ"): "PERSONAL",
    ("GRADE", "UPDATE"): "PERSONAL",
    ("TEACHING", "READ"): "PERSONAL",
}
_ADMIN = {
    (resource, action): "ORGANIZATION"
    for resource in (
        "ACCOUNT", "ASSIGNMENT", "ATTENDANCE", "BRANCH", "CLASS", "COURSE", "DASHBOARD", "ENROLLMENT",
        "GRADE", "INVOICE", "PAYROLL", "PERMISSION_GROUP", "ROLE", "STUDENT", "TEACHER",
    )
    for action in ("CREATE", "READ", "UPDATE", "APPROVE")
    if action != "APPROVE" or resource == "PAYROLL"
}


class _RolePermissions:
    def __init__(self, granted: dict[tuple[str, str], str]) -> None:
        self._granted = granted

    def has_permission(self, _account_id, resource, action, min_scope="PERSONAL") -> bool:
        scope = self._granted.get((resource, action))
        return scope is not None and PermissionRules.covers_scope(scope, min_scope)


def _reachable(granted: dict[tuple[str, str], str]) -> set[str]:
    permissions = _RolePermissions(granted)
    account_id = uuid.uuid4()
    return {
        spec.endpoint_id
        for spec in EDUERP_ENDPOINTS
        if permissions.has_permission(account_id, spec.resource, spec.action, spec.min_scope)
    }


def test_student_reaches_the_whole_learning_portal_and_nothing_administrative() -> None:
    reachable = _reachable(_STUDENT)
    assert {
        "me.account", "me.update_profile", "learning.my_classes", "learning.my_attendance",
        "learning.my_assessments", "learning.mini_task", "learning.submit_mini_task",
        "learning.my_invoices", "learning.pay_invoice", "payroll.my_payslips",
    } <= reachable
    assert reachable.isdisjoint({
        "students.list", "accounts.list", "invoices.list", "enrollments.create", "grades.upsert",
        "attendance.record", "teaching.my_classes", "rbac.catalog",
    })


def test_teacher_reaches_teaching_tools_and_nothing_administrative() -> None:
    reachable = _reachable(_TEACHER)
    assert {
        "teaching.my_classes", "attendance.class", "attendance.record", "grades.class_gradebook",
        "grades.upsert", "grades.class_progress", "assignments.create", "assignments.record_results",
        "mini_tasks.create", "payroll.my_payslips",
    } <= reachable
    assert reachable.isdisjoint({
        "students.list", "accounts.list", "invoices.list", "enrollments.create", "payroll_runs.list",
        "learning.my_classes", "rbac.catalog",
    })


def test_admin_reaches_every_administrative_endpoint() -> None:
    reachable = _reachable(_ADMIN)
    portal_only = {spec.endpoint_id for spec in EDUERP_ENDPOINTS} - reachable
    assert all(endpoint_id.startswith(("learning.", "teaching.")) for endpoint_id in portal_only), portal_only


class _AllowAll:
    def require_permission(self, _account_id, resource, action, min_scope="PERSONAL"):
        from app.runtime.contracts import EffectivePermissionDto

        return EffectivePermissionDto(resource=resource, action=action, scope=min_scope)


class _NoAudit:
    def record(self, actor, action, target="", detail="") -> None:
        pass


def _service(handler) -> EduErpMcpToolService:
    client = EduErpBackendClient(
        base_url="http://erp.test", jwt_secret=TEST_JWT_SECRET, transport=httpx.MockTransport(handler)
    )
    return EduErpMcpToolService(permissions=_AllowAll(), erp_client=client, audit=_NoAudit())


def _caller() -> CallerContext:
    return CallerContext(
        account_id=uuid.uuid4(), email="a@example.com", full_name="A", client_id="mcp_x", auth_mode="oauth2"
    )


@pytest.mark.asyncio
async def test_put_endpoints_are_writes() -> None:
    seen: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request.method)
        return httpx.Response(200, json={})

    await _service(handler).execute_write(
        "rbac.update_role", path_params={"roleId": str(uuid.uuid4())}, body={"name": "X"}, caller=_caller()
    )
    assert seen == ["PUT"]


@pytest.mark.asyncio
async def test_contract_endpoints_send_a_json_request_part() -> None:
    bodies: list[tuple[str, bytes]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        bodies.append((request.headers["content-type"], request.content))
        return httpx.Response(200, json={})

    await _service(handler).execute_write("contracts.create", body={"accountId": "abc"}, caller=_caller())

    content_type, content = bodies[0]
    assert content_type.startswith("multipart/form-data")
    assert b'name="request"' in content
    assert json.dumps({"accountId": "abc"}).encode() in content


@pytest.mark.asyncio
async def test_actions_without_a_body_can_omit_it() -> None:
    def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(204)

    result = await _service(handler).execute_write(
        "classes.start", path_params={"classId": str(uuid.uuid4())}, caller=_caller()
    )
    assert result["statusCode"] == 204
