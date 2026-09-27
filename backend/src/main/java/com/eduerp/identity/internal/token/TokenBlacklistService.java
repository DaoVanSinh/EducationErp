package com.eduerp.identity.internal.token;

import com.eduerp.infra.cache.IdentityCacheKeys;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Thu hồi token theo {@code jti} — phần bù bắt buộc của JWT stateless. */
@Service
public class TokenBlacklistService {

    private static final Duration REVOKE_ALL_TTL = Duration.ofDays(31);

    private final StringRedisTemplate redis;

    TokenBlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void blacklist(String jti, Duration ttl) {
        redis.opsForValue().set(IdentityCacheKeys.blacklistedJti(jti), "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(IdentityCacheKeys.blacklistedJti(jti)));
    }

    public void trackSession(UUID accountId, String jti, Duration ttl) {
        String key = IdentityCacheKeys.accountSessions(accountId);
        redis.opsForSet().add(key, jti);
        redis.expire(key, ttl);
    }

    public void blacklistAllActiveSessions(UUID accountId) {
        String key = IdentityCacheKeys.accountSessions(accountId);
        Set<String> jtis = redis.opsForSet().members(key);
        if (jtis == null) {
            return;
        }
        jtis.forEach(jti -> blacklist(jti, REVOKE_ALL_TTL));
        redis.delete(key);
    }
}
