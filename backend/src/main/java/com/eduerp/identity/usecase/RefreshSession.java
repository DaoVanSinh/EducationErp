package com.eduerp.identity.usecase;

import com.eduerp.identity.IdentityProperties;
import com.eduerp.identity.TokenInvalidException;
import com.eduerp.identity.dto.SessionTokens;
import com.eduerp.identity.internal.rules.IdentityRules;
import com.eduerp.identity.internal.token.JwtTokenService;
import com.eduerp.identity.internal.token.TokenBlacklistService;
import com.eduerp.identity.internal.token.TokenIssuer;
import org.springframework.stereotype.Service;

/** Không {@code @Transactional}: use case này chỉ chạm Redis, mở transaction DB là vô ích. */
@Service
public class RefreshSession {

    private final JwtTokenService tokens;
    private final TokenBlacklistService blacklist;
    private final TokenIssuer tokenIssuer;
    private final IdentityRules rules;
    private final IdentityProperties properties;

    RefreshSession(JwtTokenService tokens, TokenBlacklistService blacklist, TokenIssuer tokenIssuer,
            IdentityRules rules, IdentityProperties properties) {
        this.tokens = tokens;
        this.blacklist = blacklist;
        this.tokenIssuer = tokenIssuer;
        this.rules = rules;
        this.properties = properties;
    }

    public SessionTokens execute(String refreshToken) {
        var decoded = tokens.verify(refreshToken);
        if (!rules.isRefreshToken(decoded.type())) {
            throw new TokenInvalidException("Token này không phải refresh token");
        }
        if (blacklist.isBlacklisted(decoded.jti())) {
            throw new TokenInvalidException("Refresh token đã bị thu hồi");
        }
        blacklist.blacklist(decoded.jti(), properties.refreshTokenTtl());
        return tokenIssuer.issuePair(decoded.accountId());
    }
}
