package com.eduerp.identity;

import com.eduerp.core.exception.AppException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends AppException {
    public AccountNotFoundException(UUID id) {
        super("IDENTITY_ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản " + id);
    }
}
