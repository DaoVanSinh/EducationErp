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
        set(response, IdentityConstants.Cookies.ACCESS_TOKEN, tokens.accessToken(), properties.accessTokenTtl(),
                properties);
        set(response, IdentityConstants.Cookies.REFRESH_TOKEN, tokens.refreshToken(), properties.refreshTokenTtl(),
                properties);
    }

    static void clearSession(HttpServletResponse response, IdentityProperties properties) {
        clear(response, IdentityConstants.Cookies.ACCESS_TOKEN, properties);
        clear(response, IdentityConstants.Cookies.REFRESH_TOKEN, properties);
    }

    private static void set(HttpServletResponse response, String name, String value, Duration ttl,
            IdentityProperties properties) {
        response.addHeader("Set-Cookie", name + "=" + value
                + "; Path=/; HttpOnly" + secureFlag(properties) + "; SameSite=Strict; Max-Age=" + ttl.toSeconds());
    }

    private static void clear(HttpServletResponse response, String name, IdentityProperties properties) {
        response.addHeader("Set-Cookie",
                name + "=; Path=/; HttpOnly" + secureFlag(properties) + "; SameSite=Strict; Max-Age=0");
    }

    private static String secureFlag(IdentityProperties properties) {
        return properties.secureCookies() ? "; Secure" : "";
    }
}
