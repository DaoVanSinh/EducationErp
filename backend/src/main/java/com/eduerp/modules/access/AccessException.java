package com.eduerp.modules.access;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module access phát ra. */
public sealed class AccessException extends AppException permits AccessReferenceNotFoundException {

    protected AccessException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
