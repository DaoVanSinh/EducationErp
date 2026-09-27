package com.eduerp.identity;

import org.springframework.http.HttpStatus;

public final class TokenInvalidException extends IdentityException {
    public TokenInvalidException(String reason) {
        super("IDENTITY_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, reason);
    }
}
