package com.eduerp.modules.identity;

import org.springframework.http.HttpStatus;

public final class EmailAlreadyExistsException extends IdentityException {
    public EmailAlreadyExistsException(String email) {
        super("IDENTITY_EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT, "Email " + email + " đã được sử dụng");
    }
}
