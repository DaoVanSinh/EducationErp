package com.eduerp.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
class JwtTokenService {

    private final SecretKey key;
    private final IdentityProperties properties;

    JwtTokenService(IdentityProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    IssuedToken issueAccessToken(UUID accountId) {
        return issue(accountId, properties.accessTokenTtl());
    }

    IssuedToken issueRefreshToken(UUID accountId) {
        return issue(accountId, properties.refreshTokenTtl());
    }

    DecodedToken verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return new DecodedToken(UUID.fromString(claims.getSubject()), claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidException("Token không hợp lệ hoặc đã hết hạn");
        }
    }

    private IssuedToken issue(UUID accountId, java.time.Duration ttl) {
        String jti = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(ttl);
        String token = Jwts.builder()
                .subject(accountId.toString())
                .id(jti)
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(jti, token, expiresAt);
    }
}
