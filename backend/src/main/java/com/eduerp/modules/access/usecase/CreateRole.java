package com.eduerp.modules.access.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessEvents;
import com.eduerp.modules.access.AccessReferenceNotFoundException;
import com.eduerp.modules.access.dto.CreateRoleRequest;
import com.eduerp.modules.access.internal.model.Role;
import com.eduerp.modules.access.internal.repository.PermissionGroupRepository;
import com.eduerp.modules.access.internal.repository.RoleRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateRole {

    private final RoleRepository roles;
    private final PermissionGroupRepository permissionGroups;
    private final ApplicationEventPublisher events;

    CreateRole(RoleRepository roles, PermissionGroupRepository permissionGroups, ApplicationEventPublisher events) {
        this.roles = roles;
        this.permissionGroups = permissionGroups;
        this.events = events;
    }

    /** Role tạo qua API luôn có {@code systemDefault = false}: chỉ migration được tạo role hệ thống. */
    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateRoleRequest request) {
        var role = new Role(request.code(), request.name(), false);
        for (UUID permissionGroupId : request.permissionGroupIds()) {
            role.addPermissionGroup(permissionGroups.findById(permissionGroupId).orElseThrow(
                    () -> new AccessReferenceNotFoundException(AccessConstants.Resources.PERMISSION_GROUP,
                            permissionGroupId)));
        }
        var saved = roles.save(role);
        events.publishEvent(new AccessEvents.RoleCreated(saved.getId(), actorAccountId, actorBranchId));
        return saved.getId();
    }
}
