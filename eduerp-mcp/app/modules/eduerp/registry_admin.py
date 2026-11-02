"""Administration endpoints beyond the core lists in `registry.py`.

Each permission mirrors the controller's `@PreAuthorize(AccessConstants.AccessRules.X)`. The backend
re-checks every call, including branch scope, so an entry here never grants extra access.
"""

from __future__ import annotations

from app.modules.eduerp.constants import EduErpModuleConstants as C
from app.modules.eduerp.endpoint_spec import PAGING, EndpointSpec, endpoint

_ORG = C.Scopes.ORGANIZATION
_BRANCH = C.Scopes.BRANCH

_ACCOUNT_UPDATE = (C.Resources.ACCOUNT, C.Actions.UPDATE, _ORG)
_BRANCH_READ = (C.Resources.BRANCH, C.Actions.READ, _ORG)
_BRANCH_UPDATE = (C.Resources.BRANCH, C.Actions.UPDATE, _ORG)
_CLASS_UPDATE = (C.Resources.CLASS, C.Actions.UPDATE, _BRANCH)
_ENROLLMENT_UPDATE = (C.Resources.ENROLLMENT, C.Actions.UPDATE, _BRANCH)
_INVOICE_READ = (C.Resources.INVOICE, C.Actions.READ, _BRANCH)
_INVOICE_CREATE = (C.Resources.INVOICE, C.Actions.CREATE, _BRANCH)
_INVOICE_UPDATE = (C.Resources.INVOICE, C.Actions.UPDATE, _BRANCH)
_PAYROLL_READ = (C.Resources.PAYROLL, C.Actions.READ, _ORG)
_PAYROLL_CREATE = (C.Resources.PAYROLL, C.Actions.CREATE, _ORG)
_PAYROLL_UPDATE = (C.Resources.PAYROLL, C.Actions.UPDATE, _ORG)
_PAYROLL_APPROVE = (C.Resources.PAYROLL, C.Actions.APPROVE, _ORG)

ACADEMIC_ADMIN_ENDPOINTS: tuple[EndpointSpec, ...] = (
    endpoint(
        "branches.update", "organization", "PATCH", "/api/organization/branches/{branchId}", _BRANCH_UPDATE,
        "Sửa tên, địa chỉ hoặc trạng thái hoạt động của chi nhánh.",
        body_fields=("name", "address", "active"),
    ),
    endpoint(
        "courses.update", "academic", "PATCH", "/api/courses/courses/{courseId}",
        (C.Resources.COURSE, C.Actions.UPDATE, _ORG),
        "Sửa khóa học; tổng weightPercent của gradeColumns phải bằng 100.",
        body_fields=("name", "description", "standardSessionCount", "tuitionFee", "active", "gradeColumns"),
    ),
    endpoint(
        "classes.update", "academic", "PATCH", "/api/courses/classes/{classId}", _CLASS_UPDATE,
        "Sửa lớp còn mở: giáo viên, sĩ số, ngày và lịch học {dayOfWeek, startTime, endTime}.",
        body_fields=("teacherId", "maxSeats", "minSeats", "startDate", "endDate", "schedule"),
    ),
    endpoint(
        "classes.start", "academic", "POST", "/api/courses/classes/{classId}/start", _CLASS_UPDATE,
        "Khai giảng lớp đủ điều kiện.",
    ),
    endpoint(
        "classes.complete", "academic", "POST", "/api/courses/classes/{classId}/complete", _CLASS_UPDATE,
        "Kết thúc lớp; ghi danh đang học chuyển sang hoàn tất.",
    ),
    endpoint(
        "classes.cancel", "academic", "POST", "/api/courses/classes/{classId}/cancel", _CLASS_UPDATE,
        "Hủy lớp chưa khai giảng; ghi danh đang học chuyển sang đã rút.",
    ),
    endpoint(
        "teachers.get", "teacher", "GET", "/api/teachers/profiles/{profileId}",
        (C.Resources.TEACHER, C.Actions.READ, _BRANCH),
        "Chi tiết một hồ sơ giáo viên.",
    ),
    endpoint(
        "teachers.update", "teacher", "PATCH", "/api/teachers/profiles/{profileId}",
        (C.Resources.TEACHER, C.Actions.UPDATE, _ORG),
        "Sửa hồ sơ giáo viên.",
        body_fields=("subjects", "phone", "bio", "active"),
    ),
    endpoint(
        "students.get", "admission", "GET", "/api/students/profiles/{profileId}",
        (C.Resources.STUDENT, C.Actions.READ, _BRANCH),
        "Chi tiết một hồ sơ học viên.",
    ),
    endpoint(
        "students.update", "admission", "PATCH", "/api/students/profiles/{profileId}",
        (C.Resources.STUDENT, C.Actions.UPDATE, _BRANCH),
        "Sửa hồ sơ học viên.",
        body_fields=("dateOfBirth", "phone", "sourceChannel", "active"),
    ),
    endpoint(
        "enrollments.get", "admission", "GET", "/api/enrollment/enrollments/{enrollmentId}",
        (C.Resources.ENROLLMENT, C.Actions.READ, _BRANCH),
        "Chi tiết một ghi danh.",
    ),
    endpoint(
        "enrollments.withdraw", "admission", "POST", "/api/enrollment/enrollments/{enrollmentId}/withdraw",
        _ENROLLMENT_UPDATE,
        "Rút học viên khỏi lớp; không xóa công nợ đã phát hành.",
    ),
    endpoint(
        "enrollments.complete", "admission", "POST", "/api/enrollment/enrollments/{enrollmentId}/complete",
        _ENROLLMENT_UPDATE,
        "Đánh dấu hoàn tất một ghi danh.",
    ),
)

