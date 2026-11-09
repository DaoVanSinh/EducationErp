"""Endpoints of the student and teacher portals, plus what every signed-in account can do for itself.

Controllers without @PreAuthorize (profile, own payslips, public landing data) use
ACCOUNT:READ/UPDATE@PERSONAL, the baseline every system role holds.
"""

from __future__ import annotations

from app.modules.eduerp.constants import EduErpModuleConstants as C
from app.modules.eduerp.endpoint_spec import PAGING, EndpointSpec, endpoint

_PERSONAL = C.Scopes.PERSONAL

_ACCOUNT_READ = (C.Resources.ACCOUNT, C.Actions.READ, _PERSONAL)
_LEARNING_READ = (C.Resources.LEARNING, C.Actions.READ, _PERSONAL)
_GRADE_READ = (C.Resources.GRADE, C.Actions.READ, _PERSONAL)
_ATTENDANCE_READ = (C.Resources.ATTENDANCE, C.Actions.READ, _PERSONAL)
_ASSIGNMENT_READ = (C.Resources.ASSIGNMENT, C.Actions.READ, _PERSONAL)
_ASSIGNMENT_CREATE = (C.Resources.ASSIGNMENT, C.Actions.CREATE, _PERSONAL)
_ASSIGNMENT_UPDATE = (C.Resources.ASSIGNMENT, C.Actions.UPDATE, _PERSONAL)

SELF_SERVICE_ENDPOINTS: tuple[EndpointSpec, ...] = (
    endpoint(
        "me.account", "self", "GET", "/api/account/me", _ACCOUNT_READ,
        "Thông tin tài khoản đang kết nối: họ tên, email, vai trò, chi nhánh và các quyền.",
    ),
    endpoint(
        "me.update_profile", "self", "PATCH", "/api/account/profile",
        (C.Resources.ACCOUNT, C.Actions.UPDATE, _PERSONAL),
        "Cập nhật họ tên hoặc ảnh đại diện (URL) của chính mình.",
        body_fields=("fullName", "avatarUrl"),
    ),
    endpoint(
        "landing.overview", "self", "GET", "/api/landing/overview", _ACCOUNT_READ,
        "Thông tin công khai của trung tâm: khóa học, lớp đang mở, chi nhánh, liên hệ.",
    ),
    endpoint(
        "payments.status", "self", "GET", "/api/billing/payments/{gatewayTransactionId}/status", _ACCOUNT_READ,
        "Trạng thái một giao dịch thanh toán trực tuyến theo mã giao dịch của cổng.",
    ),
)

STUDENT_ENDPOINTS: tuple[EndpointSpec, ...] = (
    endpoint(
        "learning.my_classes", "learning", "GET", "/api/academic/me/classes", _LEARNING_READ,
        (
            "Lớp của học viên đang kết nối: lịch học, giáo viên, điểm, tiến trình. Trường scheduleConflict "
            "khác null nghĩa là lớp bị trùng lịch: hãy báo học viên liên hệ quản trị viên để được tư vấn đổi lịch."
        ),
    ),
    endpoint(
        "learning.my_attendance", "learning", "GET", "/api/attendance/me/classes", _LEARNING_READ,
        "Điểm danh của học viên đang kết nối theo từng lớp.",
    ),
    endpoint(
        "learning.my_assessments", "learning", "GET", "/api/assessment/me/classes", _LEARNING_READ,
        "Bài tập, mini-test và mini-task của học viên đang kết nối theo từng lớp.",
    ),
    endpoint(
        "learning.mini_task", "learning", "GET", "/api/assessment/me/mini-tasks/{miniTaskId}", _LEARNING_READ,
        "Đề của một mini-task để học viên luyện tập.",
    ),
    endpoint(
        "learning.submit_mini_task", "learning", "POST", "/api/assessment/me/mini-tasks/{miniTaskId}/attempts",
        (C.Resources.PRACTICE, C.Actions.CREATE, _PERSONAL),
        "Nộp bài mini-task. answers là danh sách chỉ số đáp án đã chọn, theo thứ tự câu hỏi.",
        body_fields=("answers",),
    ),
    endpoint(
        "learning.my_invoices", "learning", "GET", "/api/billing/me/invoices", _LEARNING_READ,
        "Học phí và lịch sử thanh toán của học viên đang kết nối.",
    ),
    endpoint(
        "learning.pay_invoice", "learning", "POST", "/api/billing/me/invoices/{invoiceId}/online-payment",
        _LEARNING_READ,
        "Tạo liên kết thanh toán trực tuyến cho hóa đơn của chính mình; gateway: VNPAY.",
        body_fields=("gateway",),
    ),
)

