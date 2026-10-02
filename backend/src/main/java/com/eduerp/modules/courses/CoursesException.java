package com.eduerp.modules.courses;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module courses phát ra. */
public sealed class CoursesException extends AppException
        permits CourseNotFoundException, ClassNotFoundException, CourseCodeAlreadyExistsException,
        ClassCodeAlreadyExistsException {

    protected CoursesException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
