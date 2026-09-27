package com.eduerp.modules.identity;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module identity phát ra. */
public sealed class IdentityException extends AppException
        permits AccountNotFoundException, InvalidCredentialsException, ReferenceNotFoundException, TokenInvalidException {

    protected IdentityException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
