package com.eduerp.modules.access.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessEvents;
import com.eduerp.modules.access.AccessException;
import com.eduerp.modules.access.AccessReferenceNotFoundException;
import com.eduerp.modules.access.AccountExistenceCheck;
import com.eduerp.modules.access.EffectivePermission;
import com.eduerp.modules.access.dto.ChangeAccountRoleRequest;
import com.eduerp.modules.access.internal.model.Role;
import com.eduerp.modules.access.internal.permission.EffectivePermissionCalculator;
import com.eduerp.modules.access.internal.permission.RoleAssignmentService;
import com.eduerp.modules.access.internal.repository.AccountRoleAssignmentRepository;
import com.eduerp.modules.access.internal.repository.RoleRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin gốc đổi vai trò giữa các vai trò nhân viên không có hồ sơ riêng. Vào hoặc ra khỏi Giáo viên, Học viên không đi
 * qua đây: đó là một đợt mới, phải đóng tài khoản cũ rồi tạo tài khoản mới cùng email, nên mỗi tài khoản chỉ có một vai
 * trò nghiệp vụ và một bộ dữ liệu. Vai trò Quản trị viên chỉ dành cho admin gốc.
 */
@Service
public class ChangeAccountRole {

    private final AccountExistenceCheck accountExists;
    private final RoleRepository roles;
    private final AccountRoleAssignmentRepository accountRoles;
    private final EffectivePermissionCalculator calculator;
    private final RoleAssignmentService roleAssignment;
    private final ApplicationEventPublisher events;

    ChangeAccountRole(AccountExistenceCheck accountExists, RoleRepository roles,
            AccountRoleAssignmentRepository accountRoles, EffectivePermissionCalculator calculator,
            RoleAssignmentService roleAssignment, ApplicationEventPublisher events) {
        this.accountExists = accountExists;
        this.roles = roles;
        this.accountRoles = accountRoles;
        this.calculator = calculator;
        this.roleAssignment = roleAssignment;
        this.events = events;
    }

    @Transactional
    public void execute(UUID accountId, UUID actorAccountId, UUID actorBranchId, ChangeAccountRoleRequest request) {
        if (accountId.equals(actorAccountId)) {
            throw new AccessException.SelfModificationForbidden();
        }
        if (!accountExists.exists(accountId)) {
            throw new AccessReferenceNotFoundException(AccessConstants.Resources.ACCOUNT, accountId);
        }
        var role = roles.findById(request.roleId()).orElseThrow(
                () -> new AccessReferenceNotFoundException(AccessConstants.Resources.ROLE, request.roleId()));
        var current = accountRoles.findById(accountId).map(assignment -> assignment.getRole()).orElse(null);
        requireAllowed(current, role);
        if (roleAssignment.assign(accountId, role)) {
            events.publishEvent(new AccessEvents.AccountRoleChanged(accountId, role.getId(), actorAccountId,
                    actorBranchId));
        }
    }

    private void requireAllowed(Role current, Role target) {
        if (isAdmin(target) || (current != null && isAdmin(current))) {
            throw new AccessException.SystemAdminLocked();
        }
        if (hasOwnBusinessRecord(target) || (current != null && hasOwnBusinessRecord(current))) {
            throw new AccessException.RoleChangeRequiresNewAccount();
        }
    }

    private static boolean isAdmin(Role role) {
        return AccessConstants.RoleCodes.ADMIN.equals(role.getCode());
    }

    private boolean hasOwnBusinessRecord(Role role) {
        var granted = calculator.calculate(role);
        return EffectivePermission.grants(granted, AccessConstants.Resources.TEACHING, AccessConstants.Actions.READ)
                || EffectivePermission.grants(granted, AccessConstants.Resources.LEARNING,
                        AccessConstants.Actions.READ);
    }
}
