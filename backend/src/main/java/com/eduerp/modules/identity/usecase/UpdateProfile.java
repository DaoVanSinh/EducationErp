package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.dto.ProfileUpdateRequest;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateProfile {

    private final AccountRepository accounts;

    UpdateProfile(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public void execute(UUID accountId, ProfileUpdateRequest request) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        account.updateProfile(request.fullName(), request.avatarUrl());
    }
}
