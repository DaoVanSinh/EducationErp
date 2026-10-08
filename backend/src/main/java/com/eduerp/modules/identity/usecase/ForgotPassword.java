package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.IdentityProperties;
import com.eduerp.modules.identity.dto.ForgotPasswordRequest;
import com.eduerp.modules.identity.internal.mail.PasswordResetMailer;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.identity.IdentityConstants;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Email không tồn tại vẫn trả 200 và không gửi gì — tránh dò danh sách tài khoản. */
@Service
public class ForgotPassword {

    private final AccountRepository accounts;
    private final StringRedisTemplate redis;
    private final PasswordResetMailer mailer;
    private final IdentityProperties properties;

    ForgotPassword(AccountRepository accounts, StringRedisTemplate redis, PasswordResetMailer mailer,
            IdentityProperties properties) {
        this.accounts = accounts;
        this.redis = redis;
        this.mailer = mailer;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public void execute(ForgotPasswordRequest request) {
        accounts.findByTypedEmail(request.email()).ifPresent(account -> {
            String token = UUID.randomUUID().toString();
            redis.opsForValue().set(CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.PASSWORD_RESET_TOKEN, token), account.getId().toString(),
                    properties.passwordResetTtl());
            mailer.sendResetLink(account.getEmail(), token);
        });
    }
}
