package com.eduerp.modules.organization;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module organization phát ra. */
public sealed class OrganizationException extends AppException
        permits BranchNotFoundException, BranchCodeAlreadyExistsException {

    protected OrganizationException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
