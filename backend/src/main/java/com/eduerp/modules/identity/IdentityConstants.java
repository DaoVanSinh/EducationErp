package com.eduerp.modules.identity;

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
