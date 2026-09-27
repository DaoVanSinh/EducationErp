package com.eduerp.identity.web;

import com.eduerp.identity.IdentityConstants;
import com.eduerp.identity.IdentityProperties;
import com.eduerp.identity.dto.LoginRequest;
import com.eduerp.identity.usecase.Login;
import com.eduerp.identity.usecase.Logout;
import com.eduerp.identity.usecase.RefreshSession;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final Login login;
    private final RefreshSession refreshSession;
    private final Logout logout;
    private final IdentityProperties properties;

    AuthController(Login login, RefreshSession refreshSession, Logout logout, IdentityProperties properties) {
        this.login = login;
        this.refreshSession = refreshSession;
        this.logout = logout;
        this.properties = properties;
    }

    @PostMapping("/login")
    void login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthCookies.write(response, login.execute(request), properties);
    }

    @PostMapping("/refresh")
    void refresh(@CookieValue(IdentityConstants.Cookies.REFRESH_TOKEN) String refreshToken,
            HttpServletResponse response) {
        AuthCookies.write(response, refreshSession.execute(refreshToken), properties);
    }

    @PostMapping("/logout")
    void logout(@CookieValue(IdentityConstants.Cookies.ACCESS_TOKEN) String accessToken,
            @CookieValue(value = IdentityConstants.Cookies.REFRESH_TOKEN, required = false) String refreshToken,
            HttpServletResponse response) {
        logout.execute(accessToken, refreshToken);
        AuthCookies.clearSession(response);
    }
}
