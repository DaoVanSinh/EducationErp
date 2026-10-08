package com.eduerp.modules.teachers;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

public sealed class TeachersException extends AppException
        permits TeacherProfileNotFoundException, TeacherProfileAlreadyExistsException, TeacherAccountRoleMismatchException {

    protected TeachersException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
