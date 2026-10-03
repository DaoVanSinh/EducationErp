package com.eduerp.modules.audit.internal.listener;

import com.eduerp.modules.access.AccessEvents;
import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.internal.model.AuditLog;
import com.eduerp.modules.audit.internal.repository.AuditLogRepository;
import com.eduerp.modules.courses.CoursesEvents;
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
    void on(AccessEvents.AccountJoinedGroup event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ACCOUNT_JOIN_GROUP,
                AuditConstants.EntityTypes.ACCOUNT, event.accountId().toString(), event.actorBranchId()));
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
}
