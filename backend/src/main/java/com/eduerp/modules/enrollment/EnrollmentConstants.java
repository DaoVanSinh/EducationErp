package com.eduerp.modules.enrollment;

/**
 * Hằng số dùng chung của module enrollment, nằm ở base package (không phải {@code internal}) vì
 * {@code dto} và {@code internal.model} cùng tier, cả hai cần tham chiếu - mirror
 * {@code CoursesConstants}/{@code PayrollConstants}.
 */
public final class EnrollmentConstants {

    private EnrollmentConstants() {
    }

    /** Tên của ACTIVE xuất hiện nguyên văn trong partial unique index của V17 - đổi tên là đổi DB. */
    public enum EnrollmentStatus {
        ACTIVE, WITHDRAWN, COMPLETED
    }

    /** Sĩ số nằm ở Class.maxSeats (modules.courses); ở đây chỉ có hạn mức của phần kiểm tra trùng lịch. */
    public static final class Limits {
        private Limits() {
        }

        /** Lớp không có ngày kết thúc lẫn số buổi chuẩn: xét trùng lịch trong khoảng này kể từ ngày khai giảng. */
        public static final int OPEN_END_HORIZON_WEEKS = 26;
        /** Trùng nhiều hơn số này thì câu báo lỗi chỉ ghi số buổi và khoảng ngày, không liệt kê từng ngày. */
        public static final int MAX_CONFLICT_DATES_LISTED = 3;
        public static final int MAX_ALTERNATIVE_CLASSES_LISTED = 3;
    }

    public static final class ErrorCodes {
        private ErrorCodes() {
        }

        /** Người gọi có quyền toàn tổ chức: được gửi lại kèm xác nhận chấp nhận trùng lịch. */
        public static final String SCHEDULE_CONFLICT = "ENROLLMENT_SCHEDULE_CONFLICT";
        /** Người gọi chỉ có quyền theo chi nhánh: không được tự chấp nhận, phải liên hệ quản trị viên. */
        public static final String SCHEDULE_CONFLICT_CONTACT_ADMIN = "ENROLLMENT_SCHEDULE_CONFLICT_CONTACT_ADMIN";
    }

    public static final class Messages {
        private Messages() {
        }

        public static final String CONTACT_ADMIN_FOR_SCHEDULE_CHANGE =
                "Vui lòng liên hệ quản trị viên để được tư vấn đổi lịch.";
    }

    public static final class Schedules {
        private Schedules() {
        }

        /** "Hôm nay" của trung tâm khi xét buổi nào còn chưa diễn ra - mirror {@code AttendanceConstants}. */
        public static final String CENTER_ZONE_ID = "Asia/Ho_Chi_Minh";
    }

    /** Không cần cache namespace nào ở V1 - để trống theo khuôn tier 0 của PayrollConstants. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }
    }
}
