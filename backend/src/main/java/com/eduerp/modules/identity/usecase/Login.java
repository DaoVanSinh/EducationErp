package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.InvalidCredentialsException;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.dto.SessionTokens;
import com.eduerp.modules.identity.internal.model.AuditLog;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.repository.AuditLogRepository;
import com.eduerp.modules.identity.internal.rules.IdentityRules;
import com.eduerp.modules.identity.internal.token.TokenIssuer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Login {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final IdentityRules rules;
    private final AuditLogRepository auditLogs;

    Login(AccountRepository accounts, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer, IdentityRules rules,
            AuditLogRepository auditLogs) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.rules = rules;
        this.auditLogs = auditLogs;
    }

    /**
     * Đây là use case duy nhất ghi audit trực tiếp thay vì qua {@code @Audited}. Hai lý do đều
     * thuộc về riêng việc đăng nhập: đối số của nó là {@code LoginRequest} chứa mật khẩu plaintext,
     * và lúc chạy thì {@code SecurityContext} còn rỗng nên aspect không biết ai vừa đăng nhập —
     * dòng audit sẽ không có actor, đúng thứ mà dashboard cần đọc.
     */
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
        auditLogs.save(new AuditLog(account.getId(), IdentityConstants.AuditActions.LOGIN,
                IdentityConstants.Resources.ACCOUNT, account.getId().toString(),
                account.getHomeBranch() == null ? null : account.getHomeBranch().getId()));
        return tokens;
    }
}
