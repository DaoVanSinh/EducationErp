package com.eduerp.modules.identity.web;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityProperties;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.dto.LoginResponse;
import com.eduerp.modules.identity.usecase.Login;
import com.eduerp.modules.identity.usecase.Logout;
import com.eduerp.modules.identity.usecase.RefreshSession;
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
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        var result = login.execute(request);
        if (result.requiresPasswordChange()) {
            return new LoginResponse(true);
        }
        AuthCookies.write(response, result.tokens(), properties);
        return new LoginResponse(false);
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
        AuthCookies.clearSession(response, properties);
    }
}