FINANCE_ADMIN_ENDPOINTS: tuple[EndpointSpec, ...] = (
    endpoint(
        "invoices.get", "finance", "GET", "/api/billing/invoices/{invoiceId}", _INVOICE_READ,
        "Chi tiết một hóa đơn và các khoản đã thu.",
    ),
    endpoint(
        "invoices.online_payment", "finance", "POST", "/api/billing/invoices/{invoiceId}/online-payment",
        _INVOICE_UPDATE,
        "Tạo liên kết thanh toán trực tuyến cho một hóa đơn; gateway: VNPAY.",
        body_fields=("gateway",),
    ),
    endpoint(
        "invoices.cancel", "finance", "POST", "/api/billing/invoices/{invoiceId}/cancel", _INVOICE_UPDATE,
        "Hủy một hóa đơn chưa thu.",
    ),
    endpoint(
        "combos.create", "finance", "POST", "/api/billing/combos", _INVOICE_CREATE,
        "Tạo combo nhiều khóa học cho một học viên từ các ghi danh đã có.",
        body_fields=("studentProfileId", "enrollmentIds", "dueDate"),
    ),
    endpoint(
        "combos.create_invoice", "finance", "POST", "/api/billing/combos/invoices", _INVOICE_CREATE,
        "Phát hành một đợt học phí cho combo.",
        body_fields=("comboId", "amount", "dueDate"),
    ),
    endpoint(
        "combos.get", "finance", "GET", "/api/billing/combos/{comboId}", _INVOICE_READ,
        "Chi tiết một combo.",
    ),
    endpoint(
        "combos.cancel", "finance", "POST", "/api/billing/combos/{comboId}/cancel", _INVOICE_UPDATE,
        "Hủy một combo.",
    ),
    endpoint(
        "combo_tiers.list", "finance", "GET", "/api/billing/combo-discount-tiers", _INVOICE_READ,
        "Các bậc giảm giá combo và đăng ký nhóm.",
    ),
    endpoint(
        "combo_tiers.create", "finance", "POST", "/api/billing/combo-discount-tiers",
        (C.Resources.INVOICE, C.Actions.CREATE, _ORG),
        "Thêm một bậc giảm giá.",
        body_fields=("minCourseCount", "discountPercent"),
    ),
    endpoint(
        "combo_tiers.update", "finance", "PATCH", "/api/billing/combo-discount-tiers/{tierId}",
        (C.Resources.INVOICE, C.Actions.UPDATE, _ORG),
        "Sửa một bậc giảm giá.",
        body_fields=("discountPercent", "active"),
    ),
    endpoint(
        "group_registrations.list", "finance", "GET", "/api/billing/group-registrations", _INVOICE_READ,
        "Danh sách đăng ký nhóm, lọc theo khóa học.",
        query_params=(*PAGING, "courseId"),
    ),
    endpoint(
        "group_registrations.create", "finance", "POST", "/api/billing/group-registrations", _INVOICE_CREATE,
        "Tạo đăng ký nhóm từ các ghi danh đã có.",
        body_fields=("enrollmentIds",),
    ),
    endpoint(
        "group_registrations.cancel", "finance", "POST", "/api/billing/group-registrations/{groupId}/cancel",
        _INVOICE_UPDATE,
        "Hủy một đăng ký nhóm.",
    ),
)

