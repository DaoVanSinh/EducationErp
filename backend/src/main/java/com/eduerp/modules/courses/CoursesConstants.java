package com.eduerp.modules.courses;

import java.math.BigDecimal;

/**
 * Hằng số dùng chung của module courses, nằm ở base package (không phải {@code internal}) vì
 * {@code dto} cũng cần tham chiếu - giống hệt {@code IdentityConstants.AccountStatus} vừa dùng được
 * trong entity vừa dùng được trong DTO mà không phá tier discipline (rule #14: {@code dto} và
 * {@code internal.model} cùng một tier, không được phụ thuộc lẫn nhau).
 */
public final class CoursesConstants {

    private CoursesConstants() {
    }

    public enum DayOfWeek {
        MON, TUE, WED, THU, FRI, SAT, SUN
    }

    /**
     * Vòng đời một lớp (V31): ENROLLING → IN_PROGRESS → COMPLETED, hoặc ENROLLING → CANCELLED khi không đủ
     * học viên. Chỉ hai trạng thái đầu còn "mở" (nhận ghi danh, cần giáo viên).
     */
    public enum ClassStatus {
        /** Đang tuyển sinh - đã mở lớp, chờ đủ sĩ số tối thiểu và tới ngày khai giảng. */
        ENROLLING,
        /** Đang học - đã khai giảng; điểm danh, nhập điểm, tiến trình chạy từ mốc này. */
        IN_PROGRESS,
        /** Đã kết thúc - ghi danh đang học được chuyển sang hoàn tất. */
        COMPLETED,
        /** Đã huỷ - không đủ học viên; ghi danh đang học được chuyển sang đã rút. */
        CANCELLED;

        public boolean isOpen() {
            return this == ENROLLING || this == IN_PROGRESS;
        }
    }

    /** Múi giờ quy "hôm nay" cho ngày khai giảng và tác vụ tự khai giảng - mirror các module khác. */
    public static final class Schedules {
        private Schedules() {
        }

        public static final String CENTER_ZONE_ID = "Asia/Ho_Chi_Minh";
        /** 00:30 mỗi ngày, sau mốc nửa đêm để "hôm nay" đã là ngày mới. */
        public static final String AUTO_START_CRON = "0 30 0 * * *";
    }

    public static final class ErrorCodes {
        private ErrorCodes() {
        }

        public static final String CLASS_NOT_EDITABLE = "COURSES_CLASS_NOT_EDITABLE";
        public static final String CLASS_INVALID_TRANSITION = "COURSES_CLASS_INVALID_TRANSITION";
        public static final String CLASS_NOT_ENOUGH_STUDENTS = "COURSES_CLASS_NOT_ENOUGH_STUDENTS";
        public static final String CLASS_START_DATE_NOT_REACHED = "COURSES_CLASS_START_DATE_NOT_REACHED";
        public static final String CLASS_NO_SCHEDULE = "COURSES_CLASS_NO_SCHEDULE";
        public static final String CLASS_SEATS_BELOW_ENROLLED = "COURSES_CLASS_SEATS_BELOW_ENROLLED";
        public static final String TEACHER_HAS_OPEN_CLASSES = "COURSES_TEACHER_HAS_OPEN_CLASSES";
    }

    /** Luật của cấu hình cột điểm theo khóa học (spec mục 2 quyết định 3 + mục 4). */
    public static final class GradeColumns {
        private GradeColumns() {
        }

        /** "Tổng trọng số các cột điểm của một Course bắt buộc = 100%" (spec mục 2.3). */
        public static final java.math.BigDecimal REQUIRED_TOTAL_WEIGHT_PERCENT = new java.math.BigDecimal("100.00");

        /** "sai lệch do làm tròn chấp nhận trong khoảng ±0.01" (spec mục 4) - ba cột 33.33 phải lưu được. */
        public static final java.math.BigDecimal WEIGHT_TOLERANCE = new java.math.BigDecimal("0.01");

        /** Khớp cột {@code course_grade_columns.weight_percent}/{@code max_score} NUMERIC(5,2) của V23. */
        public static final int WEIGHT_SCALE = 2;
    }
}
