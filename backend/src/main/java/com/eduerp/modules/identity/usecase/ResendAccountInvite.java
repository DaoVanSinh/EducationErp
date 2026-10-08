package com.eduerp.modules.identity.usecase;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.identity.AccountAlreadyActivatedException;
import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.IdentityProperties;
import com.eduerp.modules.identity.internal.mail.AccountInviteMailer;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.util.RandomPasswordGenerator;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResendAccountInvite {

    private final AccountRepository accounts;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final AccountInviteMailer mailer;
    private final IdentityProperties properties;
    private final ApplicationEventPublisher events;

    ResendAccountInvite(AccountRepository accounts, StringRedisTemplate redis, PasswordEncoder passwordEncoder,
            AccountInviteMailer mailer, IdentityProperties properties, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.redis = redis;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.properties = properties;
        this.events = events;
    }

    @Transactional
    public void execute(UUID accountId, UUID actorAccountId, UUID actorBranchId) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        if (account.getLastLogin() != null) {
            throw new AccountAlreadyActivatedException(accountId);
        }
        account.activate();

        var rawPassword = RandomPasswordGenerator.generate();
        account.changePasswordHash(passwordEncoder.encode(rawPassword));
        redis.opsForValue().set(CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.ACCOUNT_INVITE, accountId),
                Instant.now().toString(), properties.accountInviteTtl());
        mailer.sendInvite(account.getEmail(), account.getFullName(), rawPassword);

        events.publishEvent(new IdentityEvents.AccountInviteResent(accountId, actorAccountId, actorBranchId));
    }
}
