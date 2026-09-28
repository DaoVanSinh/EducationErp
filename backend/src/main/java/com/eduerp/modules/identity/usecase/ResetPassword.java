package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.TokenInvalidException;
import com.eduerp.modules.identity.dto.ResetPasswordRequest;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.token.TokenBlacklistService;
import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.identity.IdentityConstants;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đặt lại mật khẩu thì mọi phiên đang mở phải chết theo, kể cả phiên của kẻ đã chiếm tài khoản. */
@Service
public class ResetPassword {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService blacklist;
    private final StringRedisTemplate redis;

    ResetPassword(AccountRepository accounts, PasswordEncoder passwordEncoder, TokenBlacklistService blacklist,
            StringRedisTemplate redis) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.blacklist = blacklist;
        this.redis = redis;
    }

    @Transactional
    public void execute(ResetPasswordRequest request) {
        String key = CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.PASSWORD_RESET_TOKEN, request.token());
        String accountIdRaw = redis.opsForValue().get(key);
        if (accountIdRaw == null) {
            throw new TokenInvalidException("Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }
        var accountId = UUID.fromString(accountIdRaw);
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        account.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        redis.delete(key);
        blacklist.blacklistAllActiveSessions(accountId);
    }
}
