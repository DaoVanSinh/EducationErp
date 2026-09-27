package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.InvalidCredentialsException;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.dto.SessionTokens;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
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

    Login(AccountRepository accounts, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer, IdentityRules rules) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public SessionTokens execute(LoginRequest request) {
        var account = accounts.findByTypedEmail(request.email()).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        // Tài khoản bị vô hiệu hoá trả cùng một lỗi với sai mật khẩu, để không tiết lộ tài khoản có tồn tại.
        if (!rules.canSignIn(account.getStatus())) {
            throw new InvalidCredentialsException();
        }
        return tokenIssuer.issuePair(account.getId());
    }
}
