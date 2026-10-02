package com.eduerp.modules.audit.internal.listener;

import com.eduerp.modules.access.AccessEvents;
import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.internal.model.AuditLog;
import com.eduerp.modules.audit.internal.repository.AuditLogRepository;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.organization.OrganizationEvents;
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
}
