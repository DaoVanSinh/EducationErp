package com.eduerp.modules.access;

import com.eduerp.modules.access.internal.model.AccountGroupMembership;
import com.eduerp.modules.access.internal.model.AccountRoleAssignment;
import com.eduerp.modules.access.internal.permission.PermissionCacheService;
import com.eduerp.modules.access.internal.repository.AccountGroupMembershipRepository;
import com.eduerp.modules.access.internal.repository.AccountRoleAssignmentRepository;
import com.eduerp.modules.access.internal.repository.RoleRepository;
import com.eduerp.shared.NamedReference;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module access — type DUY NHẤT mà module khác được phép gọi (rule #1).
 */
@Service
public class AccessManagement {

    private final PermissionCacheService permissionCache;
    private final AccountRoleAssignmentRepository accountRoles;
    private final AccountGroupMembershipRepository accountGroups;
    private final RoleRepository roles;

    AccessManagement(PermissionCacheService permissionCache, AccountRoleAssignmentRepository accountRoles,
            AccountGroupMembershipRepository accountGroups, RoleRepository roles) {
        this.permissionCache = permissionCache;
        this.accountRoles = accountRoles;
        this.accountGroups = accountGroups;
        this.roles = roles;
    }

    public Set<EffectivePermission> effectivePermissions(UUID accountId) {
        return permissionCache.getEffectivePermissions(accountId);
    }

    /** Gọi sau khi role/group của tài khoản đổi để UI nhận quyền mới ngay lần gọi API kế tiếp. */
    public void evictPermissionCache(UUID accountId) {
        permissionCache.evict(accountId);
    }

    /**
     * Gán role cho một account — dùng khi seed tài khoản Admin mặc định (identity tạo Account trước,
     * rồi gọi lại đây để gán role). Idempotent: gọi lại đổi role thay vì tạo dòng thứ hai.
     */
    @Transactional
    public void assignRole(UUID accountId, String roleCode) {
        var role = roles.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Role " + roleCode + " chưa được seed"));
        assignRole(accountId, role);
    }

    /** Dùng khi nơi gọi chỉ có id (vd admin chọn vai trò từ dropdown catalog, không gõ code tay). */
    @Transactional
    public void assignRole(UUID accountId, UUID roleId) {
        var role = roles.findById(roleId)
                .orElseThrow(() -> new AccessReferenceNotFoundException("ROLE", roleId));
        assignRole(accountId, role);
    }

    private void assignRole(UUID accountId, com.eduerp.modules.access.internal.model.Role role) {
        accountRoles.findById(accountId)
                .ifPresentOrElse(existing -> existing.changeRole(role),
                        () -> accountRoles.save(new AccountRoleAssignment(accountId, role)));
    }

    /** Chỉ dùng trong test - sản phẩm thật luôn có id sẵn từ catalog, không bao giờ tra ngược từ code. */
    @Transactional(readOnly = true)
    public UUID roleIdOf(String code) {
        return roles.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Role " + code + " chưa được seed"))
                .getId();
    }

    /** Có ít nhất một account đang giữ role này không — dùng để quyết định có cần seed Admin mặc định không. */
    @Transactional(readOnly = true)
    public boolean hasAnyAccountWithRole(String roleCode) {
        return accountRoles.countByRole_Code(roleCode) > 0;
    }

    /** Role (code + tên) của một account, nếu đã được gán — phục vụ {@code /account/me}. */
    @Transactional(readOnly = true)
    public Optional<RoleSummary> roleOf(UUID accountId) {
        return accountRoles.findById(accountId)
                .map(assignment -> new RoleSummary(assignment.getRole().getCode(), assignment.getRole().getName()));
    }

    /** Role của một loạt account theo id — tránh N+1 khi liệt kê danh sách account (rule #3). */
    @Transactional(readOnly = true)
    public Map<UUID, RoleSummary> rolesFor(Collection<UUID> accountIds) {
        return accountRoles.findAllById(accountIds).stream()
                .collect(Collectors.toMap(AccountRoleAssignment::getAccountId,
                        a -> new RoleSummary(a.getRole().getCode(), a.getRole().getName())));
    }

    /** Group của một loạt account theo id — cùng lý do batch với {@link #rolesFor}. */
    @Transactional(readOnly = true)
    public Map<UUID, List<NamedReference>> groupsFor(Collection<UUID> accountIds) {
        return accountGroups.findByAccountIdIn(accountIds).stream()
                .collect(Collectors.groupingBy(AccountGroupMembership::getAccountId,
                        Collectors.mapping(m -> new NamedReference(m.getGroup().getId(), m.getGroup().getName()),
                                Collectors.collectingAndThen(Collectors.toList(),
                                        list -> list.stream().sorted(Comparator.comparing(NamedReference::name))
                                                .toList()))));
    }

    /** Số account theo từng role — role chưa ai giữ vẫn có mặt với 0, phục vụ dashboard. */
    @Transactional(readOnly = true)
    public List<RoleHeadcount> roleHeadcounts() {
        return accountRoles.countAccountsByRole().stream()
                .map(row -> new RoleHeadcount(row.getRoleCode(), row.getRoleName(), row.getAccountCount()))
                .toList();
    }

    /**
     * Bản lọc theo chi nhánh của {@link #roleHeadcounts()} — accountIds do identity cung cấp (rule
     * #3: access không có cột chi nhánh, không tự tra được "ai thuộc chi nhánh nào"). Chi nhánh không
     * ai thuộc (accountIds rỗng) tự trả về mọi role với 0, không query JPQL {@code IN ()} rỗng.
     */
    @Transactional(readOnly = true)
    public List<RoleHeadcount> roleHeadcounts(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return roles.findAll().stream()
                    .map(role -> new RoleHeadcount(role.getCode(), role.getName(), 0L))
                    .toList();
        }
        return accountRoles.countAccountsByRoleForAccounts(accountIds).stream()
                .map(row -> new RoleHeadcount(row.getRoleCode(), row.getRoleName(), row.getAccountCount()))
                .toList();
    }

    public record RoleSummary(String code, String name) {
    }

    public record RoleHeadcount(String roleCode, String roleName, long accountCount) {
    }
}