TEACHER_ENDPOINTS: tuple[EndpointSpec, ...] = (
    endpoint(
        "teaching.my_taught_classes", "teaching", "GET", "/api/attendance/me/taught-classes",
        (C.Resources.TEACHING, C.Actions.READ, _PERSONAL),
        "Lớp đã dạy của chính giáo viên, kể cả lớp đã bàn giao.",
    ),
    endpoint(
        "teaching.my_classes", "teaching", "GET", "/api/courses/me/classes",
        (C.Resources.TEACHING, C.Actions.READ, _PERSONAL),
        "Các lớp giáo viên đang kết nối được phân công dạy, kèm lịch dạy.",
        query_params=PAGING,
    ),
    endpoint(
        "grades.class_progress", "learning", "GET", "/api/academic/classes/{classId}/progress", _GRADE_READ,
        "Tiến trình học tập của một lớp (giáo viên chỉ xem được lớp mình dạy).",
    ),
    endpoint(
        "grades.student_summary", "learning", "GET", "/api/academic/students/{studentProfileId}/summary", _GRADE_READ,
        "Tổng hợp kết quả học tập của một học viên trong các lớp người gọi được xem.",
    ),
    endpoint(
        "attendance.class", "attendance", "GET", "/api/attendance/classes/{classId}", _ATTENDANCE_READ,
        "Bảng điểm danh của một lớp (giáo viên chỉ xem được lớp mình dạy).",
    ),
    endpoint(
        "attendance.record", "attendance", "POST", "/api/attendance/sessions",
        (C.Resources.ATTENDANCE, C.Actions.UPDATE, _PERSONAL),
        (
            "Lưu điểm danh một buổi. sessionDate dạng YYYY-MM-DD; entries là danh sách {enrollmentId, status, note}, "
            "status: PRESENT, LATE, ABSENT, EXCUSED."
        ),
        body_fields=("classId", "sessionDate", "note", "entries"),
    ),
    endpoint(
        "assignments.list", "assessment", "GET", "/api/assessment/classes/{classId}/assignments", _ASSIGNMENT_READ,
        "Bài tập và mini-test của một lớp.",
    ),
    endpoint(
        "assignments.create", "assessment", "POST", "/api/assessment/assignments", _ASSIGNMENT_CREATE,
        "Tạo bài tập cho một lớp; type: HOMEWORK hoặc MINI_TEST.",
        body_fields=("classId", "title", "description", "type", "dueDate", "maxScore"),
    ),
    endpoint(
        "assignments.update", "assessment", "PATCH", "/api/assessment/assignments/{assignmentId}", _ASSIGNMENT_UPDATE,
        "Sửa hoặc ẩn một bài tập (active=false).",
        body_fields=("title", "description", "type", "dueDate", "maxScore", "active"),
    ),
    endpoint(
        "assignments.record_results", "assessment", "POST", "/api/assessment/assignments/{assignmentId}/results",
        _ASSIGNMENT_UPDATE,
        (
            "Nhập kết quả bài tập; entries là danh sách {enrollmentId, status, score, feedback}, "
            "status: SUBMITTED, LATE, NOT_SUBMITTED."
        ),
        body_fields=("entries",),
    ),
    endpoint(
        "mini_tasks.list", "assessment", "GET", "/api/assessment/classes/{classId}/mini-tasks", _ASSIGNMENT_READ,
        "Mini-task của một lớp.",
    ),
    endpoint(
        "mini_tasks.create", "assessment", "POST", "/api/assessment/mini-tasks", _ASSIGNMENT_CREATE,
        "Tạo mini-task; questions là danh sách {content, options, correctOptionIndex, explanation}.",
        body_fields=("classId", "title", "description", "passPercent", "questions"),
    ),
    endpoint(
        "mini_tasks.update", "assessment", "PATCH", "/api/assessment/mini-tasks/{miniTaskId}", _ASSIGNMENT_UPDATE,
        "Sửa hoặc ẩn một mini-task (active=false).",
        body_fields=("title", "description", "passPercent", "active", "questions"),
    ),
)
