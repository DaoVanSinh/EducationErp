package com.eduerp.core.exception;

import org.springframework.http.HttpStatus;

public abstract class AppException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    protected AppException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
