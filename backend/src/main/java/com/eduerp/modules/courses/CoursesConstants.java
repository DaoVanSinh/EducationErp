package com.eduerp.modules.courses;

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
}
