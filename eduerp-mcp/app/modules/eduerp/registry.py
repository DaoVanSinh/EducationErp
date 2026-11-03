"""Curated EduERP endpoint registry with exact RBAC (resource, action, min_scope) requirements.

Not exposed on purpose: credential flows (login, password change/reset, invite completion), so an
AI client never handles a password; binary downloads and uploads (exports, avatar, contract scans);
the notification SSE stream; and the VNPay server-to-server callbacks.

Every entry mirrors one Spring controller method: same HTTP method and path, the same
`@PreAuthorize(AccessConstants.AccessRules.X)` resource/action/minimum scope, the same
`@RequestParam` names and the same request-record field names. The backend re-checks every
call, so a mismatch here never grants extra access - it only makes a tool fail for users who
should be allowed (or advertise a tool they cannot really use). Keep both sides in sync.
"""

from __future__ import annotations

from app.modules.eduerp.constants import EduErpModuleConstants as C
from app.modules.eduerp.endpoint_spec import PAGING as _PAGING
from app.modules.eduerp.endpoint_spec import EndpointSpec
from app.modules.eduerp.registry_admin import (
    ACADEMIC_ADMIN_ENDPOINTS,
    FINANCE_ADMIN_ENDPOINTS,
    PAYROLL_ADMIN_ENDPOINTS,
    SYSTEM_ADMIN_ENDPOINTS,
)
from app.modules.eduerp.registry_portals import (
    SELF_SERVICE_ENDPOINTS,
    STUDENT_ENDPOINTS,
    TEACHER_ENDPOINTS,
)

