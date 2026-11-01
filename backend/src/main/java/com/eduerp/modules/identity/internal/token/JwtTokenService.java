package com.eduerp.modules.identity.internal.token;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityProperties;
import com.eduerp.modules.identity.TokenInvalidException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT "gọn": chỉ {@code sub} + {@code jti} + {@code typ} + {@code iat}; quyền không bao giờ nằm trong
 * token. {@code iat} có để buộc đăng xuất thu hồi được mọi token phát hành trước một mốc
 * ({@link SessionRevocationService}).
 */
@Component
public class JwtTokenService {

    private static final String TYPE_CLAIM = "typ";
    /**
     * Thời điểm phát hành tính bằng MILI-giây. {@code iat} chuẩn của JWT chỉ có giây: thu hồi phiên theo mốc
     * giây thì một lần đăng nhập lại ngay sau khi mở khoá (cùng giây) cũng bị coi là "phát hành trước mốc".
     */
    private static final String ISSUED_AT_MILLIS_CLAIM = "ims";

    private final SecretKey key;
    private final IdentityProperties properties;

    JwtTokenService(IdentityProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issueAccessToken(UUID accountId) {
        return issue(accountId, properties.accessTokenTtl(), IdentityConstants.TokenTypes.ACCESS);
    }

    public IssuedToken issueRefreshToken(UUID accountId) {
        return issue(accountId, properties.refreshTokenTtl(), IdentityConstants.TokenTypes.REFRESH);
    }

    public DecodedToken verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String type = claims.get(TYPE_CLAIM, String.class);
            Long issuedAtMillis = claims.get(ISSUED_AT_MILLIS_CLAIM, Long.class);
            return new DecodedToken(UUID.fromString(claims.getSubject()), claims.getId(),
                    claims.getExpiration().toInstant(), type,
                    issuedAtMillis == null ? null : Instant.ofEpochMilli(issuedAtMillis));
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidException("Token không hợp lệ hoặc đã hết hạn");
        }
    }

    private IssuedToken issue(UUID accountId, Duration ttl, String type) {
        String jti = UUID.randomUUID().toString();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        String token = Jwts.builder()
                .subject(accountId.toString())
                .id(jti)
                .claim(TYPE_CLAIM, type)
                .issuedAt(Date.from(issuedAt))
                .claim(ISSUED_AT_MILLIS_CLAIM, issuedAt.toEpochMilli())
                .expiration(Date.from(expiresAt))
                // Ghim HS256: signWith(key) tự nâng lên HS384/HS512 khi khóa dài >= 48/64 byte, và
                // eduerp-mcp (đọc cùng cookie access_token) chỉ chấp nhận HS256 -> đổi độ dài khóa
                // không được làm chatbot báo "phiên đăng nhập không hợp lệ".
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(jti, token, expiresAt);
    }
}
