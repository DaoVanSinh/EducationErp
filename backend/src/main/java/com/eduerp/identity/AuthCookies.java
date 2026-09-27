package com.eduerp.identity;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;

final class AuthCookies {

    private AuthCookies() {
    }

    static void set(HttpServletResponse response, String name, String value, Duration ttl) {
        String cookie = name + "=" + value
                + "; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=" + ttl.toSeconds();
        response.addHeader("Set-Cookie", cookie);
    }

    static void clear(HttpServletResponse response, String name) {
        String cookie = name + "=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0";
        response.addHeader("Set-Cookie", cookie);
    }
}
