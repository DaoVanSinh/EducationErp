package com.eduerp.modules.access.internal.permission;

import com.eduerp.modules.access.EffectivePermission;
import com.eduerp.modules.access.RoleAssignmentParticipant;
import com.eduerp.modules.access.internal.model.AccountRoleAssignment;
import com.eduerp.modules.access.internal.model.Role;
import com.eduerp.modules.access.internal.repository.AccountGroupMembershipRepository;
import com.eduerp.modules.access.internal.repository.AccountRoleAssignmentRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Gán hoặc đổi vai trò của MỘT tài khoản, kèm các module tham gia ({@link RoleAssignmentParticipant}).
 * Dùng chung cho tạo tài khoản (gán lần đầu), seed admin, và admin đổi vai trò - nên mọi đường gán vai
 * trò đều đi qua cùng các kiểm tra "còn dạy lớp / còn đang học" và cùng việc tự tạo hồ sơ.
 *
 * <p>Quyền "trước" và "sau" được TÍNH LẠI từ DB (role + group hiện tại), không đọc từ cache Redis: cache
 * có thể cũ tới 10 phút, mà quyết định chặn/cho phép phải dựa trên dữ liệu thật.
 */
@Component
public class RoleAssignmentService {

    private final AccountRoleAssignmentRepository accountRoles;
    private final AccountGroupMembershipRepository accountGroups;
    private final EffectivePermissionCalculator calculator;
    private final PermissionChangeNotifier notifier;
    /**
     * Lấy trễ (lúc gọi, không lúc khởi tạo): participant của enrollment cần {@code StudentsManagement},
     * mà chuỗi {@code StudentsManagement → IdentityManagement → CreateAccount → AccessManagement} lại dẫn
     * về chính bean này - inject thẳng một {@code List} sẽ thành vòng phụ thuộc lúc khởi động.
     */
    private final ObjectProvider<RoleAssignmentParticipant> participants;

    RoleAssignmentService(AccountRoleAssignmentRepository accountRoles, AccountGroupMembershipRepository accountGroups,
            EffectivePermissionCalculator calculator, PermissionChangeNotifier notifier,
            ObjectProvider<RoleAssignmentParticipant> participants) {
        this.accountRoles = accountRoles;
        this.accountGroups = accountGroups;
        this.calculator = calculator;
        this.notifier = notifier;
        this.participants = participants;
    }

    /**
     * @return {@code true} nếu vai trò thật sự đổi (hoặc được gán lần đầu), {@code false} nếu tài khoản
     *         vốn đã giữ đúng vai trò này - idempotent, không gọi lại participant
     */
    public boolean assign(UUID accountId, Role role) {
        var groups = new HashSet<>(accountGroups.findGroupsByAccountId(accountId));
        Set<EffectivePermission> after = calculator.calculate(role, groups);
        var existing = accountRoles.findById(accountId).orElse(null);
        if (existing == null) {
            accountRoles.save(new AccountRoleAssignment(accountId, role));
            participants.orderedStream().forEach(participant -> participant.afterRoleAssigned(accountId, after));
            notifier.evictAfterCommit(List.of(accountId));
            return true;
        }
        if (existing.getRole().getId().equals(role.getId())) {
            return false;
        }
        Set<EffectivePermission> before = calculator.calculate(existing.getRole(), groups);
        participants.orderedStream().forEach(participant -> participant.beforeRoleChange(accountId, before, after));
        existing.changeRole(role);
        participants.orderedStream().forEach(participant -> participant.afterRoleAssigned(accountId, after));
        notifier.permissionsChanged(List.of(accountId));
        return true;
    }
}
