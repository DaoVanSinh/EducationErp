package com.eduerp.modules.identity.usecase;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.EmailAlreadyExistsException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.IdentityProperties;
import com.eduerp.modules.identity.ReferenceNotFoundException;
import com.eduerp.modules.identity.dto.CreateAccountRequest;
import com.eduerp.modules.identity.internal.mail.AccountInviteMailer;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.util.RandomPasswordGenerator;
import com.eduerp.modules.organization.OrganizationManagement;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateAccount {

    private final AccountRepository accounts;
    private final AccessManagement access;
    private final OrganizationManagement organization;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final AccountInviteMailer mailer;
    private final IdentityProperties properties;
    private final ApplicationEventPublisher events;

    CreateAccount(AccountRepository accounts, AccessManagement access, OrganizationManagement organization,
            StringRedisTemplate redis, PasswordEncoder passwordEncoder, AccountInviteMailer mailer,
            IdentityProperties properties, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.access = access;
        this.organization = organization;
        this.redis = redis;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.properties = properties;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateAccountRequest request) {
        if (accounts.findByTypedEmail(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException(request.email());
        }
        if (request.homeBranchId() != null && !organization.exists(request.homeBranchId())) {
            throw new ReferenceNotFoundException(AccessConstants.Resources.BRANCH, request.homeBranchId());
        }

        var rawPassword = RandomPasswordGenerator.generate();
        var account = accounts.save(new Account(request.email(), passwordEncoder.encode(rawPassword),
                request.fullName(), request.homeBranchId()));
        access.assignRole(account.getId(), request.roleId());

        redis.opsForValue().set(CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.ACCOUNT_INVITE, account.getId()),
                java.time.Instant.now().toString(), properties.accountInviteTtl());
        mailer.sendInvite(account.getEmail(), account.getFullName(), rawPassword);

        events.publishEvent(new IdentityEvents.AccountCreated(account.getId(), actorAccountId, actorBranchId));
        return account.getId();
    }
}
