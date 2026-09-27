package com.eduerp.identity;

import org.springframework.http.HttpStatus;

public final class InvalidCredentialsException extends IdentityException {
    public InvalidCredentialsException() {
        super("IDENTITY_INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
    }
}
