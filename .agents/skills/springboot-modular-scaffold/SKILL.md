---
name: springboot-modular-scaffold
description: Scaffold and extend production-grade modular Spring Boot 3.4+ / Java 21 projects with PostgreSQL, Redis, RabbitMQ and object storage, following the Spring Modulith convention where every module owns its entities, config, exceptions, rules, repository and facade, and every integration is a module rather than a flat utility package. Use whenever the user asks to start a new Spring Boot backend, set up or review a modular Spring Boot structure, refactor an existing Spring Boot codebase, decide where constants, exceptions, rules or utils should live, avoid God modules, wire up Redis caching or RabbitMQ/outbox messaging, or asks how to organize a large Java enterprise backend.
---

# Spring Boot Modular Scaffold

Build backends that survive growth. The organizing idea, powered officially by **Spring Modulith** (docs.spring.io/spring-modulith), is that **a module owns everything it needs**: its JPA entities, its enums and error codes, its `@ConfigurationProperties`, its sealed exceptions, its pure business rules, and its public facade. The application root holds mechanism only — the `@SpringBootApplication` bootstrap class and cross-cutting infrastructure — and no business concept at all.

This inverts the instinct to create a `common/` or `util/` package that accumulates everything, becomes imported by every module, and creates unmaintainable cyclic dependencies.

**Baseline assumption**: Java 21+, Spring Boot 3.4+, Maven, single deployable JAR with Spring Modulith managing package-level boundaries verified at build time.

---

## When to do what

| Situation | Action |
|---|---|
| New project bootstrap | Run `curl https://start.spring.io/starter.zip ...` then see `references/architecture.md` |
| Add a domain module | `python scripts/scaffold.py --add-module <name> --package <pkg>` |
| Add cache or messaging | `python scripts/scaffold.py --add-integration <name> --package <pkg>` |
| "Where does this code go?" or "Can this be in `shared/`?" | Read `references/placement.md` |
| God Module warning ("Identity is doing too much") | Read `references/architecture.md#avoiding-the-god-module-trap-when-and-how-to-split` |
| Full worked example of every layer in an advanced module | Read `references/layer-examples.md` |
| Redis caching, versioned keys, post-commit eviction | Read `references/caching.md` |
| RabbitMQ, transactional outbox, `@ApplicationModuleListener` | Read `references/messaging.md` |
| Structured JSON logging, correlation IDs, MDC tracing | Read `references/logging.md` |
| Scoped RBAC, permissions over roles, method security | Read `references/rbac.md` |
| RFC 9457 `ProblemDetail`, error codes, camelCase API | Read `references/api-contract.md` |
| Layered Dockerfile, compose files, Flyway migrations | Read `references/deployment.md` |
| Pre-production verification | Walk `references/checklist.md` |

---

## Generating Modules

Use the provided generator script to scaffold modules with all package-info, constants, exceptions, DTOs, entities, repositories, rules, use cases, and facades pre-wired:

```bash
# Add a domain module (e.g. billing, catalog, enrollment)
python scripts/scaffold.py --add-module billing --package com.eduerp --output ./backend/src/main/java

# Add an integration module
python scripts/scaffold.py --add-integration cache --package com.eduerp --output ./backend/src/main/java
```

---

## The Structure