PAYROLL_ADMIN_ENDPOINTS: tuple[EndpointSpec, ...] = (
    endpoint(
        "contracts.create", "payroll", "POST", "/api/payroll/contracts", _PAYROLL_CREATE,
        (
            "Tạo hợp đồng lao động; contractType: OFFICIAL hoặc COLLABORATOR "
            "(không kèm file scan; tải file scan ở giao diện web)."
        ),
        body_fields=(
            "accountId", "contractType", "baseSalary", "hourlyRate", "probationStartDate",
            "probationEndDate", "startDate", "allowances",
        ),
        multipart_part="request",
    ),
    endpoint(
        "contracts.update", "payroll", "PATCH", "/api/payroll/contracts/{contractId}", _PAYROLL_UPDATE,
        "Sửa hợp đồng lao động (không đổi file scan).",
        body_fields=("baseSalary", "hourlyRate", "probationEndDate", "allowances"),
        multipart_part="request",
    ),
    endpoint(
        "contracts.terminate", "payroll", "POST", "/api/payroll/contracts/{contractId}/terminate", _PAYROLL_UPDATE,
        "Chấm dứt một hợp đồng lao động.",
    ),
    endpoint(
        "payroll_runs.create", "payroll", "POST", "/api/payroll/runs", _PAYROLL_CREATE,
        "Tạo kỳ lương theo tháng.",
        body_fields=("year", "month"),
    ),
    endpoint(
        "payroll_runs.get", "payroll", "GET", "/api/payroll/runs/{runId}", _PAYROLL_READ,
        "Chi tiết một kỳ lương và các phiếu lương.",
    ),
    endpoint(
        "payroll_runs.update_payslip", "payroll", "PATCH", "/api/payroll/runs/{runId}/payslips/{payslipId}",
        _PAYROLL_UPDATE,
        "Sửa giờ công, thuế khấu trừ hoặc ghi chú của một phiếu lương.",
        body_fields=("hoursWorked", "incomeTaxWithheld", "note"),
    ),
    endpoint(
        "payroll_runs.submit", "payroll", "POST", "/api/payroll/runs/{runId}/submit", _PAYROLL_UPDATE,
        "Gửi kỳ lương đi duyệt.",
    ),
    endpoint(
        "payroll_runs.approve", "payroll", "POST", "/api/payroll/runs/{runId}/approve", _PAYROLL_APPROVE,
        "Duyệt kỳ lương.",
    ),
    endpoint(
        "payroll_runs.reject", "payroll", "POST", "/api/payroll/runs/{runId}/reject", _PAYROLL_APPROVE,
        "Từ chối kỳ lương, kèm lý do.",
        body_fields=("reason",),
    ),
)

