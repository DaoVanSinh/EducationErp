package com.eduerp.modules.audit.internal.listener;

import com.eduerp.modules.access.AccessEvents;
import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.internal.model.AuditLog;
import com.eduerp.modules.audit.internal.repository.AuditLogRepository;
import com.eduerp.modules.identity.IdentityEvents;
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
}
