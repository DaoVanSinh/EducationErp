package com.eduerp.modules.courses;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class CourseNotFoundException extends CoursesException {

    public CourseNotFoundException(UUID courseId) {
        super("COURSES_COURSE_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy khóa học " + courseId);
    }
}
