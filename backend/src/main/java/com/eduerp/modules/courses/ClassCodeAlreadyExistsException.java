package com.eduerp.modules.courses;

import org.springframework.http.HttpStatus;

public final class ClassCodeAlreadyExistsException extends CoursesException {

    public ClassCodeAlreadyExistsException(String code) {
        super("COURSES_CLASS_CODE_ALREADY_EXISTS", HttpStatus.CONFLICT, "Mã lớp " + code + " đã tồn tại");
    }
}
