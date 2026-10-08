package com.eduerp.modules.students;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

public sealed class StudentsException extends AppException
        permits StudentProfileNotFoundException, StudentProfileAlreadyExistsException, StudentAccountRoleMismatchException {

    protected StudentsException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
