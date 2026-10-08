package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Cùng tên đơn giản với {@code java.lang.ClassNotFoundException} và
 * {@code modules.courses.ClassNotFoundException} nhưng khác package - lỗi của riêng enrollment, mang
 * errorCode riêng; mọi nơi dùng phải import tường minh (mirror cách courses đã đặt tên). */
public final class ClassNotFoundException extends EnrollmentException {
    public ClassNotFoundException(UUID classId) {
        super("ENROLLMENT_CLASS_NOT_FOUND", HttpStatus.BAD_REQUEST, "Không tìm thấy lớp học " + classId);
    }
}
