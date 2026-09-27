package com.eduerp.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionCacheService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "perm:account:";

    private final StringRedisTemplate redis;
    private final AccountRepository accounts;
    private final EffectivePermissionCalculator calculator;
    private final ObjectMapper mapper;

    PermissionCacheService(StringRedisTemplate redis, AccountRepository accounts,
            EffectivePermissionCalculator calculator, ObjectMapper mapper) {
        this.redis = redis;
        this.accounts = accounts;
        this.calculator = calculator;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Set<EffectivePermission> getEffectivePermissions(UUID accountId) {
        String key = KEY_PREFIX + accountId;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return deserialize(cached);
        }
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        var computed = calculator.calculate(account.getRole(), account.getGroups());
        redis.opsForValue().set(key, serialize(computed), TTL);
        return computed;
    }

    public void evict(UUID accountId) {
        redis.delete(KEY_PREFIX + accountId);
    }

    private String serialize(Set<EffectivePermission> permissions) {
        try {
            return mapper.writeValueAsString(permissions);
        } catch (Exception e) {
            throw new IllegalStateException("Không serialize được permission set", e);
        }
    }

    private Set<EffectivePermission> deserialize(String json) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(Set.class, EffectivePermission.class));
        } catch (Exception e) {
            throw new IllegalStateException("Không deserialize được permission set", e);
        }
    }
}