_CORE_ENDPOINTS: tuple[EndpointSpec, ...] = (
    # Dashboard - DashboardController (READ_DASHBOARD = DASHBOARD:READ@BRANCH)
    EndpointSpec(
        endpoint_id="dashboard.stats",
        domain="dashboard",
        method="GET",
        path_template="/api/dashboard/stats",
        resource=C.Resources.DASHBOARD,
        action=C.Actions.READ,
        min_scope=C.Scopes.BRANCH,
        summary="Thống kê tổng quan: số tài khoản theo vai trò và các lần đăng nhập gần đây.",
        query_params=("branchId",),
    ),
    # Organization - BranchAdminController (BRANCH:*@ORGANIZATION)
    EndpointSpec(
        endpoint_id="branches.list",
        domain="organization",
        method="GET",
        path_template="/api/organization/branches",
        resource=C.Resources.BRANCH,
        action=C.Actions.READ,
        min_scope=C.Scopes.ORGANIZATION,
        summary="Danh sách chi nhánh (phân trang).",
        query_params=_PAGING,
    ),
    EndpointSpec(
        endpoint_id="branches.create",
        domain="organization",
        method="POST",
        path_template="/api/organization/branches",
        resource=C.Resources.BRANCH,
        action=C.Actions.CREATE,
        min_scope=C.Scopes.ORGANIZATION,
        summary="Tạo chi nhánh mới (code, name bắt buộc).",
        body_fields=("code", "name", "address"),
    ),
    # Courses - CourseAdminController (READ_COURSE@BRANCH, CREATE_COURSE@ORGANIZATION)
    EndpointSpec(
        endpoint_id="courses.list",
        domain="academic",
        method="GET",
        path_template="/api/courses/courses",
        resource=C.Resources.COURSE,
        action=C.Actions.READ,
        min_scope=C.Scopes.BRANCH,
        summary="Danh sách khóa học (mã, tên, học phí, số buổi chuẩn, cột điểm).",
        query_params=_PAGING,
    ),
    EndpointSpec(
        endpoint_id="courses.create",
        domain="academic",
        method="POST",
        path_template="/api/courses/courses",
        resource=C.Resources.COURSE,
        action=C.Actions.CREATE,
        min_scope=C.Scopes.ORGANIZATION,
        summary=(
            "Tạo khóa học mới. gradeColumns là danh sách {name, weightPercent, maxScore, "
            "displayOrder}, tổng weightPercent phải bằng 100."
        ),
        body_fields=("code", "name", "description", "standardSessionCount", "tuitionFee", "gradeColumns"),
    ),
    # Classes - ClassAdminController (CLASS:*@BRANCH)
    EndpointSpec(
        endpoint_id="classes.list",
        domain="academic",
        method="GET",
        path_template="/api/courses/classes",
        resource=C.Resources.CLASS,
        action=C.Actions.READ,
        min_scope=C.Scopes.BRANCH,
        summary="Danh sách lớp học, lọc theo giáo viên, chi nhánh; openOnly=true lấy lớp đang tuyển sinh và đang học (chỉ lớp đang tuyển sinh mới nhận ghi danh).",
        query_params=(*_PAGING, "teacherId", "branchId", "openOnly"),
    ),
    EndpointSpec(
        endpoint_id="classes.create",
        domain="academic",
        method="POST",
        path_template="/api/courses/classes",
        resource=C.Resources.CLASS,
        action=C.Actions.CREATE,
        min_scope=C.Scopes.BRANCH,
        summary=(
            "Mở lớp học mới. schedule là danh sách {dayOfWeek, startTime, endTime} "
            "(dayOfWeek: MON, TUE, WED, THU, FRI, SAT, SUN; giờ dạng HH:mm)."
        ),
        body_fields=(
            "courseId",
            "code",
            "branchId",
            "teacherId",
            "maxSeats",
            "minSeats",
            "startDate",
            "endDate",
            "schedule",
        ),
    ),
    # Teachers - TeacherAdminController (READ_TEACHER@BRANCH, CREATE_TEACHER@ORGANIZATION)
    EndpointSpec(
        endpoint_id="teachers.list",
        domain="teacher",
        method="GET",
        path_template="/api/teachers/profiles",
        resource=C.Resources.TEACHER,
        action=C.Actions.READ,
        min_scope=C.Scopes.BRANCH,
        summary="Danh sách hồ sơ giáo viên và môn dạy.",
        query_params=_PAGING,
    ),
    EndpointSpec(
        endpoint_id="teachers.create",
        domain="teacher",
        method="POST",
        path_template="/api/teachers/profiles",
        resource=C.Resources.TEACHER,
        action=C.Actions.CREATE,
        min_scope=C.Scopes.ORGANIZATION,
        summary="Tạo giáo viên mới kèm tài khoản; hệ thống gửi email mời tới địa chỉ email.",
        body_fields=("email", "fullName", "homeBranchId", "subjects", "phone", "bio"),
    ),
    # Students - StudentAdminController (STUDENT:*@BRANCH)
    EndpointSpec(
        endpoint_id="students.list",
        domain="admission",
        method="GET",
        path_template="/api/students/profiles",
        resource=C.Resources.STUDENT,
        action=C.Actions.READ,
        min_scope=C.Scopes.BRANCH,
        summary="Danh sách hồ sơ học viên trong phạm vi chi nhánh được phép.",
        query_params=_PAGING,
    ),
    EndpointSpec(
        endpoint_id="students.create",
        domain="admission",
        method="POST",
        path_template="/api/students/profiles",
        resource=C.Resources.STUDENT,
        action=C.Actions.CREATE,
        min_scope=C.Scopes.BRANCH,
        summary="Tạo học viên mới kèm tài khoản; hệ thống gửi email mời tới địa chỉ email.",
        body_fields=("email", "fullName", "homeBranchId", "dateOfBirth", "phone", "sourceChannel"),
    ),
    # Enrollment - EnrollmentAdminController (ENROLLMENT:*@BRANCH)
    EndpointSpec(
        endpoint_id="enrollments.list",
        domain="admission",
        method="GET",
        path_template="/api/enrollment/enrollments",
        resource=C.Resources.ENROLLMENT,
        action=C.Actions.READ,
        min_scope=C.Scopes.BRANCH,
        summary="Danh sách ghi danh; status: ACTIVE, WITHDRAWN, COMPLETED.",
        query_params=(*_PAGING, "studentProfileId", "classId", "status"),
    ),
    EndpointSpec(
        endpoint_id="enrollments.create",
        domain="admission",
        method="POST",
        path_template="/api/enrollment/enrollments",
        resource=C.Resources.ENROLLMENT,
        action=C.Actions.CREATE,
        min_scope=C.Scopes.BRANCH,
        summary=(
            "Ghi danh một học viên vào một lớp (chặn ghi danh trùng, lớp đủ sĩ số và trùng lịch). Nếu trả về "
            "ENROLLMENT_SCHEDULE_CONFLICT, hãy đọc nguyên văn thông báo; chỉ gửi lại với "
            "acceptScheduleConflict=true khi quản trị viên đã xác nhận rõ. Nếu trả về "
            "ENROLLMENT_SCHEDULE_CONFLICT_CONTACT_ADMIN, hãy hướng dẫn liên hệ quản trị viên."
        ),
        body_fields=("studentProfileId", "classId", "acceptScheduleConflict"),
    ),
    # Billing - InvoiceAdminController / ComboAdminController (INVOICE:*@BRANCH)
    EndpointSpec(
        endpoint_id="invoices.list",
        domain="finance",
        method="GET",
        path_template="/api/billing/invoices",
        resource=C.Resources.INVOICE,
        action=C.Actions.READ,
        min_scope=C.Scopes.BRANCH,
        summary="Danh sách hóa đơn học phí; status: UNPAID, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED.",
        query_params=(*_PAGING, "studentProfileId", "enrollmentId", "status"),
    ),
    EndpointSpec(
        endpoint_id="invoices.create",
        domain="finance",
        method="POST",
        path_template="/api/billing/invoices",
        resource=C.Resources.INVOICE,
        action=C.Actions.CREATE,
        min_scope=C.Scopes.BRANCH,
        summary="Phát hành một đợt học phí cho một ghi danh (tối đa 3 đợt, tổng không vượt học phí).",
        body_fields=("enrollmentId", "amount", "dueDate"),
    ),
    EndpointSpec(
        endpoint_id="invoices.manual_payment",
        domain="finance",
        method="POST",
        path_template="/api/billing/invoices/{invoiceId}/manual-payment",
        resource=C.Resources.INVOICE,
        action=C.Actions.UPDATE,
        min_scope=C.Scopes.BRANCH,
        summary="Ghi nhận khoản thu tại trung tâm cho hóa đơn; method: CASH hoặc BANK_TRANSFER.",
        path_params=("invoiceId",),
        body_fields=("amount", "method", "note"),
    ),
    EndpointSpec(
        endpoint_id="combos.list",
        domain="finance",
        method="GET",
        path_template="/api/billing/combos",
        resource=C.Resources.INVOICE,
        action=C.Actions.READ,
        min_scope=C.Scopes.BRANCH,
        summary="Danh sách combo nhiều khóa học và mức giảm giá đã áp dụng.",
        query_params=(*_PAGING, "studentProfileId"),
    ),
    # Academic - GradeAdminController (GRADE:* without minimum scope; class guard in use case)
    EndpointSpec(
        endpoint_id="grades.class_gradebook",
        domain="learning",
        method="GET",
        path_template="/api/academic/classes/{classId}/gradebook",
        resource=C.Resources.GRADE,
        action=C.Actions.READ,
        min_scope=C.Scopes.PERSONAL,
        summary="Sổ điểm của một lớp (giáo viên chỉ xem được lớp mình được phân công).",
        path_params=("classId",),
    ),
    EndpointSpec(
        endpoint_id="grades.upsert",
        domain="learning",
        method="POST",
        path_template="/api/academic/grades",
        resource=C.Resources.GRADE,
        action=C.Actions.UPDATE,
        min_scope=C.Scopes.PERSONAL,
        summary="Nhập hoặc sửa điểm một cột điểm cho một học viên trong lớp.",
        body_fields=("classId", "enrollmentId", "courseGradeColumnId", "score"),
    ),
    # Payroll - ContractAdminController / PayrollRunAdminController (PAYROLL:*@ORGANIZATION)
    EndpointSpec(
        endpoint_id="contracts.list",
        domain="payroll",
        method="GET",
        path_template="/api/payroll/contracts",
        resource=C.Resources.PAYROLL,
        action=C.Actions.READ,
        min_scope=C.Scopes.ORGANIZATION,
        summary="Danh sách hợp đồng lao động, lọc theo tài khoản nhân sự.",
        query_params=(*_PAGING, "accountId"),
    ),
    EndpointSpec(
        endpoint_id="payroll_runs.list",
        domain="payroll",
        method="GET",
        path_template="/api/payroll/runs",
        resource=C.Resources.PAYROLL,
        action=C.Actions.READ,
        min_scope=C.Scopes.ORGANIZATION,
        summary="Danh sách kỳ lương theo tháng.",
        query_params=_PAGING,
    ),
    # PayrollSelfServiceController has no @PreAuthorize: any signed-in account sees only its own
    # payslips. ACCOUNT:READ@PERSONAL is the baseline every system role holds.
    EndpointSpec(
        endpoint_id="payroll.my_payslips",
        domain="payroll",
        method="GET",
        path_template="/api/payroll/me/payslips",
        resource=C.Resources.ACCOUNT,
        action=C.Actions.READ,
        min_scope=C.Scopes.PERSONAL,
        summary="Phiếu lương của chính tài khoản đang kết nối.",
        query_params=_PAGING,
    ),
    # Identity - AccountAdminController (READ_ACCOUNT = ACCOUNT:READ@BRANCH)
    EndpointSpec(
        endpoint_id="accounts.list",
        domain="identity",
        method="GET",
        path_template="/api/rbac/accounts",
        resource=C.Resources.ACCOUNT,
        action=C.Actions.READ,
        min_scope=C.Scopes.BRANCH,
        summary="Danh sách tài khoản người dùng, lọc theo chi nhánh.",
        query_params=(*_PAGING, "branchId"),
    ),
    # Access - RbacAdminController (READ_ROLE = ROLE:READ@ORGANIZATION). The catalog already
    # returns roles, permission groups, branches and permissions; there is no GET /api/rbac/roles.
    EndpointSpec(
        endpoint_id="rbac.catalog",
        domain="access",
        method="GET",
        path_template="/api/rbac/catalog",
        resource=C.Resources.ROLE,
        action=C.Actions.READ,
        min_scope=C.Scopes.ORGANIZATION,
        summary="Danh mục phân quyền: vai trò, nhóm quyền, chi nhánh và toàn bộ quyền (resource + action).",
    ),
)

EDUERP_ENDPOINTS: tuple[EndpointSpec, ...] = (
    *_CORE_ENDPOINTS,
    *SELF_SERVICE_ENDPOINTS,
    *STUDENT_ENDPOINTS,
    *TEACHER_ENDPOINTS,
    *ACADEMIC_ADMIN_ENDPOINTS,
    *FINANCE_ADMIN_ENDPOINTS,
    *PAYROLL_ADMIN_ENDPOINTS,
    *SYSTEM_ADMIN_ENDPOINTS,
)

ENDPOINTS_BY_ID: dict[str, EndpointSpec] = {spec.endpoint_id: spec for spec in EDUERP_ENDPOINTS}
