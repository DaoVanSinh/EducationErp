# Caching and messaging

## Redis caching

Own the client config and the key scheme the same way `infra/cache` owns them in `fastapi-modular-scaffold` — a module never builds its own key strings.

```java
package com.acme.shop.infra.cache;

@ConfigurationProperties(prefix = "cache")
public record CacheProperties(Duration defaultTtl) {}

@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory cf, CacheProperties props) {
        var defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(props.defaultTtl())
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        return RedisCacheManager.builder(cf).cacheDefaults(defaults).build();
    }
}
```

`cache.default-ttl` env var is `CACHE_DEFAULT_TTL` — the module-prefix rule (rule #9) applies to infra packages too.

**Keys.** One class per cached entity, still owned by `infra/cache`, fed the entity name by the module that owns it — never hardcode the module's name inside the cache package itself:

```java
package com.acme.shop.infra.cache;

public final class IdentityCacheKeys {
    private IdentityCacheKeys() {}
    public static String user(UUID id) { return "identity:user:" + id; }
}
```

**Invalidate after commit, never before** — the same rule as `fastapi-modular-scaffold`'s caching.md, for the same reason: bumping the key first lets a concurrent reader repopulate the cache from pre-commit state. `@CacheEvict` on a `@Transactional` method does **not** reliably give you this — Spring's cache and transaction advisors are two separate pieces of AOP advice with no defined ordering between them by default, so whether the eviction actually lands before or after the underlying commit is unspecified, not guaranteed. Register a `TransactionSynchronization` in `afterCommit()` instead — it's the only mechanism that's actually anchored to the transaction outcome:

```java
@Transactional
public void execute(UUID id) {
    // ... the write ...
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() {
            cacheManager.getCache("identity-user").evict(id);
        }
    });
}
```

Reach for the plain `@CacheEvict` annotation only on methods that aren't wrapped in a wider transaction to begin with (nothing to race against); once `@Transactional` is on the same method, use `afterCommit()`.

**Cache entities, not join results.** A cached `user+tenant` blob has two invalidation triggers and one will be missed — cache `user` and `tenant` separately and compose at read time.

## RabbitMQ and the outbox

Add `spring-boot-starter-amqp` for plain queue/consumer work, and `spring-modulith-starter-jdbc` + `spring-modulith-events-amqp` when domain events need to survive past an in-process `ApplicationEventPublisher` call — i.e. when losing an event has business consequences (see "Deciding how far to go" in `SKILL.md`).

**Own topology in the infra package**, not scattered `@RabbitListener` annotations with inline exchange names:

```java
package com.acme.shop.infra.messaging;

@Configuration
class MessagingTopology {
    @Bean Exchange identityExchange() { return ExchangeBuilder.topicExchange("identity.events").durable(true).build(); }
    @Bean Queue billingInboxQueue() { return QueueBuilder.durable("billing.identity-events").build(); }
    @Bean Binding binding() {
        return BindingBuilder.bind(billingInboxQueue()).to(identityExchange()).with("identity.user.registered");
    }
}
```

**The outbox, without hand-writing one.** Spring Modulith's event publication registry *is* the transactional outbox: once `spring-modulith-starter-jdbc` is on the classpath, every `ApplicationEventPublisher.publishEvent(...)` call inside a `@Transactional` method is persisted to an `EVENT_PUBLICATION` table in the same transaction as the business write, and only marked complete after every `@ApplicationModuleListener` (in-process) or externalized listener (via `spring-modulith-events-amqp`, out to RabbitMQ) has processed it. A crash between commit and publish replays on restart — the same guarantee `fastapi-modular-scaffold`'s hand-rolled outbox gives, with no bespoke polling table to write or maintain.

```yaml
spring:
  modulith:
    events:
      completion-mode: archive   # keep processed events instead of deleting, for audit/replay
```

**Idempotent consumers.** The outbox guarantees at-least-once delivery, not exactly-once — a listener can still see the same event twice after a crash-and-retry. Make the listener idempotent by checking a `processed_event_id` unique constraint (or the target write itself being naturally idempotent, e.g. an upsert) before acting, the same requirement as `fastapi-modular-scaffold`'s messaging.md:

```java
@ApplicationModuleListener
void on(IdentityEvents.UserRegistered event) {
    if (processedEvents.existsById(event.eventId())) return;
    // do the work, then processedEvents.save(new ProcessedEvent(event.eventId()));
}
```

**Retry.** `@RabbitListener` methods get backoff via `spring-retry` (`@Retryable(maxAttempts = ..., backoff = @Backoff(...))`) or the listener container's own `RetryTemplate` — reach for the container-level retry first; per-method `@Retryable` is for transient failures inside the use case itself (an external HTTP call, not the message delivery).
