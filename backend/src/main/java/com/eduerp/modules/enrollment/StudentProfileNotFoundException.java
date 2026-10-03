package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** 400 chứ không 404: id học viên là dữ liệu người dùng gửi lên trong body, không phải tài nguyên
 * của chính request này (mirror cách AppValidationException dùng 400 cho id trỏ sang module khác). */
public final class StudentProfileNotFoundException extends EnrollmentException {
    public StudentProfileNotFoundException(UUID studentProfileId) {
        super("ENROLLMENT_STUDENT_PROFILE_NOT_FOUND", HttpStatus.BAD_REQUEST,
                "Không tìm thấy hồ sơ học viên " + studentProfileId);
    }
}
