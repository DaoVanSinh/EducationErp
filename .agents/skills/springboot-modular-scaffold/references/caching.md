# Caching Reference

## Where This Lives

Caching infrastructure is encapsulated in a dedicated integration module: `com.eduerp.integrations.cache`.

```
integrations/cache/
├── CacheConfig.java             // RedisTemplate<String, Object> & serializers
├── CacheProperties.java         // cache.* configuration properties
├── CacheKeyBuilder.java         // The single class where key strings are assembled
└── package-info.java            // @ApplicationModule(displayName = "Cache Integration")
```

Key construction is strictly centralized in `CacheKeyBuilder`. Domain modules supply their business entity namespace via their own `<Module>Constants.CacheNamespaces`.

Nothing else in the codebase builds a raw Redis key string.

---

## Centralized Key Assembly (`CacheKeyBuilder.java`)

```java
package com.eduerp.integrations.cache;

public final class CacheKeyBuilder {

    private static final String SEPARATOR = ":";
    private static final String WILDCARD = "*";

    private CacheKeyBuilder() {}

    public static String key(String namespace, Object identifier) {
        return namespace + SEPARATOR + identifier;
    }

    public static String pattern(String namespace) {
        return namespace + SEPARATOR + WILDCARD;
    }

    public static String identifierIn(String namespace, String key) {
        return key.substring(namespace.length() + SEPARATOR.length());
    }
}
```

Usage in domain modules:
```java
// Correct: Module supplies its namespace, CacheKeyBuilder formats the key
String key = CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.EFFECTIVE_PERMISSIONS, accountId);

// WRONG: Hardcoded string concatenation scattered in service files
String key = "perm:account:" + accountId; 
```

---

## Versioned Keys (Generational Invalidation)

Deleting batches of derived keys requires scanning or maintaining secondary indexes. Generational versioning avoids key enumeration completely:

```
ver:account:101           -> 5
account:101:v5:s1         -> { "email": "user@eduerp.local", ... }
```

1. To invalidate, increment the version counter: `INCR ver:account:101`.
2. All existing cache entries for generation 5 become immediately unreachable in $O(1)$ time.
3. Abandoned generation entries expire naturally according to their standard TTL.
4. The `s1` suffix represents the schema serialization version. Bump it when changing the DTO class structure to avoid deserialization errors during rolling deployments.

---

## Order of Operations: Invalidate AFTER Commit

**Never invalidate cache before the database transaction commits.**

If you invalidate or delete a cache key before `commit()`:
1. Thread A updates DB and deletes the cache key.
2. Thread B executes a read query before Thread A commits. Thread B finds a cache miss, reads the **old** data from Postgres, and stores the old data back into Redis.
3. Thread A commits.
4. Redis now contains stale data until TTL expires!

### Spring Transaction Synchronization
Always execute cache invalidation in the `afterCommit` phase of the transaction:

```java
@Transactional
public void updateAccountRole(UUID accountId, UUID roleId) {
    accountRepository.updateRole(accountId, roleId);

    // Register post-commit cache eviction
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            cacheService.evict(CacheKeyBuilder.key(
                IdentityConstants.CacheNamespaces.EFFECTIVE_PERMISSIONS, accountId
            ));
        }
    });
}
```

---

## Cache Stampede Protection (Singleflight)

When a hot key expires under heavy traffic, thousands of concurrent requests miss and hammer the database simultaneously.

### Strategy 1: Distributed Lock
Use a Redis lock (`SET lock:<key> <token> NX PX 3000`) or Redisson lock. The winning thread loads from the database and populates the cache; other threads wait briefly and re-read from the cache.

### Strategy 2: L1 (In-Memory Caffeine) + L2 (Redis)
Combine a local near-cache (Caffeine, 5–30 seconds TTL) with a distributed Redis cache (10–60 minutes TTL). Local Caffeine absorbs 99% of hot-key traffic within the JVM instance.

---

## Defensive Deserialization

Treat a deserialization error on cached data as a cache miss, **NEVER** throw a 500 error:

```java
public <T> Optional<T> get(String key, Class<T> targetClass) {
    try {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return Optional.empty();
        return Optional.of(objectMapper.readValue(json, targetClass));
    } catch (Exception ex) {
        log.warn("Cache deserialization failed for key {}. Treating as cache miss.", key, ex);
        return Optional.empty();
    }
}
```

During rolling deployments, new pods might read payloads written by old pods or vice versa. Treating errors as misses ensures zero user-facing downtime.

---

## What NOT to Cache

- **Uniqueness checks**: `accountRepository.existsByEmail(...)` before registration must always query the database.
- **In-flight transactional state**: Queries executed within a mutating transaction that depend on uncommitted writes.
- **Safety / Financial / Quota checks**: Money balances, exam submission status, or security tokens requiring immediate consistency.

---

## Graceful Degradation (Cache is Not a Database)

If Redis goes down, the application must degrade gracefully to direct database queries. A Redis outage should cause higher latency, never a total system outage.

Wrap all Redis calls with try-catch fallback to DB:
```java
public Set<String> getPermissions(UUID accountId) {
    try {
        return redisCache.get(accountId);
    } catch (RedisConnectionException ex) {
        log.error("Redis unreachable. Falling back to database.", ex);
        return databasePermissionService.computeEffectivePermissions(accountId);
    }
}
```
