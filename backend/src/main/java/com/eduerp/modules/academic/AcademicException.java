package com.eduerp.modules.academic;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module academic phát ra - mirror {@code BillingException}. */
public sealed class AcademicException extends AppException
        permits ClassNotFoundException, EnrollmentNotFoundException, GradeColumnNotInCourseException,
        InvalidScoreException, NotAssignedToClassException {

    protected AcademicException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
