package com.eduerp.modules.identity.internal.util;

import java.security.SecureRandom;

/** Mật khẩu tạm gửi qua email mời tài khoản — không dùng Base64/UUID thẳng vì cần tránh ký tự dễ nhầm (0/O, l/1). */
public final class RandomPasswordGenerator {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
    private static final int LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomPasswordGenerator() {
    }

    public static String generate() {
        var builder = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            builder.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return builder.toString();
    }
}
