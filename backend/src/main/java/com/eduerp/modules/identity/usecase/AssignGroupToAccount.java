package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.ReferenceNotFoundException;
import com.eduerp.modules.identity.dto.AssignGroupRequest;
import com.eduerp.modules.identity.internal.audit.Audited;
import com.eduerp.modules.identity.internal.permission.PermissionCacheService;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.repository.GroupRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignGroupToAccount {

    private final AccountRepository accounts;
    private final GroupRepository groups;
    private final PermissionCacheService permissionCache;

    AssignGroupToAccount(AccountRepository accounts, GroupRepository groups, PermissionCacheService permissionCache) {
        this.accounts = accounts;
        this.groups = groups;
        this.permissionCache = permissionCache;
    }

    /**
     * Vào group là đổi quyền hiệu lực, nên phải xoá cache — nếu không, người dùng vẫn mang bộ quyền
     * cũ cho tới khi cache tự hết hạn, và UI sẽ hiển thị sai đúng bằng khoảng thời gian đó.
     */
    @Transactional
    @Audited(action = IdentityConstants.AuditActions.ACCOUNT_JOIN_GROUP,
            entityType = IdentityConstants.Resources.ACCOUNT)
    public void execute(UUID accountId, AssignGroupRequest request) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        var group = groups.findById(request.groupId()).orElseThrow(
                () -> new ReferenceNotFoundException(IdentityConstants.Resources.GROUP, request.groupId()));
        account.joinGroup(group);
        permissionCache.evict(accountId);
    }
}
