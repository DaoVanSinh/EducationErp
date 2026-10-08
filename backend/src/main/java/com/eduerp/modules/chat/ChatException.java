package com.eduerp.modules.chat;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module chat phát ra - mirror {@code AcademicException}. */
public sealed class ChatException extends AppException
        permits ChatbotUnavailableException, MessageTooLongException, TooManyMessagesException {

    protected ChatException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
