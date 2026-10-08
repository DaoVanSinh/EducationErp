package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.InvalidCredentialsException;
import com.eduerp.modules.identity.dto.ChangePasswordRequest;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangePassword {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    ChangePassword(AccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void execute(UUID accountId, ChangePasswordRequest request) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        account.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }
}
