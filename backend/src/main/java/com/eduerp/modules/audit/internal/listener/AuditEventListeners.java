package com.eduerp.modules.audit.internal.listener;

import com.eduerp.modules.academic.AcademicEvents;
import com.eduerp.modules.access.AccessEvents;
import com.eduerp.modules.assessment.AssessmentEvents;
import com.eduerp.modules.attendance.AttendanceEvents;
import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.internal.model.AuditLog;
import com.eduerp.modules.audit.internal.repository.AuditLogRepository;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.organization.OrganizationEvents;
import com.eduerp.modules.payroll.PayrollEvents;
import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class AuditEventListeners {

    private final AuditLogRepository auditLogs;

    AuditEventListeners(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @ApplicationModuleListener
    void on(IdentityEvents.AccountSignedIn event) {
        auditLogs.save(new AuditLog(event.accountId(), AuditConstants.Actions.LOGIN,
                AuditConstants.EntityTypes.ACCOUNT, event.accountId().toString(), event.branchId()));
    }

    @ApplicationModuleListener
    void on(IdentityEvents.AccountBranchTransferred event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ACCOUNT_TRANSFER_BRANCH,
                AuditConstants.EntityTypes.ACCOUNT, event.accountId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(IdentityEvents.AccountCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ACCOUNT_CREATE,
                AuditConstants.EntityTypes.ACCOUNT, event.accountId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(IdentityEvents.AccountInviteResent event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ACCOUNT_INVITE_RESEND,
                AuditConstants.EntityTypes.ACCOUNT, event.accountId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(IdentityEvents.AccountInviteRevoked event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ACCOUNT_INVITE_REVOKE,
                AuditConstants.EntityTypes.ACCOUNT, event.accountId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(AccessEvents.RoleCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ROLE_CREATE,
                AuditConstants.EntityTypes.ROLE, event.roleId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(AccessEvents.PermissionGroupCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.PERMISSION_GROUP_CREATE,
                AuditConstants.EntityTypes.PERMISSION_GROUP, event.permissionGroupId().toString(),
                event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(AccessEvents.RoleUpdated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ROLE_UPDATE,
                AuditConstants.EntityTypes.ROLE, event.roleId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(AccessEvents.PermissionGroupUpdated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.PERMISSION_GROUP_UPDATE,
                AuditConstants.EntityTypes.PERMISSION_GROUP, event.permissionGroupId().toString(),
                event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(AccessEvents.AccountRoleChanged event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ACCOUNT_ROLE_CHANGE,
                AuditConstants.EntityTypes.ACCOUNT, event.accountId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(IdentityEvents.AccountDisabled event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ACCOUNT_DISABLE,
                AuditConstants.EntityTypes.ACCOUNT, event.accountId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(IdentityEvents.AccountEnabled event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ACCOUNT_ENABLE,
                AuditConstants.EntityTypes.ACCOUNT, event.accountId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(IdentityEvents.AccountSignedOutEverywhere event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ACCOUNT_SIGN_OUT_EVERYWHERE,
                AuditConstants.EntityTypes.ACCOUNT, event.accountId().toString(), event.actorBranchId()));
    }

    /** {@code actorAccountId} null = tác vụ tự khai giảng của hệ thống (mirror {@code InvoiceOverdue}). */
    @ApplicationModuleListener
    void on(CoursesEvents.ClassStarted event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.CLASS_START,
                AuditConstants.EntityTypes.CLASS, event.classId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(CoursesEvents.ClassCompleted event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.CLASS_COMPLETE,
                AuditConstants.EntityTypes.CLASS, event.classId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(CoursesEvents.ClassCancelled event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.CLASS_CANCEL,
                AuditConstants.EntityTypes.CLASS, event.classId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(OrganizationEvents.BranchCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.BRANCH_CREATE,
                AuditConstants.EntityTypes.BRANCH, event.branchId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(OrganizationEvents.BranchUpdated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.BRANCH_UPDATE,
                AuditConstants.EntityTypes.BRANCH, event.branchId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(CoursesEvents.CourseCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.COURSE_CREATE,
                AuditConstants.EntityTypes.COURSE, event.courseId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(CoursesEvents.CourseUpdated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.COURSE_UPDATE,
                AuditConstants.EntityTypes.COURSE, event.courseId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(CoursesEvents.ClassCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.CLASS_CREATE,
                AuditConstants.EntityTypes.CLASS, event.classId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(CoursesEvents.ClassUpdated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.CLASS_UPDATE,
                AuditConstants.EntityTypes.CLASS, event.classId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(PayrollEvents.ContractCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.CONTRACT_CREATE,
                AuditConstants.EntityTypes.CONTRACT, event.contractId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(PayrollEvents.ContractTerminated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.CONTRACT_TERMINATE,
                AuditConstants.EntityTypes.CONTRACT, event.contractId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(PayrollEvents.PayrollRunApproved event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.PAYROLL_RUN_APPROVE,
                AuditConstants.EntityTypes.PAYROLL_RUN, event.payrollRunId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(EnrollmentEvents.EnrollmentCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ENROLLMENT_CREATE,
                AuditConstants.EntityTypes.ENROLLMENT, event.enrollmentId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(EnrollmentEvents.EnrollmentWithdrawn event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ENROLLMENT_WITHDRAW,
                AuditConstants.EntityTypes.ENROLLMENT, event.enrollmentId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(EnrollmentEvents.EnrollmentCompleted event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ENROLLMENT_COMPLETE,
                AuditConstants.EntityTypes.ENROLLMENT, event.enrollmentId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(BillingEvents.InvoiceCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.INVOICE_CREATE,
                AuditConstants.EntityTypes.INVOICE, event.invoiceId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(BillingEvents.PaymentReceived event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.PAYMENT_RECEIVED,
                AuditConstants.EntityTypes.INVOICE, event.invoiceId().toString(), event.actorBranchId()));
    }

    /** {@code actorAccountId} là {@code null} (scheduler tự sinh, không ai bấm) - truyền thẳng, cột
     * {@code audit_logs.actor_account_id} nullable từ V7 nên dòng log vẫn ghi được. Đây là sự kiện hệ
     * thống đầu tiên trong dự án không có actor; không được thay bằng một UUID giả, vì khi đó nhật ký
     * sẽ nói rằng một người đã làm việc này. */
    @ApplicationModuleListener
    void on(BillingEvents.InvoiceOverdue event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.INVOICE_OVERDUE,
                AuditConstants.EntityTypes.INVOICE, event.invoiceId().toString(), event.actorBranchId()));
    }

    /** Final review Important #9: huỷ hoá đơn là voiding một chứng từ tài chính - phải có dấu vết. */
    @ApplicationModuleListener
    void on(BillingEvents.InvoiceCancelled event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.INVOICE_CANCEL,
                AuditConstants.EntityTypes.INVOICE, event.invoiceId().toString(), event.actorBranchId()));
    }

    /** Gộp nhiều khoá thành một gói giảm giá là một quyết định về tiền - mirror 1:1 InvoiceCreated. */
    @ApplicationModuleListener
    void on(BillingEvents.ComboCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.COMBO_CREATE,
                AuditConstants.EntityTypes.COMBO, event.comboId().toString(), event.actorBranchId()));
    }

    /** Huỷ combo là xoá cứng bản ghi, nên dòng log này là dấu vết DUY NHẤT còn lại cho thấy combo
     * từng tồn tại - khác InvoiceCancelled, nơi hoá đơn vẫn còn đó ở trạng thái CANCELLED. */
    @ApplicationModuleListener
    void on(BillingEvents.ComboCancelled event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.COMBO_CANCEL,
                AuditConstants.EntityTypes.COMBO, event.comboId().toString(), event.actorBranchId()));
    }

    /** Lập nhóm hạ trần học phí của nhiều ghi danh - mirror ComboCreated. */
    @ApplicationModuleListener
    void on(BillingEvents.GroupRegistrationCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.GROUP_REGISTRATION_CREATE,
                AuditConstants.EntityTypes.GROUP_REGISTRATION, event.groupId().toString(), event.actorBranchId()));
    }

    /** Huỷ nhóm là xoá cứng - dòng log này là dấu vết duy nhất, mirror ComboCancelled. */
    @ApplicationModuleListener
    void on(BillingEvents.GroupRegistrationCancelled event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.GROUP_REGISTRATION_CANCEL,
                AuditConstants.EntityTypes.GROUP_REGISTRATION, event.groupId().toString(), event.actorBranchId()));
    }

    /**
     * Spec mục 9. Điểm là một con số có hệ quả với học viên nên mọi lần nhập/sửa phải có dấu vết -
     * mirror 1:1 các listener của enrollment/billing. {@code entityId} là {@code enrollmentId}:
     * {@code audit_logs} chỉ có một cột {@code entity_id}, và câu hỏi người đọc nhật ký đặt ra là
     * "điểm của lượt ghi danh nào vừa bị sửa", không phải "dòng grade_entries nào".
     */
    @ApplicationModuleListener
    void on(AcademicEvents.GradeRecorded event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.GRADE_RECORD,
                AuditConstants.EntityTypes.GRADE_ENTRY, event.enrollmentId().toString(),
                event.actorBranchId()));
    }

    /** Điểm danh có hệ quả với học viên (tỷ lệ chuyên cần) nên mọi lần ghi/sửa một buổi đều có dấu vết. */
    @ApplicationModuleListener
    void on(AttendanceEvents.AttendanceRecorded event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ATTENDANCE_RECORD,
                AuditConstants.EntityTypes.ATTENDANCE_SESSION, event.sessionId().toString(),
                event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(AssessmentEvents.AssignmentCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ASSIGNMENT_CREATE,
                AuditConstants.EntityTypes.ASSIGNMENT, event.assignmentId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(AssessmentEvents.AssignmentResultsRecorded event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ASSIGNMENT_RESULTS_RECORD,
                AuditConstants.EntityTypes.ASSIGNMENT, event.assignmentId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(AssessmentEvents.MiniTaskCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.MINI_TASK_CREATE,
                AuditConstants.EntityTypes.MINI_TASK, event.miniTaskId().toString(), event.actorBranchId()));
    }
}
