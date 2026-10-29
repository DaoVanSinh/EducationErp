package com.eduerp.modules.identity.internal.token;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityProperties;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Buộc đăng xuất mọi phiên của một tài khoản (spec 6.3 Force-logout).
 *
 * <p>Hai lớp: (1) blacklist mọi access token đang được theo dõi để chúng chết ngay ở request kế tiếp;
 * (2) đặt mốc "không trước" - mọi token (kể cả refresh token, thứ không được theo dõi) có {@code iat}
 * trước mốc đều bị từ chối, nên phiên không thể tự làm mới. Mốc và thời điểm phát hành đều tính bằng
 * mili-giây (claim {@code ims}), nên đăng nhập lại ngay sau khi được mở khoá không bị chặn nhầm.
 */
@Service
public class SessionRevocationService {

    private final StringRedisTemplate redis;
    private final TokenBlacklistService blacklist;
    private final IdentityProperties properties;

    SessionRevocationService(StringRedisTemplate redis, TokenBlacklistService blacklist,
            IdentityProperties properties) {
        this.redis = redis;
        this.blacklist = blacklist;
        this.properties = properties;
    }

    public void revokeAll(UUID accountId) {
        long notBefore = Instant.now().toEpochMilli();
        redis.opsForValue().set(key(accountId), Long.toString(notBefore), properties.refreshTokenTtl());
        blacklist.blacklistAllActiveSessions(accountId);
    }

    public boolean isRevoked(DecodedToken token) {
        String notBefore = redis.opsForValue().get(key(token.accountId()));
        if (notBefore == null) {
            return false;
        }
        return token.issuedAt() == null || token.issuedAt().toEpochMilli() < Long.parseLong(notBefore);
    }

    private static String key(UUID accountId) {
        return CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.SESSION_NOT_BEFORE, accountId);
    }
}
