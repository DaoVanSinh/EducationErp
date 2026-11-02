package com.eduerp.modules.enrollment;

import org.springframework.http.HttpStatus;

/**
 * Lớp sắp ghi danh trùng buổi với lớp học viên đang học. Mã lỗi cho giao diện biết bước tiếp theo: quản trị viên
 * được xác nhận chấp nhận trùng, nhân viên chi nhánh thì được hướng dẫn liên hệ quản trị viên.
 */
public final class ScheduleConflictException extends EnrollmentException {

    private ScheduleConflictException(String errorCode, String message) {
        super(errorCode, HttpStatus.CONFLICT, message);
    }

    public static ScheduleConflictException acceptable(String description) {
        return new ScheduleConflictException(EnrollmentConstants.ErrorCodes.SCHEDULE_CONFLICT, description);
    }

    public static ScheduleConflictException requiresAdmin(String description) {
        return new ScheduleConflictException(EnrollmentConstants.ErrorCodes.SCHEDULE_CONFLICT_CONTACT_ADMIN,
                description + " " + EnrollmentConstants.Messages.CONTACT_ADMIN_FOR_SCHEDULE_CHANGE);
    }
}
