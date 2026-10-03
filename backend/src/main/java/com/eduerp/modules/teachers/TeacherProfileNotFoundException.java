package com.eduerp.modules.teachers;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class TeacherProfileNotFoundException extends TeachersException {
    public TeacherProfileNotFoundException(UUID id) {
        super("TEACHERS_PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ giáo viên " + id);
    }
}
