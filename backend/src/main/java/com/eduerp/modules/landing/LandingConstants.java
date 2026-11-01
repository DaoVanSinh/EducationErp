package com.eduerp.modules.landing;

/**
 * Hằng số của module landing — gom theo static inner class đúng quy ước
 * {@code springboot-modular-scaffold}.
 */
public final class LandingConstants {

    private LandingConstants() {
    }

    public enum ConsultationStatus {
        NEW,
        CONTACTED,
        ENROLLED,
        CLOSED
    }

    public static final class Defaults {
        private Defaults() {
        }

        public static final String DEFAULT_CONFIG_CODE = "DEFAULT";
        public static final String BRAND_NAME = "DS Academy";
        public static final String BRAND_SUBTITLE = "Nền Tảng Đào Tạo & Phát Triển Năng Lực Toàn Diện";
        public static final String TAGLINE = "Kiến tạo nền tảng vững chắc – Bứt phá kết quả đầu ra";
        public static final String HERO_BADGE = "TUYỂN SINH CÁC LỚP ĐÀO TẠO MỚI NHẤT";
        public static final String HERO_TITLE = "Lộ Trình Học Tập Bài Bản &";
        public static final String HERO_HIGHLIGHT = "Cam Kết Chất Lượng Đầu Ra";
        public static final String HERO_DESCRIPTION =
                "Khám phá các khoá học đang tuyển sinh, tra cứu lịch khai giảng tại từng cơ sở, đội ngũ giảng viên giàu kinh nghiệm và tính toán ngay mức ưu đãi học phí khi đăng ký theo gói lộ trình.";
        public static final String HOTLINE = "0869724615";
        public static final String HOTLINE_DISPLAY = "0869 724 615";
        public static final String EMAIL = "tuyensinh@hocvien.edu.vn";
        public static final String WORK_TIME = "Thứ 2 - Thứ 7: 08:00 - 20:30";
        public static final String DEFAULT_ADDRESS = "số 18 phố Viên, phường Đông Ngạc, Hà Nội";
        public static final String ZALO_URL = "https://zalo.me/0869724615";
        public static final String MESSENGER_URL = "https://m.me/hocvien";
        public static final String FOOTER_DESCRIPTION =
                "Đơn vị đào tạo uy tín với chương trình học chuẩn hoá, lộ trình cá nhân hoá theo năng lực, cơ sở vật chất hiện đại và đội ngũ giảng viên tận tâm đồng hành cùng học viên.";
    }

    public static final class Paths {
        private Paths() {
        }

        public static final String OVERVIEW = "/api/landing/overview";
        public static final String PUBLIC_CONSULTATIONS = "/api/landing/consultations";
        public static final String ADMIN_CONFIG = "/api/landing/admin/config";
        public static final String ADMIN_CONSULTATIONS = "/api/landing/admin/consultations";
    }

    public static final class ErrorCodes {
        private ErrorCodes() {
        }

        public static final String CONSULTATION_NOT_FOUND = "LANDING_CONSULTATION_NOT_FOUND";
    }
}
