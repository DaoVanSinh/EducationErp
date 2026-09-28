package com.eduerp.modules.access.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessEvents;
import com.eduerp.modules.access.AccessReferenceNotFoundException;
import com.eduerp.modules.access.dto.CreatePermissionGroupRequest;
import com.eduerp.modules.access.internal.model.PermissionGroup;
import com.eduerp.modules.access.internal.repository.PermissionGroupRepository;
import com.eduerp.modules.access.internal.repository.PermissionRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatePermissionGroup {

    private final PermissionGroupRepository permissionGroups;
    private final PermissionRepository permissions;
    private final ApplicationEventPublisher events;

    CreatePermissionGroup(PermissionGroupRepository permissionGroups, PermissionRepository permissions,
            ApplicationEventPublisher events) {
        this.permissionGroups = permissionGroups;
        this.permissions = permissions;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreatePermissionGroupRequest request) {
        var group = new PermissionGroup(request.name(), request.description());
        for (var item : request.items()) {
            var permission = permissions.findById(item.permissionId()).orElseThrow(
                    () -> new AccessReferenceNotFoundException(AccessConstants.Resources.PERMISSION,
                            item.permissionId()));
            group.addItem(permission, item.scope());
        }
        var saved = permissionGroups.save(group);
        events.publishEvent(new AccessEvents.PermissionGroupCreated(saved.getId(), actorAccountId, actorBranchId));
        return saved.getId();
    }
}
