package com.eduerp.modules.teachers;

/**
 * Hằng số của module teachers, nằm ở base package vì {@code dto}, {@code internal.model} và module
 * {@code payroll} đều tham chiếu - mirror {@code CoursesConstants}.
 */
public final class TeachersConstants {

    private TeachersConstants() {
    }

    /** Hình thức trả lương - quyết định công thức trong {@code PayrollRules}. */
    public enum PayType {
        /** Lương tháng cố định, có BHXH, thuế lũy tiến. */
        MONTHLY,
        /** Trả theo giờ dạy thực tế từ điểm danh, không BHXH, khấu trừ 10%. */
        HOURLY
    }

    public static final class Limits {
        private Limits() {
        }

        public static final int MAX_ALLOWANCE_NAME_LENGTH = 100;
        public static final int MAX_ALLOWANCES = 20;
        public static final int MAX_DEPENDENTS = 20;
    }

    public static final class Schedules {
        private Schedules() {
        }

        public static final String CENTER_ZONE_ID = "Asia/Ho_Chi_Minh";
    }
}
