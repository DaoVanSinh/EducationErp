package com.eduerp.modules.identity.usecase;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.identity.AccountAlreadyActivatedException;
import com.eduerp.modules.identity.AccountInviteExpiredException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.InvalidCredentialsException;
import com.eduerp.modules.identity.dto.CompletePasswordInviteRequest;
import com.eduerp.modules.identity.dto.SessionTokens;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.token.TokenIssuer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompletePasswordInvite {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final TokenIssuer tokenIssuer;

    CompletePasswordInvite(AccountRepository accounts, PasswordEncoder passwordEncoder, StringRedisTemplate redis,
            TokenIssuer tokenIssuer) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public SessionTokens execute(CompletePasswordInviteRequest request) {
        var account = accounts.findByTypedEmail(request.email()).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (account.getLastLogin() != null) {
            throw new AccountAlreadyActivatedException(account.getId());
        }
        var inviteKey = CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.ACCOUNT_INVITE, account.getId());
        if (Boolean.FALSE.equals(redis.hasKey(inviteKey))) {
            throw new AccountInviteExpiredException();
        }

        account.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        account.recordFirstLogin();
        redis.delete(inviteKey);
        return tokenIssuer.issuePair(account.getId());
    }
}
