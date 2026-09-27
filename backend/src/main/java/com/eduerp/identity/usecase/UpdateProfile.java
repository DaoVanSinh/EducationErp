package com.eduerp.identity.usecase;

import com.eduerp.identity.AccountNotFoundException;
import com.eduerp.identity.dto.ProfileUpdateRequest;
import com.eduerp.identity.internal.repository.AccountRepository;
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
