package com.eduerp.modules.students;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class StudentProfileAlreadyExistsException extends StudentsException {
    public StudentProfileAlreadyExistsException(UUID accountId) {
        super("STUDENTS_PROFILE_ALREADY_EXISTS", HttpStatus.CONFLICT,
                "Tài khoản " + accountId + " đã có hồ sơ học viên");
    }
}
