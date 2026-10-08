package com.eduerp.modules.academic;

import java.math.BigDecimal;

/**
 * Hằng số dùng chung của module academic, nằm ở base package (không phải {@code internal}) vì
 * {@code dto} và {@code internal} cùng tier, cả hai cần tham chiếu - mirror
 * {@code CoursesConstants}/{@code BillingConstants}.
 */
public final class AcademicConstants {

    private AcademicConstants() {
    }

    public static final class Limits {
        private Limits() {
        }

        /** Trần của công thức tiến trình: {@code min(đã học, chuẩn) / chuẩn * 100} (spec mục 5) -
         * học bù quá số buổi chuẩn vẫn dừng ở 100%, không ra 112%. */
        public static final int PROGRESS_PERCENT_CAP = 100;
    }

    public static final class Scoring {
        private Scoring() {
        }

        /** Khớp {@code grade_entries.score} và {@code course_grade_columns.max_score} NUMERIC(5,2). */
        public static final int SCORE_SCALE = 2;

        /** Chia {@code score / maxScore} trước khi nhân trọng số nên cần dư chữ số thập phân, nếu
         * không 8/10 thành 0 (BigDecimal chia theo scale của số bị chia) - mirror
         * {@code BillingRules.DISCOUNT_MATH_SCALE}. */
        public static final int WEIGHTED_MATH_SCALE = 6;

        /** Điểm thấp nhất nhận được ở mọi cột. Trần là {@code maxScore} của riêng từng cột, không
         * phải một hằng dùng chung (spec mục 6). */
        public static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    }

    public static final class Schedules {
        private Schedules() {
        }

        /**
         * Múi giờ dùng để quy một {@code Instant} ({@code Enrollment.enrolledAt}) về một ngày/giờ
         * địa phương so được với {@code ClassSchedule.startTime} — cột đó là {@code TIME} không mang
         * múi giờ, nên phép đếm buổi học BẮT BUỘC phải chọn một múi. Trung tâm vận hành ở Việt Nam
         * nên lịch "18:00 thứ Hai" là 18:00 giờ Việt Nam; không được dùng múi giờ của máy chạy
         * server, vì khi đó cùng một dữ liệu sẽ cho hai con số khác nhau trên hai môi trường.
         */
        public static final String CENTER_ZONE_ID = "Asia/Ho_Chi_Minh";
    }

    /** Nhãn hiển thị trong file Excel xuất ra (spec mục 7) - người dùng đọc trực tiếp các chuỗi này. */
    public static final class Excel {
        private Excel() {
        }

        public static final String CONTENT_TYPE =
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        public static final String GRADEBOOK_SHEET_NAME = "Bảng điểm";
        public static final String PROGRESS_SHEET_NAME = "Tiến trình học tập";
        public static final String GRADEBOOK_FILE_NAME = "bang-diem.xlsx";
        public static final String PROGRESS_FILE_NAME = "tien-trinh-hoc-tap.xlsx";
        public static final String HEADER_STUDENT_NAME = "Học viên";
        public static final String HEADER_FINAL_SCORE = "Điểm tổng kết";
        public static final String HEADER_ENROLLED_AT = "Ngày ghi danh";
        public static final String HEADER_STATUS = "Trạng thái";
        public static final String HEADER_ELAPSED_SESSIONS = "Số buổi đã học";
        public static final String HEADER_STANDARD_SESSIONS = "Tổng số buổi chuẩn";
        public static final String HEADER_PROGRESS_PERCENT = "Tiến trình (%)";
        /** Ô trống không được in "null": với điểm tổng kết nó có nghĩa "chưa đủ điểm" (spec mục 6),
         * với tiến trình nó có nghĩa "chưa cấu hình tổng số buổi chuẩn" (spec mục 5). */
        public static final String CELL_NOT_ENOUGH_SCORES = "Chưa đủ điểm";
        public static final String CELL_SESSION_COUNT_NOT_CONFIGURED = "Chưa cấu hình tổng số buổi chuẩn";
    }

    /** Không cần cache namespace nào ở phase này - điểm đọc trực tiếp từ DB, không qua Redis. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }
    }
}
