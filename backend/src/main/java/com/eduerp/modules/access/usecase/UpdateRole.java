package com.eduerp.modules.access.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessEvents;
import com.eduerp.modules.access.AccessException;
import com.eduerp.modules.access.AccessReferenceNotFoundException;
import com.eduerp.modules.access.dto.UpdateRoleRequest;
import com.eduerp.modules.access.internal.model.PermissionGroup;
import com.eduerp.modules.access.internal.permission.PermissionChangeNotifier;
import com.eduerp.modules.access.internal.repository.AccountRoleAssignmentRepository;
import com.eduerp.modules.access.internal.repository.PermissionGroupRepository;
import com.eduerp.modules.access.internal.repository.RoleRepository;
import com.eduerp.modules.access.internal.rules.AccessRules;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đổi tên và bộ nhóm quyền của một vai trò (spec mục 5: vai trò hệ thống "không xoá được nhưng sửa được
 * quyền"). Riêng vai trò ADMIN bị khoá - xem {@link AccessRules#isLockedRole(String)}. Mọi người đang giữ
 * vai trò nhận quyền mới ngay lần gọi API kế tiếp (cache bị xoá sau commit, trình duyệt được báo qua SSE).
 */
@Service
public class UpdateRole {

    private final RoleRepository roles;
    private final PermissionGroupRepository permissionGroups;
    private final AccountRoleAssignmentRepository accountRoles;
    private final AccessRules rules;
    private final PermissionChangeNotifier notifier;
    private final ApplicationEventPublisher events;

    UpdateRole(RoleRepository roles, PermissionGroupRepository permissionGroups,
            AccountRoleAssignmentRepository accountRoles, AccessRules rules, PermissionChangeNotifier notifier,
            ApplicationEventPublisher events) {
        this.roles = roles;
        this.permissionGroups = permissionGroups;
        this.accountRoles = accountRoles;
        this.rules = rules;
        this.notifier = notifier;
        this.events = events;
    }

    @Transactional
    public void execute(UUID roleId, UUID actorAccountId, UUID actorBranchId, UpdateRoleRequest request) {
        var role = roles.findById(roleId).orElseThrow(
                () -> new AccessReferenceNotFoundException(AccessConstants.Resources.ROLE, roleId));
        if (rules.isLockedRole(role.getCode())) {
            throw new AccessException.SystemAdminLocked();
        }
        List<PermissionGroup> groups = new ArrayList<>();
        for (UUID permissionGroupId : new LinkedHashSet<>(request.permissionGroupIds())) {
            groups.add(permissionGroups.findById(permissionGroupId).orElseThrow(
                    () -> new AccessReferenceNotFoundException(AccessConstants.Resources.PERMISSION_GROUP,
                            permissionGroupId)));
        }
        role.rename(request.name().strip());
        role.replacePermissionGroups(groups);

        notifier.permissionsChanged(accountRoles.findAccountIdsByRoleIds(List.of(roleId)));
        events.publishEvent(new AccessEvents.RoleUpdated(roleId, actorAccountId, actorBranchId));
    }
}
