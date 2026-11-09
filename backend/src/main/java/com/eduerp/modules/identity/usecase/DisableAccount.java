package com.eduerp.modules.identity.usecase;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.IdentityException;
import com.eduerp.modules.identity.internal.access.AccountLifecycleGuard;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.token.SessionRevocationService;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tạm đóng một tài khoản, mở lại được. Lương vẫn tính vì đây chỉ là chặn đăng nhập. Không đăng nhập được nữa, và mọi phiên
 * đang mở bị thu hồi ngay - không đợi access token hết hạn. Lời mời còn treo (nếu chưa kích hoạt) cũng
 * bị huỷ, để mật khẩu tạm trong email không còn dùng được.
 *
 * <p>Thu hồi phiên SAU commit: transaction rollback thì không được đá người dùng ra cho một lần khoá
 * chưa từng xảy ra.
 */
@Service
public class DisableAccount {

    private final AccountRepository accounts;
    private final AccountLifecycleGuard guard;
    private final SessionRevocationService revocations;
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher events;

    DisableAccount(AccountRepository accounts, AccountLifecycleGuard guard, SessionRevocationService revocations,
            StringRedisTemplate redis, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.guard = guard;
        this.revocations = revocations;
        this.redis = redis;
        this.events = events;
    }

    @Transactional
    public void execute(UUID accountId, UUID actorAccountId, UUID actorBranchId) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        guard.require(actorAccountId, account);
        if (account.isClosed()) {
            throw new IdentityException.AccountClosed(accountId);
        }
        if (account.getStatus() == IdentityConstants.AccountStatus.DISABLED) {
            return;
        }
        account.disable();
        events.publishEvent(new IdentityEvents.AccountDisabled(accountId, actorAccountId, actorBranchId));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                revocations.revokeAll(accountId);
                redis.delete(CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.ACCOUNT_INVITE, accountId));
            }
        });
    }
}
