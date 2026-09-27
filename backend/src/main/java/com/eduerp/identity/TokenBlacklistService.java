package com.eduerp.identity;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "blacklist:jti:";
    private static final String SESSIONS_PREFIX = "sessions:account:";

    private final StringRedisTemplate redis;

    TokenBlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void blacklist(String jti, Duration ttl) {
        redis.opsForValue().set(BLACKLIST_PREFIX + jti, "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + jti));
    }

    public void trackSession(UUID accountId, String jti, Duration ttl) {
        String key = SESSIONS_PREFIX + accountId;
        redis.opsForSet().add(key, jti);
        redis.expire(key, ttl);
    }

    public void blacklistAllActiveSessions(UUID accountId) {
        String key = SESSIONS_PREFIX + accountId;
        Set<String> jtis = redis.opsForSet().members(key);
        if (jtis == null) {
            return;
        }
        jtis.forEach(jti -> blacklist(jti, Duration.ofDays(31)));
        redis.delete(key);
    }
}
