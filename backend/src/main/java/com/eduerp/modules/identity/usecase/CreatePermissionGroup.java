package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.ReferenceNotFoundException;
import com.eduerp.modules.identity.dto.CreatePermissionGroupRequest;
import com.eduerp.modules.identity.internal.audit.Audited;
import com.eduerp.modules.identity.internal.model.PermissionGroup;
import com.eduerp.modules.identity.internal.repository.PermissionGroupRepository;
import com.eduerp.modules.identity.internal.repository.PermissionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatePermissionGroup {

    private final PermissionGroupRepository permissionGroups;
    private final PermissionRepository permissions;

    CreatePermissionGroup(PermissionGroupRepository permissionGroups, PermissionRepository permissions) {
        this.permissionGroups = permissionGroups;
        this.permissions = permissions;
    }

    @Transactional
    @Audited(action = IdentityConstants.AuditActions.PERMISSION_GROUP_CREATE,
            entityType = IdentityConstants.Resources.PERMISSION_GROUP)
    public UUID execute(CreatePermissionGroupRequest request) {
        var group = new PermissionGroup(request.name(), request.description());
        for (var item : request.items()) {
            var permission = permissions.findById(item.permissionId()).orElseThrow(
                    () -> new ReferenceNotFoundException(IdentityConstants.Resources.PERMISSION,
                            item.permissionId()));
            group.addItem(permission, item.scope());
        }
        return permissionGroups.save(group).getId();
    }
}
