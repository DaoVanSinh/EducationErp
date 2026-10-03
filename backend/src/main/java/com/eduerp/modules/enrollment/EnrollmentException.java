package com.eduerp.modules.enrollment;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module enrollment phát ra. */
public sealed class EnrollmentException extends AppException
        permits EnrollmentNotFoundException, StudentProfileNotFoundException, StudentNotActiveException,
        ClassNotFoundException, ClassNotActiveException, ClassFullException, DuplicateActiveEnrollmentException,
        EnrollmentNotActiveException {

    protected EnrollmentException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