SYSTEM_ADMIN_ENDPOINTS: tuple[EndpointSpec, ...] = (
    endpoint(
        "accounts.create", "identity", "POST", "/api/rbac/accounts",
        (C.Resources.ACCOUNT, C.Actions.CREATE, _ORG),
        "Tạo tài khoản; hệ thống gửi email mời.",
        body_fields=("email", "fullName", "homeBranchId", "roleId"),
    ),
    endpoint(
        "accounts.disable", "identity", "POST", "/api/rbac/accounts/{accountId}/disable", _ACCOUNT_UPDATE,
        "Khóa một tài khoản.",
    ),
    endpoint(
        "accounts.enable", "identity", "POST", "/api/rbac/accounts/{accountId}/enable", _ACCOUNT_UPDATE,
        "Mở khóa một tài khoản.",
    ),
    endpoint(
        "accounts.sign_out_everywhere", "identity", "POST", "/api/rbac/accounts/{accountId}/sign-out-everywhere",
        _ACCOUNT_UPDATE,
        "Đăng xuất một tài khoản khỏi mọi thiết bị.",
    ),
    endpoint(
        "accounts.resend_invite", "identity", "POST", "/api/rbac/accounts/{accountId}/resend-invite", _ACCOUNT_UPDATE,
        "Gửi lại email mời.",
    ),
    endpoint(
        "accounts.revoke_invite", "identity", "POST", "/api/rbac/accounts/{accountId}/revoke-invite", _ACCOUNT_UPDATE,
        "Thu hồi lời mời chưa kích hoạt.",
    ),
    endpoint(
        "accounts.transfer_branch", "identity", "POST", "/api/rbac/accounts/{accountId}/transfer-branch",
        _ACCOUNT_UPDATE,
        "Chuyển tài khoản sang chi nhánh khác.",
        body_fields=("branchId",),
    ),
    endpoint(
        "accounts.change_role", "access", "PATCH", "/api/rbac/accounts/{accountId}/role", _ACCOUNT_UPDATE,
        "Đổi vai trò của một tài khoản.",
        body_fields=("roleId",),
    ),
    endpoint(
        "rbac.create_permission_group", "access", "POST", "/api/rbac/permission-groups",
        (C.Resources.PERMISSION_GROUP, C.Actions.CREATE, _ORG),
        "Tạo nhóm quyền; items là danh sách quyền kèm phạm vi.",
        body_fields=("name", "description", "items"),
    ),
    endpoint(
        "rbac.update_permission_group", "access", "PUT", "/api/rbac/permission-groups/{permissionGroupId}",
        (C.Resources.PERMISSION_GROUP, C.Actions.UPDATE, _ORG),
        "Sửa nhóm quyền.",
        body_fields=("name", "description", "items"),
    ),
    endpoint(
        "rbac.create_role", "access", "POST", "/api/rbac/roles",
        (C.Resources.ROLE, C.Actions.CREATE, _ORG),
        "Tạo vai trò từ các nhóm quyền.",
        body_fields=("code", "name", "permissionGroupIds"),
    ),
    endpoint(
        "rbac.update_role", "access", "PUT", "/api/rbac/roles/{roleId}",
        (C.Resources.ROLE, C.Actions.UPDATE, _ORG),
        "Sửa vai trò.",
        body_fields=("name", "permissionGroupIds"),
    ),
    endpoint(
        "center_config.get", "organization", "GET", "/api/landing/admin/config", _BRANCH_READ,
        "Cấu hình thông tin trung tâm và trang giới thiệu.",
    ),
    endpoint(
        "center_config.update", "organization", "PATCH", "/api/landing/admin/config", _BRANCH_UPDATE,
        "Cập nhật cấu hình thông tin trung tâm và trang giới thiệu.",
        body_fields=("centerInfo", "roadmapSteps", "whyChooseFeatures", "faqs"),
    ),
    endpoint(
        "consultations.list", "admission", "GET", "/api/landing/admin/consultations", _BRANCH_READ,
        "Danh sách yêu cầu tư vấn từ trang giới thiệu.",
    ),
    endpoint(
        "consultations.update_status", "admission", "PATCH", "/api/landing/admin/consultations/{consultationId}",
        _BRANCH_UPDATE,
        "Đổi trạng thái một yêu cầu tư vấn; status: NEW, CONTACTED, ENROLLED, CLOSED.",
        body_fields=("status",),
    ),
)
