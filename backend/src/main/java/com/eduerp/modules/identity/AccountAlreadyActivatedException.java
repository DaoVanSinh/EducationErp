package com.eduerp.modules.identity;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class AccountAlreadyActivatedException extends IdentityException {
    public AccountAlreadyActivatedException(UUID accountId) {
        super("IDENTITY_ACCOUNT_ALREADY_ACTIVATED", HttpStatus.CONFLICT,
                "Tài khoản " + accountId + " đã kích hoạt, không áp dụng thao tác mời");
    }
}
