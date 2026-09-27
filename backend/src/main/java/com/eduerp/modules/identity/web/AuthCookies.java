package com.eduerp.modules.identity.web;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityProperties;
import com.eduerp.modules.identity.dto.SessionTokens;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;

/** Nơi duy nhất sinh header {@code Set-Cookie} cho phiên đăng nhập. */
final class AuthCookies {

    private AuthCookies() {
    }

    static void write(HttpServletResponse response, SessionTokens tokens, IdentityProperties properties) {
        set(response, IdentityConstants.Cookies.ACCESS_TOKEN, tokens.accessToken(), properties.accessTokenTtl());
        set(response, IdentityConstants.Cookies.REFRESH_TOKEN, tokens.refreshToken(), properties.refreshTokenTtl());
    }

    static void clearSession(HttpServletResponse response) {
        clear(response, IdentityConstants.Cookies.ACCESS_TOKEN);
        clear(response, IdentityConstants.Cookies.REFRESH_TOKEN);
    }

    private static void set(HttpServletResponse response, String name, String value, Duration ttl) {
        response.addHeader("Set-Cookie", name + "=" + value
                + "; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=" + ttl.toSeconds());
    }

    private static void clear(HttpServletResponse response, String name) {
        response.addHeader("Set-Cookie", name + "=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0");
    }
}
