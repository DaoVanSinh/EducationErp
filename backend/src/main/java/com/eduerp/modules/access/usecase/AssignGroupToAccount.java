package com.eduerp.modules.access.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessEvents;
import com.eduerp.modules.access.AccessReferenceNotFoundException;
import com.eduerp.modules.access.AccountExistenceCheck;
import com.eduerp.modules.access.dto.AssignGroupRequest;
import com.eduerp.modules.access.internal.model.AccountGroupMembership;
import com.eduerp.modules.access.internal.permission.PermissionCacheService;
import com.eduerp.modules.access.internal.repository.AccountGroupMembershipRepository;
import com.eduerp.modules.access.internal.repository.GroupRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AssignGroupToAccount {

    private final AccountExistenceCheck accountExists;
    private final GroupRepository groups;
    private final AccountGroupMembershipRepository memberships;
    private final PermissionCacheService permissionCache;
    private final ApplicationEventPublisher events;

    AssignGroupToAccount(AccountExistenceCheck accountExists, GroupRepository groups,
            AccountGroupMembershipRepository memberships, PermissionCacheService permissionCache,
            ApplicationEventPublisher events) {
        this.accountExists = accountExists;
        this.groups = groups;
        this.memberships = memberships;
        this.permissionCache = permissionCache;
        this.events = events;
    }

    /**
     * Vào group là đổi quyền hiệu lực, nên phải xoá cache — nếu không, người dùng vẫn mang bộ quyền
     * cũ cho tới khi cache tự hết hạn. Evict SAU commit (rule #6): nếu transaction rollback, cache
     * không được xoá cho một thay đổi chưa từng thật sự xảy ra.
     */
    @Transactional
    public void execute(UUID accountId, UUID actorAccountId, UUID actorBranchId, AssignGroupRequest request) {
        if (!accountExists.exists(accountId)) {
            throw new AccessReferenceNotFoundException(AccessConstants.Resources.ACCOUNT, accountId);
        }
        var group = groups.findById(request.groupId()).orElseThrow(
                () -> new AccessReferenceNotFoundException(AccessConstants.Resources.GROUP, request.groupId()));
        memberships.save(new AccountGroupMembership(accountId, group));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                permissionCache.evict(accountId);
                events.publishEvent(new AccessEvents.AccountJoinedGroup(accountId, group.getId(), actorAccountId,
                        actorBranchId));
            }
        });
    }
}
