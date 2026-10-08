package com.eduerp.modules.students;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class StudentProfileNotFoundException extends StudentsException {
    public StudentProfileNotFoundException(UUID id) {
        super("STUDENTS_PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ học viên " + id);
    }
}
