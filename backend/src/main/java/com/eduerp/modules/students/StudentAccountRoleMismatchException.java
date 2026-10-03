package com.eduerp.modules.students;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** 400, không phải 404/409: accountId tồn tại nhưng không mang role STUDENT - dữ liệu đầu vào sai, không phải thiếu tài nguyên. */
public final class StudentAccountRoleMismatchException extends StudentsException {
    public StudentAccountRoleMismatchException(UUID accountId) {
        super("STUDENTS_ACCOUNT_ROLE_MISMATCH", HttpStatus.BAD_REQUEST,
                "Tài khoản " + accountId + " không có vai trò STUDENT");
    }
}
