package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.dto.SessionResponse;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.OrganizationManagement;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCurrentSession {

    private final AccountRepository accounts;
    private final AccessManagement access;
    private final OrganizationManagement organization;

    GetCurrentSession(AccountRepository accounts, AccessManagement access, OrganizationManagement organization) {
        this.accounts = accounts;
        this.access = access;
        this.organization = organization;
    }

    /**
     * Quyền lấy từ đúng cache mà bộ lọc xác thực dùng, nên những gì giao diện ẩn/hiện luôn khớp với
     * những gì backend thật sự cho qua — kể cả ngay sau một lần đổi group đã evict cache.
     */
    @Transactional(readOnly = true)
    public SessionResponse execute(UUID accountId) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        var role = access.roleOf(accountId).orElse(null);
        var branchId = account.getHomeBranchId();
        var branchName = branchId == null ? null
                : organization.namesOf(Set.of(branchId)).get(branchId);
        var permissions = access.effectivePermissions(accountId).stream()
                .map(p -> new SessionResponse.GrantedPermission(p.resource(), p.action(), p.scope()))
                .sorted(Comparator.comparing(SessionResponse.GrantedPermission::resource)
                        .thenComparing(SessionResponse.GrantedPermission::action))
                .toList();
        return new SessionResponse(account.getId(), account.getEmail(), account.getFullName(),
                account.getAvatarUrl(), role == null ? null : role.code(), role == null ? null : role.name(),
                branchId, branchName, permissions);
    }
}
