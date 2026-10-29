package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.IdentityProperties;
import com.eduerp.modules.identity.TokenInvalidException;
import com.eduerp.modules.identity.dto.SessionTokens;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.rules.IdentityRules;
import com.eduerp.modules.identity.internal.token.JwtTokenService;
import com.eduerp.modules.identity.internal.token.SessionRevocationService;
import com.eduerp.modules.identity.internal.token.TokenBlacklistService;
import com.eduerp.modules.identity.internal.token.TokenIssuer;
import org.springframework.stereotype.Service;

/**
 * Không {@code @Transactional}: chỉ đọc một dòng tài khoản (repository tự mở transaction đọc) và ghi
 * Redis, mở transaction DB bao ngoài là vô ích.
 *
 * <p>Kiểm tra trạng thái tài khoản và mốc buộc đăng xuất ở ĐÂY là bắt buộc: refresh token sống tới 30
 * ngày, nên thiếu hai kiểm tra này thì một tài khoản đã bị khoá vẫn tự làm mới phiên được mãi.
 */
@Service
public class RefreshSession {

    private final JwtTokenService tokens;
    private final TokenBlacklistService blacklist;
    private final SessionRevocationService revocations;
    private final TokenIssuer tokenIssuer;
    private final IdentityRules rules;
    private final IdentityProperties properties;
    private final AccountRepository accounts;

    RefreshSession(JwtTokenService tokens, TokenBlacklistService blacklist, SessionRevocationService revocations,
            TokenIssuer tokenIssuer, IdentityRules rules, IdentityProperties properties, AccountRepository accounts) {
        this.tokens = tokens;
        this.blacklist = blacklist;
        this.revocations = revocations;
        this.tokenIssuer = tokenIssuer;
        this.rules = rules;
        this.properties = properties;
        this.accounts = accounts;
    }

    public SessionTokens execute(String refreshToken) {
        var decoded = tokens.verify(refreshToken);
        if (!rules.isRefreshToken(decoded.type())) {
            throw new TokenInvalidException("Token này không phải refresh token");
        }
        if (blacklist.isBlacklisted(decoded.jti()) || revocations.isRevoked(decoded)) {
            throw new TokenInvalidException("Refresh token đã bị thu hồi");
        }
        var canSignIn = accounts.findById(decoded.accountId())
                .map(account -> rules.canSignIn(account.getStatus()))
                .orElse(false);
        if (!canSignIn) {
            throw new TokenInvalidException("Tài khoản không còn hoạt động");
        }
        blacklist.blacklist(decoded.jti(), properties.refreshTokenTtl());
        return tokenIssuer.issuePair(decoded.accountId());
    }
}
