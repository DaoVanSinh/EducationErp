package com.eduerp.identity.internal.token;

import com.eduerp.identity.IdentityProperties;
import com.eduerp.identity.dto.SessionTokens;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Phát hành cặp access + refresh và ghi nhận phiên đang hoạt động — dùng chung bởi
 * use case đăng nhập và làm mới phiên, để hai đường không lệch nhau.
 */
@Component
public class TokenIssuer {

    private final JwtTokenService tokens;
    private final TokenBlacklistService blacklist;
    private final IdentityProperties properties;

    TokenIssuer(JwtTokenService tokens, TokenBlacklistService blacklist, IdentityProperties properties) {
        this.tokens = tokens;
        this.blacklist = blacklist;
        this.properties = properties;
    }

    public SessionTokens issuePair(UUID accountId) {
        var access = tokens.issueAccessToken(accountId);
        var refresh = tokens.issueRefreshToken(accountId);
        blacklist.trackSession(accountId, access.jti(), properties.accessTokenTtl());
        return new SessionTokens(access.token(), refresh.token());
    }
}
