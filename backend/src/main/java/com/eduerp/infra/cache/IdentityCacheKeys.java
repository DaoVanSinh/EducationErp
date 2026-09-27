package com.eduerp.infra.cache;

import java.util.UUID;

/** Toàn bộ key Redis mà module {@code identity} dùng. Không nơi nào khác được nối chuỗi key này. */
public final class IdentityCacheKeys {

    private static final String BLACKLISTED_JTI = "blacklist:jti:";
    private static final String ACCOUNT_SESSIONS = "sessions:account:";
    private static final String EFFECTIVE_PERMISSIONS = "perm:account:";
    private static final String PASSWORD_RESET_TOKEN = "pwreset:token:";

    private IdentityCacheKeys() {
    }

    public static String blacklistedJti(String jti) {
        return BLACKLISTED_JTI + jti;
    }

    public static String accountSessions(UUID accountId) {
        return ACCOUNT_SESSIONS + accountId;
    }

    public static String effectivePermissions(UUID accountId) {
        return EFFECTIVE_PERMISSIONS + accountId;
    }

    public static String passwordResetToken(String token) {
        return PASSWORD_RESET_TOKEN + token;
    }

    /** Dùng cho quét key (vận hành, test) thay vì viết lại tiền tố ở nơi khác. */
    public static String passwordResetTokenPattern() {
        return PASSWORD_RESET_TOKEN + "*";
    }

    public static String passwordResetTokenOf(String key) {
        return key.substring(PASSWORD_RESET_TOKEN.length());
    }
}
