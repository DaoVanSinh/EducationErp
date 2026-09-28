package com.eduerp.modules.identity.internal.access;

import com.eduerp.modules.access.AccountExistenceCheck;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class IdentityAccountExistenceCheck implements AccountExistenceCheck {

    private final AccountRepository accounts;

    IdentityAccountExistenceCheck(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean exists(UUID accountId) {
        return accounts.existsById(accountId);
    }
}
