# Messaging Reference

## When a Message Broker is Warranted

RabbitMQ or Kafka earns its operational cost when:
1. Work must not block HTTP requests (sending emails, background document generation, video transcode).
2. Work must survive application restarts and pod crashes.
3. Multiple bounded contexts need to react asynchronously to domain events without direct compile-time coupling.

For simple synchronous cross-module reactions, Spring's in-process `ApplicationEventPublisher` suffices.

---

## Where This Lives

```
integrations/messaging/
├── RabbitTopologyConfig.java    // Exchanges, bindings, DLX, DLQ declarations
├── MessagingProperties.java     // messaging.* properties (queue names, prefixes)
├── EventOutboxRelay.java        // Outbox publisher to external broker
└── package-info.java            // @ApplicationModule(displayName = "Messaging Integration")
```

Domain modules define their own event records in `<module>/<Module>Events.java`. They never import AMQP or broker-specific libraries directly.

---

## Spring Modulith Event Publication Registry (Transactional Outbox)

Committing to PostgreSQL and publishing to RabbitMQ are two distinct distributed systems with no shared transaction. If the app crashes between the DB commit and broker publish, the message is permanently lost.

Spring Modulith provides a built-in **Transactional Outbox** via `spring-modulith-starter-jdbc` (or starter-jpa):

```xml
<dependency>
  <groupId>org.springframework.modulith</groupId>
  <artifactId>spring-modulith-starter-jdbc</artifactId>
</dependency>
```

### How It Operates
1. In the use case, call `events.publishEvent(new IdentityEvents.AccountRegistered(...))`.
2. Spring Modulith intercepts the event and writes it to the `event_publication` table in PostgreSQL **within the same database transaction**.
3. When the transaction commits, Spring Modulith invokes listeners annotated with `@ApplicationModuleListener`:
   ```java
   @ApplicationModuleListener
   void on(IdentityEvents.AccountRegistered event) {
       // Executed asynchronously in a new transaction after the publishing transaction commits!
   }
   ```
4. Once the listener completes without error, Spring Modulith marks the publication as completed in `event_publication`.
5. If the pod crashes before the listener completes, Spring Modulith automatically replays uncompleted publications upon application startup!

---

## External Broker Externalization (RabbitMQ)

To forward Spring Modulith events directly to RabbitMQ, add `spring-modulith-events-amqp`:

```xml
<dependency>
  <groupId>org.springframework.modulith</groupId>
  <artifactId>spring-modulith-events-amqp</artifactId>
</dependency>
```

Annotate the event record with `@Externalized`:

```java
public final class IdentityEvents {

    @Externalized("eduerp.identity::identity.account.registered")
    public record AccountRegistered(
        UUID accountId,
        String email,
        String fullName,
        Instant occurredAt
    ) {}
}
```

Spring Modulith routes the event to exchange `eduerp.identity` with routing key `identity.account.registered` automatically through its outbox relay!

---

## AMQP Topology: Topic Exchanges & Dead-Letter Queues (DLQ)

Follow standard naming conventions:
- **Exchanges**: Topic exchanges named `<app>.<module>` (e.g. `eduerp.identity`).
- **Routing Keys**: `<module>.<entity>.<action>` (e.g. `identity.account.registered`, `identity.account.suspended`).
- **Queues**: Owned by consumers, named `q.<consumer-module>.<intent>` (e.g. `q.mail.welcome_email`, `q.audit.account_events`).

### DLQ Declaration
Every queue MUST have a dead-letter exchange configured at creation time:

```java
@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange identityExchange() {
        return new TopicExchange("eduerp.identity", true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("eduerp.dlx", true, false);
    }

    @Bean
    public Queue welcomeEmailQueue() {
        return QueueBuilder.durable("q.mail.welcome_email")
            .withArgument("x-dead-letter-exchange", "eduerp.dlx")
            .withArgument("x-dead-letter-routing-key", "mail.welcome_email.dlq")
            .build();
    }

    @Bean
    public Queue welcomeEmailDlq() {
        return QueueBuilder.durable("q.mail.welcome_email.dlq").build();
    }
}
```

---

## Idempotent Consumers

At-least-once delivery guarantees that consumers will occasionally receive duplicate messages. Consumers must be idempotent.

### Strategy 1: Natural Idempotency
Design operations so repeating them produces identical state:
- Safe: `UPDATE accounts SET status = 'ACTIVE' WHERE id = :id`
- Unsafe: `UPDATE accounts SET balance = balance + :amount`

### Strategy 2: Redis Idempotency Guard
For operations that cannot be made naturally idempotent, record processed message IDs:

```java
@Component
public class WelcomeEmailListener {

    private final StringRedisTemplate redis;
    private final MailService mailService;

    @RabbitListener(queues = "q.mail.welcome_email")
    public void handle(AccountRegisteredEvent event) {
        String idempotencyKey = "idemp:mail:welcome:" + event.accountId();
        Boolean isFirstDelivery = redis.opsForValue()
            .setIfAbsent(idempotencyKey, "1", Duration.ofDays(7));

        if (Boolean.FALSE.equals(isFirstDelivery)) {
            log.info("Message for account {} already processed. Skipping.", event.accountId());
            return;
        }

        mailService.sendWelcomeEmail(event.email(), event.fullName());
    }
}
```

---

## Graceful Consumer Shutdown

On receiving `SIGTERM`, message listeners must complete in-flight messages before closing connections:

```yaml
# application.yml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: false
```
