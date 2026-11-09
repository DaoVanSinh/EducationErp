package com.eduerp.modules.identity.usecase;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.identity.AccountLifecycleParticipant;
import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.internal.access.AccountLifecycleGuard;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.token.SessionRevocationService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Đóng tài khoản vĩnh viễn. Dữ liệu cũ giữ nguyên, email được giải phóng cho tài khoản mới. Bị chặn khi còn lớp đang
 * mở, còn đang học hoặc còn nợ học phí. Lấy participant trễ qua {@link ObjectProvider} vì chúng thuộc các module vốn
 * phụ thuộc ngược vào identity - cùng lý do với {@code RoleAssignmentService}.
 */
@Service
public class CloseAccount {

    private final AccountRepository accounts;
    private final AccountLifecycleGuard guard;
    private final ObjectProvider<AccountLifecycleParticipant> participants;
    private final SessionRevocationService revocations;
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher events;

    CloseAccount(AccountRepository accounts, AccountLifecycleGuard guard,
            ObjectProvider<AccountLifecycleParticipant> participants, SessionRevocationService revocations,
            StringRedisTemplate redis, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.guard = guard;
        this.participants = participants;
        this.revocations = revocations;
        this.redis = redis;
        this.events = events;
    }

    @Transactional
    public void execute(UUID accountId, UUID actorAccountId, UUID actorBranchId) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        guard.require(actorAccountId, account);
        if (account.isClosed()) {
            return;
        }
        participants.orderedStream().forEach(participant -> participant.beforeClose(accountId));
        account.close();
        var today = LocalDate.now(ZoneId.of(IdentityConstants.Schedules.CENTER_ZONE_ID));
        participants.orderedStream().forEach(participant -> participant.afterClose(accountId, today));
        events.publishEvent(new IdentityEvents.AccountClosed(accountId, actorAccountId, actorBranchId));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                revocations.revokeAll(accountId);
                redis.delete(CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.ACCOUNT_INVITE, accountId));
            }
        });
    }
}
