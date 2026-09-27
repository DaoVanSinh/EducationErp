package com.eduerp.identity;

import com.eduerp.identity.dto.LoginRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService blacklist;
    private final IdentityProperties properties;

    AuthController(AccountRepository accounts, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
            TokenBlacklistService blacklist, IdentityProperties properties) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.blacklist = blacklist;
        this.properties = properties;
    }

    @PostMapping("/login")
    public void login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        var account = accounts.findByEmail(request.email()).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        issueTokenPair(account.getId(), response);
    }

    @PostMapping("/refresh")
    public void refresh(@CookieValue(IdentityConstants.Cookies.REFRESH_TOKEN) String refreshToken, HttpServletResponse response) {
        var decoded = jwtTokenService.verify(refreshToken);
        if (!IdentityConstants.TokenTypes.REFRESH.equals(decoded.type())) {
            throw new TokenInvalidException("Token này không phải refresh token");
        }
        if (blacklist.isBlacklisted(decoded.jti())) {
            throw new TokenInvalidException("Refresh token đã bị thu hồi");
        }
        blacklist.blacklist(decoded.jti(), properties.refreshTokenTtl());
        issueTokenPair(decoded.accountId(), response);
    }

    @PostMapping("/logout")
    public void logout(@CookieValue(IdentityConstants.Cookies.ACCESS_TOKEN) String accessToken,
            @CookieValue(value = IdentityConstants.Cookies.REFRESH_TOKEN, required = false) String refreshToken,
            HttpServletResponse response) {
        blacklist.blacklist(jwtTokenService.verify(accessToken).jti(), properties.accessTokenTtl());
        if (refreshToken != null) {
            blacklist.blacklist(jwtTokenService.verify(refreshToken).jti(), properties.refreshTokenTtl());
        }
        AuthCookies.clear(response, IdentityConstants.Cookies.ACCESS_TOKEN);
        AuthCookies.clear(response, IdentityConstants.Cookies.REFRESH_TOKEN);
    }

    private void issueTokenPair(UUID accountId, HttpServletResponse response) {
        var access = jwtTokenService.issueAccessToken(accountId);
        var refresh = jwtTokenService.issueRefreshToken(accountId);
        blacklist.trackSession(accountId, access.jti(), properties.accessTokenTtl());
        AuthCookies.set(response, IdentityConstants.Cookies.ACCESS_TOKEN, access.token(), properties.accessTokenTtl());
        AuthCookies.set(response, IdentityConstants.Cookies.REFRESH_TOKEN, refresh.token(), properties.refreshTokenTtl());
    }
}
