package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.InvalidCredentialsException;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.dto.SessionTokens;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.rules.IdentityRules;
import com.eduerp.modules.identity.internal.token.TokenIssuer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Login {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final IdentityRules rules;
    private final ApplicationEventPublisher events;

    Login(AccountRepository accounts, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer, IdentityRules rules,
            ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.rules = rules;
        this.events = events;
    }

    @Transactional
    public SessionTokens execute(LoginRequest request) {
        var account = accounts.findByTypedEmail(request.email()).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        // Tài khoản bị vô hiệu hoá trả cùng một lỗi với sai mật khẩu, để không tiết lộ tài khoản có tồn tại.
        if (!rules.canSignIn(account.getStatus())) {
            throw new InvalidCredentialsException();
        }
        var tokens = tokenIssuer.issuePair(account.getId());
        // Publish thay vì ghi trực tiếp: trước đây Login phải tự ghi AuditLog vì SecurityContext còn
        // rỗng lúc này, nhưng actor ở đây chính là account vừa đăng nhập — truyền tường minh trong
        // payload, không còn cần đọc SecurityContext hay là trường hợp đặc biệt nữa.
        events.publishEvent(new IdentityEvents.AccountSignedIn(account.getId(), account.getHomeBranchId()));
        return tokens;
    }
}
