package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Lớp đã kết thúc hoặc đã huỷ - không còn nhận ghi danh (mã lỗi giữ nguyên từ Phân hệ 3). */
public final class ClassNotActiveException extends EnrollmentException {
    public ClassNotActiveException(UUID classId) {
        super("ENROLLMENT_CLASS_NOT_ACTIVE", HttpStatus.CONFLICT,
                "Lớp học " + classId + " đã kết thúc hoặc đã huỷ, không còn nhận ghi danh");
    }
}
