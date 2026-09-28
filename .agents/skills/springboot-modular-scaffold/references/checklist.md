# Pre-Production Checklist

Run this checklist before shipping code to production, and after any significant structural refactoring.

---

## 1. Modular Structure & Boundaries

- [ ] `ApplicationModules.of(...).verify()` passes in CI via `ModularityTests.java`.
- [ ] `spring.modulith.detection-strategy: explicitly-annotated` is configured in `application.yml`.
- [ ] Every domain package under `modules/` and integration under `integrations/` carries `@ApplicationModule` in its `package-info.java`.
- [ ] `ModularityTests` asserts the exact list of detected modules (`assertThat(detected).containsExactlyInAnyOrder(...)`) to prevent unannotated packages from slipping past verification.
- [ ] No "God Module" exists: `identity` does NOT own audit logs, mail dispatch, branch management, or dashboard metrics.
- [ ] Every table in PostgreSQL has exactly one owning module; no cross-module SQL joins or `@ManyToOne` cross-module entity relationships.
- [ ] Cross-module calls go exclusively through the base package's **Facade** (`<Module>Management.java`) or types exposed via `@NamedInterface`.
- [ ] No `@Repository` is injected into a Controller or another module's classes.
- [ ] No `@Entity` class is returned from a Controller or exposed across module boundaries.
- [ ] No class over ~400–500 lines, no method over cyclomatic complexity 15.
- [ ] One class per use case under `usecase/`, with a single `public` `@Transactional` method named `execute(...)`.
- [ ] No loose constants floating outside static inner classes in `<Module>Constants.java`.
- [ ] Domain rules under `internal/rules/` are pure Java: zero Spring annotations, zero database queries, zero HTTP concepts.

---

## 2. Database & Persistence

- [ ] Every schema change has a corresponding Flyway migration script in `src/main/resources/db/migration/`.
- [ ] `spring.jpa.hibernate.ddl-auto` is set to `validate` in staging and production environments.
- [ ] `spring.jpa.open-in-view` is set to `false`.
- [ ] Foreign keys and queried columns (`WHERE`, `ORDER BY`) have explicit database indexes.
- [ ] HikariCP connection pool is configured (`maximum-pool-size`, `connection-timeout`, `max-lifetime`).
- [ ] All timestamps use `Instant` and are stored in UTC (`timestamptz`).
- [ ] JPA entity `equals()` and `hashCode()` are implemented safely (never referencing lazy-loaded collections).

---

## 3. Caching & Redis

- [ ] All Redis keys are assembled exclusively via `integrations.cache.CacheKeyBuilder`.
- [ ] Cache namespaces are owned by their respective modules (`<Module>Constants.CacheNamespaces`).
- [ ] Invalidation occurs **strictly after database commit** via `TransactionSynchronizationManager.registerSynchronization(afterCommit(...))`.
- [ ] Deserialization errors on cached payloads are logged as warnings and treated as cache misses, never raising 500 errors.
- [ ] Every cached key has an explicit TTL (never unbounded keys).
- [ ] Redis maxmemory policy is configured (`allkeys-lru`).
- [ ] Application degrades gracefully if Redis becomes temporarily unreachable.

---

## 4. Messaging & Asynchronous Events

- [ ] Cross-module reactions use in-process `ApplicationEventPublisher` and `@ApplicationModuleListener`.
- [ ] Outbox persistence (`spring-modulith-starter-jdbc`) is enabled if losing an event has business consequences.
- [ ] Every RabbitMQ queue has a configured Dead-Letter Exchange (DLX) and Dead-Letter Queue (DLQ).
- [ ] Consumers are idempotent, verified against replayed messages.
- [ ] Graceful shutdown timeout is configured (`spring.lifecycle.timeout-per-shutdown-phase: 30s`).

---

## 5. Security & RBAC

- [ ] Method security is enabled (`@EnableMethodSecurity(prePostEnabled = true)`).
- [ ] Code checks capabilities (`hasAuthority('PERM:RESOURCE:ACTION')`), never hardcoded role names (`hasRole('ADMIN')`).
- [ ] Permission checks are scoped (Personal, Branch, Organization).
- [ ] Effective permissions are cached in Redis and evicted immediately when user roles or groups change.
- [ ] Sensitive passwords and secrets are stored in environment variables, never committed to git.
- [ ] Cookie session tokens are configured with `HttpOnly`, `SameSite=Lax/Strict`, and `Secure=true` in production.

---

## 6. API & HTTP Contracts

- [ ] All JSON wire contracts use strict `camelCase`.
- [ ] All error responses adhere to the RFC 9457 `ProblemDetail` specification.
- [ ] Domain errors carry stable, machine-readable `errorCode` strings for frontend translation.
- [ ] List endpoints accept `Pageable` and return `PageResponse<T>`.
- [ ] Request parameters and body payloads are validated using Bean Validation (`@Valid`, `@NotBlank`, etc.).

---

## 7. Observability & Operations

- [ ] Logs are structured JSON in staging and production.
- [ ] `RequestCorrelationFilter` binds `requestId` and `correlationId` to SLF4J's MDC on every request.
- [ ] Response headers echo back `X-Request-ID` and `X-Correlation-ID`.
- [ ] Sensitive fields (passwords, tokens, authorization headers) are redacted from logs.
- [ ] Kubernetes liveness and readiness health probes (`/actuator/health/liveness`, `/actuator/health/readiness`) are exposed and operational.
