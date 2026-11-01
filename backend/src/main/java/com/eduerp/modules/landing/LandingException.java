package com.eduerp.modules.landing;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module landing phát ra. */
public sealed class LandingException extends AppException
        permits LandingConsultationNotFoundException {

    protected LandingException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
