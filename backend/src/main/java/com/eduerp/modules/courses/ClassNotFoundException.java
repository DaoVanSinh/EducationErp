package com.eduerp.modules.courses;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ClassNotFoundException extends CoursesException {

    public ClassNotFoundException(UUID classId) {
        super("COURSES_CLASS_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy lớp học " + classId);
    }
}
