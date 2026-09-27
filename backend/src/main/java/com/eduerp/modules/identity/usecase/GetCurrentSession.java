package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.dto.SessionResponse;
import com.eduerp.modules.identity.internal.permission.PermissionCacheService;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCurrentSession {

    private final AccountRepository accounts;
    private final PermissionCacheService permissionCache;

    GetCurrentSession(AccountRepository accounts, PermissionCacheService permissionCache) {
        this.accounts = accounts;
        this.permissionCache = permissionCache;
    }

    /**
     * Quyền lấy từ đúng cache mà bộ lọc xác thực dùng, nên những gì giao diện ẩn/hiện luôn khớp với
     * những gì backend thật sự cho qua — kể cả ngay sau một lần đổi group đã evict cache.
     */
    @Transactional(readOnly = true)
    public SessionResponse execute(UUID accountId) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        var branch = account.getHomeBranch();
        var permissions = permissionCache.getEffectivePermissions(accountId).stream()
                .map(p -> new SessionResponse.GrantedPermission(p.resource(), p.action(), p.scope()))
                .sorted(Comparator.comparing(SessionResponse.GrantedPermission::resource)
                        .thenComparing(SessionResponse.GrantedPermission::action))
                .toList();
        return new SessionResponse(account.getId(), account.getEmail(), account.getFullName(),
                account.getAvatarUrl(), account.getRole().getCode(), account.getRole().getName(),
                branch == null ? null : branch.getId(), branch == null ? null : branch.getName(),
                permissions);
    }
}
