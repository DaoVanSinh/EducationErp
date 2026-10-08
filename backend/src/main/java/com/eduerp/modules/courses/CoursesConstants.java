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
