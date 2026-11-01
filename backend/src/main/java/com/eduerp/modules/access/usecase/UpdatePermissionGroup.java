package com.eduerp.modules.access.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessEvents;
import com.eduerp.modules.access.AccessException;
import com.eduerp.modules.access.AccessReferenceNotFoundException;
import com.eduerp.modules.access.dto.CreatePermissionGroupRequest;
import com.eduerp.modules.access.dto.UpdatePermissionGroupRequest;
import com.eduerp.modules.access.internal.model.Permission;
import com.eduerp.modules.access.internal.permission.PermissionChangeNotifier;
import com.eduerp.modules.access.internal.repository.AccountRoleAssignmentRepository;
import com.eduerp.modules.access.internal.repository.PermissionGroupRepository;
import com.eduerp.modules.access.internal.repository.PermissionRepository;
import com.eduerp.modules.access.internal.repository.RoleRepository;
import com.eduerp.modules.access.internal.rules.AccessRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sửa tên, mô tả và danh sách (quyền, phạm vi) của một nhóm quyền. Nhóm "Toàn quyền hệ thống" bị khoá.
 * Người bị ảnh hưởng = người giữ mọi vai trò chứa nhóm này.
 */
@Service
public class UpdatePermissionGroup {

    private final PermissionGroupRepository permissionGroups;
    private final PermissionRepository permissions;
    private final RoleRepository roles;
    private final AccountRoleAssignmentRepository accountRoles;
    private final AccessRules rules;
    private final PermissionChangeNotifier notifier;
    private final ApplicationEventPublisher events;

    UpdatePermissionGroup(PermissionGroupRepository permissionGroups, PermissionRepository permissions,
            RoleRepository roles, AccountRoleAssignmentRepository accountRoles, AccessRules rules,
            PermissionChangeNotifier notifier, ApplicationEventPublisher events) {
        this.permissionGroups = permissionGroups;
        this.permissions = permissions;
        this.roles = roles;
        this.accountRoles = accountRoles;
        this.rules = rules;
        this.notifier = notifier;
        this.events = events;
    }

    @Transactional
    public void execute(UUID permissionGroupId, UUID actorAccountId, UUID actorBranchId,
            UpdatePermissionGroupRequest request) {
        var group = permissionGroups.findById(permissionGroupId).orElseThrow(
                () -> new AccessReferenceNotFoundException(AccessConstants.Resources.PERMISSION_GROUP,
                        permissionGroupId));
        if (rules.isLockedPermissionGroup(permissionGroupId)) {
            throw new AccessException.SystemAdminLocked();
        }
        Map<UUID, AccessConstants.PermissionScope> desired = new LinkedHashMap<>();
        Map<UUID, Permission> permissionsById = new HashMap<>();
        for (CreatePermissionGroupRequest.Item item : request.items()) {
            var permission = permissions.findById(item.permissionId()).orElseThrow(
                    () -> new AccessReferenceNotFoundException(AccessConstants.Resources.PERMISSION,
                            item.permissionId()));
            permissionsById.put(permission.getId(), permission);
            desired.put(permission.getId(), item.scope());
        }
        group.setName(request.name().strip());
        group.setDescription(request.description());
        group.syncItems(permissionsById, desired);

        notifier.permissionsChanged(affectedAccounts(permissionGroupId));
        events.publishEvent(new AccessEvents.PermissionGroupUpdated(permissionGroupId, actorAccountId,
                actorBranchId));
    }

    private List<UUID> affectedAccounts(UUID permissionGroupId) {
        var affected = new ArrayList<UUID>();
        var roleIds = roles.findIdsByPermissionGroupId(permissionGroupId);
        if (!roleIds.isEmpty()) {
            affected.addAll(accountRoles.findAccountIdsByRoleIds(roleIds));
        }
        return affected;
    }
}
