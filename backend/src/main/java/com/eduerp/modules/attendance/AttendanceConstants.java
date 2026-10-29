package com.eduerp.modules.attendance;

/** Hằng số của module attendance - mirror {@code AcademicConstants}. */
public final class AttendanceConstants {

    private AttendanceConstants() {
    }

    /**
     * Trạng thái của một học viên ở một buổi. {@code EXCUSED} (vắng có phép) KHÔNG tính vào mẫu số của
     * tỷ lệ chuyên cần - xem {@code AttendanceRules.summarize}.
     */
    public enum AttendanceStatus {
        PRESENT, LATE, ABSENT, EXCUSED
    }

    public static final class Limits {
        private Limits() {
        }

        public static final int MAX_NOTE_LENGTH = 500;
        public static final int PERCENT_SCALE = 100;
    }

    public static final class Schedules {
        private Schedules() {
        }

        /** "Hôm nay" của trung tâm - chặn điểm danh cho ngày trong tương lai phải theo giờ Việt Nam, không
         * theo múi giờ của máy chủ (mirror {@code AcademicConstants.Schedules.CENTER_ZONE_ID}). */
        public static final String CENTER_ZONE_ID = "Asia/Ho_Chi_Minh";
    }

    /** Không cần cache namespace nào - điểm danh đọc thẳng từ DB. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }
    }
}
