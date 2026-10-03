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

    /** Không có hạn mức nào ở phân hệ này (sĩ số nằm ở Class.maxSeats, thuộc modules.courses). */
    public static final class Limits {
        private Limits() {
        }
    }

    /** Không cần cache namespace nào ở V1 - để trống theo khuôn tier 0 của PayrollConstants. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }
    }
}