```
src/main/java/com/eduerp/
├── EduErpApplication.java        @SpringBootApplication + @Modulithic(systemName = "EduERP")
│
├── core/                         shared mechanism — no business concept lives here
│   ├── exception/                AppException base hierarchy, GlobalExceptionHandler -> ProblemDetail
│   ├── web/                      RequestCorrelationFilter (requestId, correlationId -> MDC + headers)
│   ├── config/                   CorsConfig, JacksonConfig, OpenApiConfig — app-wide settings only
│   └── pagination/               PageResponse<T> wrapper
│
├── modules/                      domain modules — grouping folder (requires explicitly-annotated detection)
│   ├── identity/                 ONLY accounts, credentials, auth sessions, profile
│   │   ├── package-info.java       @ApplicationModule(displayName = "Identity & Access")
│   │   ├── IdentityManagement.java the facade — the ONLY type other modules may inject/call
│   │   ├── IdentityConstants.java  enums, limits, namespaces — grouped inside static inner classes
│   │   ├── IdentityProperties.java @ConfigurationProperties(prefix = "identity")
│   │   ├── IdentityException.java  sealed base + AccountNotFoundException, EmailAlreadyExistsException
│   │   ├── IdentityEvents.java     domain event records published via ApplicationEventPublisher
│   │   ├── dto/                    request/response records; @NamedInterface("dto")
│   │   ├── usecase/                one class = one use case, public @Transactional on execute()
│   │   ├── web/                    HTTP controllers, cookie helpers, auth filters
│   │   └── internal/               implementation hidden by Spring Modulith
│   │       ├── model/              JPA @Entity classes; only this module queries these tables
│   │       ├── repository/         Spring Data JpaRepository interfaces
│   │       ├── rules/              pure business decisions, zero I/O — 100% unit-testable with `new`
│   │       └── util/               pure data transforms (EmailNormalizer)
│   │
│   ├── access/                   RBAC: roles, permissions, permission groups, assignments
│   ├── organization/             Branches, departments, campus hierarchy
│   ├── audit/                    Audit trail (consumes domain events via @ApplicationModuleListener)
│   └── billing/ ...              same shape, one package per domain module
│
├── shared/                       only once 3+ modules need the same concept — rule #16
│   ├── SharedConstants.java      promoted enums, static inner classes
│   └── package-info.java         one-way shared module
│
└── integrations/                 adapters to outside infrastructure
    ├── cache/                    Redis: CacheConfig, CacheProperties, CacheKeyBuilder
    ├── messaging/                RabbitMQ: RabbitTopologyConfig, EventRelay
    └── storage/                  S3: S3ClientConfig, S3StorageService

src/test/java/com/eduerp/         mirrors src/main 1:1
├── ModularityTests.java          ApplicationModules.of(...).verify() — mandatory in CI
├── modules/identity/
│   ├── internal/rules/           IdentityRulesTest — pure JUnit 5, instant
│   ├── usecase/                  RegisterAccountTest — Mockito test, no database
│   └── web/                      AccountControllerIT — MockMvc integration test
```

---

## Placement Rules

| Code | Goes to |
|---|---|
| `AccountStatus`, `Limits.MAX_EMAIL_LENGTH`, error codes | `<module>/<Module>Constants.java` — nested static classes |
| `AccountNotFoundException`, `EmailAlreadyExistsException` | `<module>/<Module>Exception.java` (sealed hierarchy) |
| `AppException`, `ConflictException` base classes | `core/exception/`, published via `@NamedInterface("exception")` |
| `identity.jwt-secret`, `identity.access-token-ttl` | `<module>/<Module>Properties.java`, `@ConfigurationProperties(prefix = "...")` |
| `spring.datasource.*`, `server.port`, CORS | `application.yml` root, bound in `core/config/` |
| `canAuthenticateAccount()`, `canModify()` — a decision | `<module>/internal/rules/<Module>Rules.java` — pure Java, no I/O |
| `normalizeEmail()`, `formatPhone()` — a transform | `<module>/internal/util/` — pure transform, no decisions |
| `ProblemDetail`, `PageResponse<T>` | `core/`, as mechanism |
| `JpaRepository<T, ID>` sub-interface | `<module>/internal/repository/` — persistence port |
| Redis key assembly (`key(namespace, id)`) | `integrations/cache/CacheKeyBuilder.java` |
| Redis key namespaces (`perm:account`, `blacklist:jti`) | `<module>/<Module>Constants.CacheNamespaces` |
| An enum needed by 3+ modules (`PermissionScope`) | `shared/SharedConstants.java` — promotion only (Rule of Three) |

---

## Non-Negotiable Rules

**1. Modules reach each other only through the Facade.** Other modules inject `<Module>Management.java` or consume types exposed via `@NamedInterface`. Never import `<module>.internal.*`, an entity, or a repository directly. Spring Modulith enforces this at build time via `ApplicationModules.of(...).verify()`.

**2. Cross-module references name the module.** Import `com.eduerp.modules.identity.IdentityManagement`, never wildcard or obscured imports.

**3. One table has exactly one owning module.** Cross-module reads go through the facade. No JPA `@ManyToOne` across module boundaries; no cross-module SQL joins. Compose in the use case.

**4. The root (`core/`, application class) holds mechanism, never a business concept.**

**5. Cache keys are assembled only in `integrations/cache/CacheKeyBuilder`.** Key namespaces are owned by the domain (`<Module>Constants.CacheNamespaces`).

