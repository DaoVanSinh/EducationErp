package com.eduerp.modules.identity;

import java.util.Set;

public final class IdentityConstants {

    private IdentityConstants() {
    }

    public enum AccountStatus {
        ACTIVE, DISABLED
    }

    public static final class TokenTypes {
        private TokenTypes() {
        }

        public static final String ACCESS = "access";
        public static final String REFRESH = "refresh";
    }

    public static final class ErrorCodes {
        private ErrorCodes() {
        }

        public static final String INVALID_AVATAR = "IDENTITY_INVALID_AVATAR";
        public static final String AVATAR_NOT_FOUND = "IDENTITY_AVATAR_NOT_FOUND";
    }

    /**
     * Cấu hình chuẩn hoá và lưu trữ ảnh đại diện trên MinIO — theo khuôn {@code LogoImage} /
     * {@code SaveMcpLogo} của mcp-hub.
     */
    public static final class Avatar {
        private Avatar() {
        }

        public static final String KEY_PREFIX = "avatars";
        public static final String ENDPOINT_PATH = "/api/account/avatar";
        public static final String KEY_QUERY_PREFIX = ENDPOINT_PATH + "?key=";
        public static final int MAX_BYTES = 5 * 1024 * 1024;
        public static final int MAX_PIXELS = 4_000_000;
        public static final int MAX_DIMENSION = 512;
        public static final String CONTENT_TYPE_PNG = "image/png";
        public static final String CONTENT_TYPE_JPEG = "image/jpeg";
        public static final String CONTENT_TYPE_WEBP = "image/webp";
        public static final String NORMALIZED_FORMAT = "png";
        public static final String NORMALIZED_FILE_NAME = "avatar.png";
        public static final String WEBP_FILE_NAME = "avatar.webp";
        public static final String CACHE_CONTROL = "private, max-age=86400";
        public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
                CONTENT_TYPE_PNG,
                CONTENT_TYPE_JPEG,
                CONTENT_TYPE_WEBP);
    }

    /**
     * Namespace Redis mà module này sở hữu. Tên nghiệp vụ nằm ở đây, việc lắp key nằm ở
     * {@code integrations.cache.CacheKeyBuilder} — nên {@code integrations} không cần biết
     * "pwreset" hay "sessions" nghĩa là gì.
     */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }

        public static final String BLACKLISTED_JTI = "blacklist:jti";
        public static final String ACCOUNT_SESSIONS = "sessions:account";
        public static final String PASSWORD_RESET_TOKEN = "pwreset:token";
        public static final String ACCOUNT_INVITE = "invite:account";
    }

    public static final class Cookies {
        private Cookies() {
        }

        public static final String ACCESS_TOKEN = "access_token";
        public static final String REFRESH_TOKEN = "refresh_token";
    }

    /** Giá trị mặc định do chính module quyết định, không phụ thuộc cấu hình triển khai. */
    public static final class Defaults {
        private Defaults() {
        }

        public static final String ADMIN_FULL_NAME = "Quản trị viên mặc định";
    }
}
