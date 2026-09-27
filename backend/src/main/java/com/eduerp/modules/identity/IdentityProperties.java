package com.eduerp.modules.identity;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity")
public record IdentityProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration passwordResetTtl,
        String mailFrom,
        String frontendResetUrl,
        String defaultAdminEmail,
        String defaultAdminPassword,
        /**
         * Gắn cờ {@code Secure} vào cookie phiên. Bật trên mọi môi trường thật; chỉ tắt khi chạy
         * local qua http, vì có trình duyệt từ chối cookie {@code Secure} trên kết nối không TLS
         * và khi đó đăng nhập thành công nhưng cookie không được lưu.
         */
        boolean secureCookies) {
}
