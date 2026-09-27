package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.ReferenceNotFoundException;
import com.eduerp.modules.identity.dto.CreateRoleRequest;
import com.eduerp.modules.identity.internal.audit.Audited;
import com.eduerp.modules.identity.internal.model.Role;
import com.eduerp.modules.identity.internal.repository.PermissionGroupRepository;
import com.eduerp.modules.identity.internal.repository.RoleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateRole {

    private final RoleRepository roles;
    private final PermissionGroupRepository permissionGroups;

    CreateRole(RoleRepository roles, PermissionGroupRepository permissionGroups) {
        this.roles = roles;
        this.permissionGroups = permissionGroups;
    }

    /** Role tạo qua API luôn có {@code systemDefault = false}: chỉ migration được tạo role hệ thống. */
    @Transactional
    @Audited(action = IdentityConstants.AuditActions.ROLE_CREATE, entityType = IdentityConstants.Resources.ROLE)
    public UUID execute(CreateRoleRequest request) {
        var role = new Role(request.code(), request.name(), false);
        for (UUID permissionGroupId : request.permissionGroupIds()) {
            role.addPermissionGroup(permissionGroups.findById(permissionGroupId).orElseThrow(
                    () -> new ReferenceNotFoundException(IdentityConstants.Resources.PERMISSION_GROUP,
                            permissionGroupId)));
        }
        return roles.save(role).getId();
    }
}
