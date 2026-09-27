package com.eduerp.identity.usecase;

import com.eduerp.identity.IdentityProperties;
import com.eduerp.identity.internal.token.JwtTokenService;
import com.eduerp.identity.internal.token.TokenBlacklistService;
import org.springframework.stereotype.Service;

/** Không {@code @Transactional}: chỉ ghi blacklist trên Redis. */
@Service
public class Logout {

    private final JwtTokenService tokens;
    private final TokenBlacklistService blacklist;
    private final IdentityProperties properties;

    Logout(JwtTokenService tokens, TokenBlacklistService blacklist, IdentityProperties properties) {
        this.tokens = tokens;
        this.blacklist = blacklist;
        this.properties = properties;
    }

    public void execute(String accessToken, String refreshToken) {
        blacklist.blacklist(tokens.verify(accessToken).jti(), properties.accessTokenTtl());
        if (refreshToken != null) {
            blacklist.blacklist(tokens.verify(refreshToken).jti(), properties.refreshTokenTtl());
        }
    }
}
