package com.eduerp.modules.access.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessEvents;
import com.eduerp.modules.access.AccessException;
import com.eduerp.modules.access.AccessReferenceNotFoundException;
import com.eduerp.modules.access.AccountExistenceCheck;
import com.eduerp.modules.access.dto.ChangeAccountRoleRequest;
import com.eduerp.modules.access.internal.permission.RoleAssignmentService;
import com.eduerp.modules.access.internal.repository.RoleRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin đổi vai trò của một tài khoản đã có (vd tạo nhầm Học viên thay vì Giáo viên). Các module nghiệp
 * vụ được hỏi trước qua {@code RoleAssignmentParticipant}: còn dạy lớp đang mở thì không được bỏ năng
 * lực giáo viên, còn đang học thì không được bỏ năng lực học viên; sau khi đổi, hồ sơ tương ứng được tự
 * tạo hoặc tạm ngưng.
 */
@Service
public class ChangeAccountRole {

    private final AccountExistenceCheck accountExists;
    private final RoleRepository roles;
    private final RoleAssignmentService roleAssignment;
    private final ApplicationEventPublisher events;

    ChangeAccountRole(AccountExistenceCheck accountExists, RoleRepository roles,
            RoleAssignmentService roleAssignment, ApplicationEventPublisher events) {
        this.accountExists = accountExists;
        this.roles = roles;
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
        if (roleAssignment.assign(accountId, role)) {
            events.publishEvent(new AccessEvents.AccountRoleChanged(accountId, role.getId(), actorAccountId,
                    actorBranchId));
        }
    }
}
