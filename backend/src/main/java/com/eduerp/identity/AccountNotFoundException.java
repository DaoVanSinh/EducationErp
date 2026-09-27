package com.eduerp.identity;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class AccountNotFoundException extends IdentityException {
    public AccountNotFoundException(UUID id) {
        super("IDENTITY_ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản " + id);
    }
}
