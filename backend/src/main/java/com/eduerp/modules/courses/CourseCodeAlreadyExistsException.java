package com.eduerp.modules.courses;

import org.springframework.http.HttpStatus;

public final class CourseCodeAlreadyExistsException extends CoursesException {

    public CourseCodeAlreadyExistsException(String code) {
        super("COURSES_COURSE_CODE_ALREADY_EXISTS", HttpStatus.CONFLICT, "Mã khóa học " + code + " đã tồn tại");
    }
}
