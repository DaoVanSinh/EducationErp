package com.eduerp.modules.teachers;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class TeacherProfileAlreadyExistsException extends TeachersException {
    public TeacherProfileAlreadyExistsException(UUID accountId) {
        super("TEACHERS_PROFILE_ALREADY_EXISTS", HttpStatus.CONFLICT,
                "Tài khoản " + accountId + " đã có hồ sơ giáo viên");
    }
}
