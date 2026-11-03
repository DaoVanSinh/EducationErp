package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Lớp đã khai giảng, đã kết thúc hoặc đã huỷ - không nhận thêm học viên (mã lỗi giữ nguyên từ Phân hệ 3). */
public final class ClassNotActiveException extends EnrollmentException {
    public ClassNotActiveException(UUID classId) {
        super("ENROLLMENT_CLASS_NOT_ACTIVE", HttpStatus.CONFLICT,
                "Lớp học đã khai giảng hoặc đã đóng, không nhận thêm học viên");
    }
}