**6. Invalidate cache after database commit, never before.** Wrap cache eviction in `TransactionSynchronizationManager.registerSynchronization(afterCommit(...))` so uncommitted rollbacks never leave stale cache entries.

**7. Cache entities or IDs, not cross-module join results.** Composing two cached reads beats caching a multi-table blob that creates cascading invalidation bugs.

**8. Connection pools live in Spring Boot / HikariCP config.** Size pools responsibly; do not create ad-hoc database connections.

**9. One use case = one class with one public `execute()` method, `@Transactional` on that method.** Spring proxy-based AOP requires `public` visibility; a package-private `@Transactional` method will silently fail to open a transaction.

**10. Controllers hold no business logic.** Controllers translate HTTP requests to use-case calls and map responses. Domain exceptions map to RFC 9457 `ProblemDetail` centrally in `GlobalExceptionHandler`.

**11. Configuration property prefixes mirror the module's folder name.** `modules/identity/` binds `identity.*`, `integrations/cache/` binds `cache.*`. Never use vendor names (`redis.*`, `s3.*`) for module-level properties.

**12. No class over ~400–500 lines, no method over cyclomatic complexity 15.** Split use cases, controllers, and repositories into focused single-responsibility classes.

**13. Avoid God Modules.** Never cram unrelated domains into `identity`. Keep `identity` strictly focused on accounts, credentials, and auth sessions. Extract audit to `modules.audit`, roles/permissions to `modules.access`, branches to `modules.organization`, mail to `integrations.mail`, and dashboard metrics to `modules.dashboard`.

**14. Intra-module package tiers are strictly one-way.** Tier 0 (constants/properties) → Tier 1 (exceptions/dto/events/model) → Tier 2 (rules/util) → Tier 3 (repository) → Tier 4 (usecase) → Tier 5 (web) → Tier 6 (facade). No circular imports within a module.

**15. No bare constants or loose floating helper methods.** Group constants, limits, authorities, and namespaces into named static inner classes inside `<Module>Constants.java`.

**16. `shared/` is the only sanctioned shared module, and it is strictly one-way.** Promotion only (Rule of Three: 3+ modules need it, it is stable, and divergence would be a bug). `shared` may never depend on a domain module.

**17. Cross-module asynchronous reactions use `ApplicationEventPublisher` and `@ApplicationModuleListener`.** Listeners run asynchronously after transaction commit, backed by the Transactional Outbox (`spring-modulith-starter-jdbc`).

**18. Every project verifies boundaries in CI with `ApplicationModules.of(...).verify()`.** Configure `spring.modulith.detection-strategy: explicitly-annotated` in `application.yml` and pin detected modules in test assertions.

---

## Reference Files

- [architecture.md](references/architecture.md) — Dependency direction, avoiding God modules, package tiers, Spring Modulith detection, transaction boundaries.
- [placement.md](references/placement.md) — Where each kind of code belongs, decision matrix, rules vs utils, sealed exceptions, integrations as modules.
- [layer-examples.md](references/layer-examples.md) — Real Java 21 / Spring Boot 3.4 worked code for every layer: entity, rules, usecase, facade, controller, tests.
- [caching.md](references/caching.md) — Redis cache keys, versioned counters, post-commit invalidation, stampede singleflight, graceful degradation.
- [messaging.md](references/messaging.md) — Spring Modulith Event Publication Registry, Transactional Outbox, RabbitMQ AMQP topology, dead-letter exchanges, idempotent consumers.
- [logging.md](references/logging.md) — RequestCorrelationFilter, MDC tracking, structured JSON formatting, correlation propagation, sensitive data redaction.
- [rbac.md](references/rbac.md) — Scoped RBAC, permission catalog, method security, SpEL evaluation, effective permission caching.
- [api-contract.md](references/api-contract.md) — CamelCase wire format, RFC 9457 ProblemDetail, GlobalExceptionHandler, PageResponse, Server-Sent Events.
- [deployment.md](references/deployment.md) — Layered Dockerfile, compose.yaml, env variable matrix, Flyway migrations, HikariCP pool sizing, Actuator health probes.
- [checklist.md](references/checklist.md) — Pre-production checklist covering modular structure, database, cache, messaging, security, API contracts, and observability.

---

## Verify Before Handing Over

```bash
# Verify modular boundaries (ArchUnit + Spring Modulith)
mvn test -Dtest=ModularityTests

# Run full unit and integration test suite
mvn test

# Verify build package and Docker layertools
mvn clean package -DskipTests
```
