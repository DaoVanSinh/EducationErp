package com.eduerp.modules.identity.usecase;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.identity.AccountAlreadyActivatedException;
import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeAccountInvite {

    private final AccountRepository accounts;
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher events;

    RevokeAccountInvite(AccountRepository accounts, StringRedisTemplate redis, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.redis = redis;
        this.events = events;
    }

    @Transactional
    public void execute(UUID accountId, UUID actorAccountId, UUID actorBranchId) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        if (account.getLastLogin() != null) {
            throw new AccountAlreadyActivatedException(accountId);
        }
        redis.delete(CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.ACCOUNT_INVITE, accountId));
        account.disable();

        events.publishEvent(new IdentityEvents.AccountInviteRevoked(accountId, actorAccountId, actorBranchId));
    }
}
