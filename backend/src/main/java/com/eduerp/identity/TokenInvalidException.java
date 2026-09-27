package com.eduerp.identity;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

public class TokenInvalidException extends AppException {
    public TokenInvalidException(String reason) {
        super("IDENTITY_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, reason);
    }
}
