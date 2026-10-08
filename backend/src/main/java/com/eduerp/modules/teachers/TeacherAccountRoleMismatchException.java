package com.eduerp.modules.teachers;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** 400, không phải 404/409: accountId tồn tại nhưng không mang role TEACHER - dữ liệu đầu vào sai, không phải thiếu tài nguyên. */
public final class TeacherAccountRoleMismatchException extends TeachersException {
    public TeacherAccountRoleMismatchException(UUID accountId) {
        super("TEACHERS_ACCOUNT_ROLE_MISMATCH", HttpStatus.BAD_REQUEST,
                "Tài khoản " + accountId + " không có vai trò TEACHER");
    }
}
