package com.eduerp.identity;

import com.eduerp.identity.dto.ChangePasswordRequest;
import com.eduerp.identity.dto.ForgotPasswordRequest;
import com.eduerp.identity.dto.ProfileUpdateRequest;
import com.eduerp.identity.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountSelfServiceController {

    private static final String RESET_TOKEN_PREFIX = "pwreset:token:";

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService blacklist;
    private final StringRedisTemplate redis;
    private final PasswordResetMailer mailer;
    private final IdentityProperties properties;

    AccountSelfServiceController(AccountRepository accounts, PasswordEncoder passwordEncoder,
            TokenBlacklistService blacklist, StringRedisTemplate redis,
            PasswordResetMailer mailer, IdentityProperties properties) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.blacklist = blacklist;
        this.redis = redis;
        this.mailer = mailer;
        this.properties = properties;
    }

    @PostMapping("/change-password")
    @Transactional
    public void changePassword(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        var account = accounts.findById(principal.accountId()).orElseThrow(() -> new AccountNotFoundException(principal.accountId()));
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        account.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @PostMapping("/forgot-password")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accounts.findByEmail(request.email()).ifPresent(account -> {
            String token = UUID.randomUUID().toString();
            redis.opsForValue().set(RESET_TOKEN_PREFIX + token, account.getId().toString(), properties.passwordResetTtl());
            mailer.sendResetLink(account.getEmail(), token);
        });
    }

    @PostMapping("/reset-password")
    @Transactional
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String key = RESET_TOKEN_PREFIX + request.token();
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

    @PatchMapping("/profile")
    @Transactional
    public void updateProfile(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request) {
        var account = accounts.findById(principal.accountId()).orElseThrow(() -> new AccountNotFoundException(principal.accountId()));
        account.updateProfile(request.fullName(), request.avatarUrl());
    }
}
