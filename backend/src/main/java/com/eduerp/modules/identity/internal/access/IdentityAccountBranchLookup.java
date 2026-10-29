package com.eduerp.modules.identity.internal.access;

import com.eduerp.modules.access.AccountBranchLookup;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class IdentityAccountBranchLookup implements AccountBranchLookup {

    private final AccountRepository accounts;

    IdentityAccountBranchLookup(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<UUID> homeBranchOf(UUID accountId) {
        return accounts.findById(accountId).map(Account::getHomeBranchId);
    }
}
