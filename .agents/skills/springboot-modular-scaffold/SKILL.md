---
name: springboot-modular-scaffold
description: Scaffold and extend production-grade modular Spring Boot projects with PostgreSQL, Redis, RabbitMQ and object storage, following the Spring Modulith convention where every module owns its entities, config, exceptions, rules and a facade, and every integration is a module rather than a flat utility package. Use whenever the user asks to start a new Spring Boot/Java backend, set up or review a Spring Boot project structure, add a module or feature to an existing Spring Boot codebase, decide where constants, exceptions, config or utility code should live, wire up Redis caching or RabbitMQ/outbox messaging, or asks how to organize a large Java backend — even without saying "modular monolith" or "Spring Modulith". The Spring Boot counterpart to fastapi-modular-scaffold; use this one when the stack is Java/Spring instead of Python/FastAPI.
---

# Spring Boot Modular Scaffold

Build backends that survive growth. The organizing idea is the same one this repo already applies to FastAPI, expressed through Spring's own official tooling: **Spring Modulith** (docs.spring.io/spring-modulith), which treats every direct sub-package of the application's base package as an application module with a public API (its base package) and hidden internals (its sub-packages), verified at build time rather than trusted to reviewer discipline.

**A module owns everything it needs**: its `@Entity` classes, its enums and error codes, its `@ConfigurationProperties`, its exceptions, its rules. The application root holds mechanism only — the `@SpringBootApplication` class and cross-cutting infrastructure — and no business concept at all.

This inverts the instinct to create a `common/` or `util/` package that every module imports from. Those packages start small, accumulate everything, and end up imported by every module while importing from several — a new version of the mess the structure was meant to prevent. The one sanctioned exception is a real `shared` module — a promoted concept, not a dumping ground — that a type only enters once it clears the bar in `references/placement.md#when-duplication-is-correct` (needed by three or more modules, stable, and one where divergence would be a bug).

**Baseline assumption** (state the alternative and move on if the project differs): Java 21+, Spring Boot 3.4+, Maven, single deployable jar with Spring Modulith managing package-level module boundaries — not a Maven multi-module reactor with one jar per domain. Spring Modulith's own philosophy is exactly Dispatch's: one deployable, real internal boundaries, split into services later only if you actually need to. A team that already runs a Maven multi-module / polyrepo microservices setup is solving a different problem; say so rather than forcing this structure onto it.

## When to do what

| Situation | Action |
|---|---|
| New project | `references/architecture.md#bootstrapping` (Spring Initializr command), then read the rest of that file |
| Add a domain module | Follow `references/architecture.md#adding-a-module` — no generator script; the module shape is small enough to hand-write correctly once you've read the rules |
| Add Redis, RabbitMQ, S3 | `references/caching-and-messaging.md` |
| "Where does this constant/config/exception go, or does it belong in `shared`?" | Read `references/placement.md` |
| Boundary violation, `ApplicationModules.verify()` failure, or unsure which class in a module may reference which | Read `references/architecture.md#module-boundaries-and-verification` |
| Full worked example of every layer — entity, rules, use case, facade, controller, module test | Read `references/layer-examples.md` |
| Wire caching, fix stale data | `references/caching-and-messaging.md#redis-caching` |
| Add RabbitMQ, outbox, idempotent consumers | `references/caching-and-messaging.md#rabbitmq-and-the-outbox` |
| Wire a new endpoint, decide the error/response shape | `references/api-contract.md` |
| Deploy, set up profiles/env, layered Docker image | `references/deployment.md` |
| Add permissions, roles, method-level access control | `references/architecture.md#authorization` |
| Before shipping | Walk `references/checklist.md` |

Ask what infrastructure the project actually needs before adding it. A message broker added to a project with no async work is weight the team carries forever.

**Works alongside:** if this system also has a Next.js/React frontend, that half is governed by the `nextjs-modular-architecture` skill, not this one — same shape of rules (modular, layered, one-way dependencies, small-file budget), different stack. If this system's backend is FastAPI instead of Spring Boot, use `fastapi-modular-scaffold` instead — the two are deliberately parallel so a team can read either and recognize the shape. After changing code, run `reviewing-code-against-skills` before calling the work done.

## Bootstrapping

```bash
curl https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d bootVersion=3.4.1 \
  -d javaVersion=21 -d groupId=com.acme -d artifactId=shop -d packageName=com.acme.shop \
  -d dependencies=web,data-jpa,postgresql,validation,actuator,modulith \
  -o shop.zip && unzip shop.zip -d shop
```

Add `spring-boot-starter-data-redis` (cache), `spring-modulith-starter-amqp` + `spring-boot-starter-amqp` (queue), and an S3 client (`software.amazon.awssdk:s3`) only when the project actually needs them.

## The structure

```
src/main/java/com/acme/shop/
├── ShopApplication.java        @SpringBootApplication + @Modulithic(systemName = "Shop")
│
├── core/                       shared mechanism — no business concept lives here
│   ├── exception/               AppException hierarchy, GlobalExceptionHandler → ProblemDetail
│   ├── web/                     RequestCorrelationFilter (request/correlation id → MDC + response headers)
│   ├── security/                JWT resource-server config, method-security bootstrap — no role/permission constants
│   └── config/                  CorsConfig, JacksonConfig, OpenApiConfig — app-wide settings only
│
├── identity/                   domain module — owns everything it needs
│   ├── Identity.java             @Entity — only this module's repository queries this table
│   ├── IdentityConstants.java    enums, error codes, limits — nested inside one class (see rule #10)
│   ├── IdentityProperties.java   @ConfigurationProperties(prefix = "identity")
│   ├── IdentityException.java    base + UserNotFoundException, EmailTakenException — concrete errors
│   ├── IdentityRules.java        pure decisions, no I/O, no Spring annotation — unit-testable with `new`
│   ├── dto/                      request/response records + Bean Validation annotations
│   ├── IdentityRepository.java   `interface IdentityRepository extends JpaRepository<Identity, UUID>` — the port
│   ├── usecase/                  one class = one use case, `@Transactional` on its single public method
│   ├── IdentityEvents.java       records implementing a marker event interface
│   ├── IdentityController.java   HTTP surface — thin, delegates to a use case
│   ├── IdentityManagement.java   the facade — the ONLY type other modules may call
│   └── internal/                 everything else — invisible outside the module (Spring Modulith enforced)
│
├── billing/ ...                 same shape, one package per domain module
│
├── shared/                      only once 3+ modules need the same concept — promotion, not a first draft
│   ├── SharedConstants.java      the promoted enum/limit, still one class (rule #10)
│   └── package-info.java         @NamedInterface / no @ApplicationModule(allowedDependencies=...) pointing at a domain module — one-way
│
└── infra/
    ├── cache/                    Redis: CacheConfig, <Entity>CacheKeys — Spring Cache abstraction
    ├── messaging/                RabbitMQ: topology/exchanges/queues, listeners
    └── storage/                  S3 client, config, exceptions — no tables, no HTTP

src/test/java/com/acme/shop/     mirrors src/main 1:1 — Maven/Gradle convention enforces this, not choice
├── ModularityTests.java          ApplicationModules.of(ShopApplication.class).verify() — run in CI
├── identity/
│   ├── IdentityRulesTest.java    plain JUnit, no Spring context at all
│   ├── IdentityUseCaseTest.java  Mockito fake for IdentityRepository — no database
│   └── IdentityControllerIT.java @SpringBootTest + Testcontainers Postgres, real HTTP client
└── billing/ ...

pom.xml, Dockerfile (layertools), compose.yaml (dev), application.yml + application-{profile}.yml
```

An infrastructure package (`infra/cache`, `infra/messaging`, `infra/storage`) is a module like any other. It differs from a domain module only in what it lacks: no `@Entity` because it owns no tables, no `@RestController` because it exposes no HTTP. It still owns its own config, constants and exceptions.

## Placement rules

The recurring question is where a given piece of code belongs. The test is **who owns the concept**, not what shape the code has.

| Code | Goes to |
|---|---|
| `OrderStatus`, `MAX_SEATS`, error codes | `<module>/IdentityConstants.java`, nested inside one class |
| `OrderNotFoundException`, `SeatLimitReachedException` | `<module>/IdentityException.java` (or its own file if it grows) |
| `AppException`, `NotFoundException` base classes | `core/exception/` |
| `jwt.secret`, `cache.ttl` for one module | `<module>/IdentityProperties.java`, `@ConfigurationProperties(prefix = "identity")` |
| `spring.datasource.*`, CORS origins | `application.yml` root, bound in `core/config/` |
| `canCancelOrder()` — a decision | `<module>/IdentityRules.java` — no `@Component` needed, plain object |
| `normalizeEmail()` — a transform | `<module>/util/` — no decisions, one class per concern |
| `ProblemDetail` mapping, pagination wrapper | `core/`, as mechanism (`Page<T>`/`Pageable` are already framework-provided — don't reinvent them) |
| `JpaRepository<T, ID>` subinterface | lives in the module; it *is* the abstraction — no hand-written `Abstract*Repository` needed, see rule #6 |
| A role/permission enum 3+ modules need | `shared/SharedConstants.java` — promotion only, never a first draft |

`core/exception/` naming a domain entity is the earliest visible sign the boundary has leaked. So is any `Utils.java` at the application root.

## Non-negotiable rules

**1. Modules reach each other only through the facade (the module's base-package public types).** Never reach into `<module>.internal` or another module's `@Entity`/`@Repository` from outside. Spring Modulith enforces this at build time via `ApplicationModules.of(ShopApplication.class).verify()` in `ModularityTests.java` — a boundary violation fails the build, not a code review.

**2. Cross-module references name the module.** Import `com.acme.shop.identity.IdentityManagement`, not a static import that hides where it came from. Reading the call site tells you where the type came from.

**3. One table has exactly one owning module's repository.** Cross-module reads go through the facade. No JPA `@ManyToOne` or SQL join across module boundaries; compose in the use case. This costs a query and buys the ability to split the module into its own service later.

**4. The root (`ShopApplication`, `core/`) holds mechanism, never a business concept.**

**5. Cache keys are built only in `infra/cache/<Entity>CacheKeys`.** Each module supplies its entity name via its own constants class — see `references/caching-and-messaging.md`.

**6. Don't hand-write `AbstractRepository`/`AbstractUnitOfWork` — Spring already gives you both, and duplicating them is where this skill deliberately diverges from `fastapi-modular-scaffold`.** `JpaRepository<T, ID>` *is* the repository abstraction (Spring Data proxies it at runtime; a `Fake`/in-memory implementation for tests is just another bean, not a subclass of a hand-rolled base class). `@Transactional` on the use case's single public method *is* the unit of work (`PlatformTransactionManager` commits or rolls back the whole method). Reintroducing an `AbstractUnitOfWork` on top of `@Transactional` buys nothing and gives you two competing transaction boundaries.

**7. One use case = one class with one public method, `@Transactional` at that method.** Growth adds classes, not branches inside a service.

**8. Controllers hold no business logic.** They translate HTTP to a use-case call. Domain exceptions map to HTTP status via `@RestControllerAdvice` centrally — see `references/api-contract.md`.

**9. Config property prefixes mirror the module's package name, never a vendor name.** `infra/cache/` binds `cache.*` (`CACHE_TTL` as env var via Spring's relaxed binding), not `redis.*` — swapping Redis for another backend shouldn't force every deployment's env vars to change. Same rule for every domain module (`identity.*` → `IDENTITY_JWT_SECRET`) and every infra package. Only root, app-wide settings (`spring.datasource.*`, `server.port`, CORS) stay unprefixed by a module name.

**10. No bare `public static final` constant or free-floating utility method scattered across files it isn't the file's job to own.** Java already forces every method into a class, so this rule is nearly free — the discipline is making sure it's the *right* class: `IdentityConstants.MAX_NAME_LENGTH`, not a constant duplicated inline in three use cases; `IdentityRules.canCancelOrder(...)`, not the same `if` chain copy-pasted into two controllers. See rule #16 of `fastapi-modular-scaffold` for the Python-side version of this same discipline.

**11. `internal/` (or package-private visibility in a flat module) is where implementation details live.** A module with no sub-packages: mark everything except the facade class as package-private (no `public` modifier) and Spring Modulith's simple-module rule hides it automatically. A module with sub-packages: put internals under `<module>.internal` and Spring Modulith's advanced-module rule hides that sub-package from everyone but the module itself.

**12. `shared/` is the only sanctioned shared module, and it is one-way.** Every domain module may depend on it (declare it in `sharedModules` on `@Modulithic`, or reference it plainly since Spring Modulith allows unrestricted dependencies on shared modules by design); `shared` itself may never import a domain module — that would recreate the exact cross-module cycle rule #1 forbids, one hop removed. A concept lands here only once it clears the bar in `references/placement.md#when-duplication-is-correct`.

**13. Cross-module communication that shouldn't be a direct call is an application event, not a shared table poke.** Publish via `ApplicationEventPublisher`; listen with `@ApplicationModuleListener` (async, and — once `spring-modulith-starter-jdbc` is on the classpath — backed by the transactional outbox automatically, so "publish" and "commit" can never disagree). See `references/caching-and-messaging.md#rabbitmq-and-the-outbox`.

**14. No class over ~400-500 lines, no method over cyclomatic complexity 15.** Checkstyle/PMD (or SonarQube in CI) enforce the second; the first is a judgment call at review time made the same way as in `fastapi-modular-scaffold`. Split into a sub-package instead of writing a flatter God class.

**15. `@SuppressWarnings` never goes bare.** State what and why: `@SuppressWarnings("unchecked") // Jackson TypeReference erasure, verified by the round-trip test below`. A suppression with no reason is a decision that needs to survive the person who wrote it leaving the team.

## Deciding how far to go

Match the ceremony to the size — over-structuring is as damaging as under-structuring and much easier to do by accident.

- **Under ~15 endpoints**: one module, skip `shared/` entirely until a second module actually needs something from it.
- **Multiple domains and a team**: the full structure, `ModularityTests` in CI from day one — it's nearly free and gets much more painful to add after the boundaries have already blurred.
- **Only add RabbitMQ** when there's real async work or real cross-module eventing that must survive a restart. Spring's own `ApplicationEventPublisher` (in-process, synchronous by default) covers the rest.
- **Only turn on the outbox** (`spring-modulith-starter-jdbc`) when losing an event has business consequences. Without it, `@ApplicationModuleListener` still runs after commit — you're choosing durability across a broker/consumer restart, not correctness within the app.
- **Spring Data's `JpaRepository` and `@Transactional` are the default, not a judgment call** (rule #6) — don't rebuild what Spring already gives you for free.

Say so plainly when a request would push past what the project needs. Suggesting the smaller version is more useful than silently building the bigger one.

## Writing style in generated code

- One Javadoc line per public class/method where the *why* isn't obvious from the name; no inline `//` narration of *what* the code does.
- Prefer `record` for DTOs and events — immutable, `equals`/`hashCode`/`toString` for free, and it reads as data rather than behavior.
- Constructor injection only (`final` fields, no `@Autowired` on fields) — makes missing dependencies a compile error, not a runtime `NullPointerException`.
- Never return a JPA `@Entity` from a controller. Lazy-loaded associations serialize unpredictably (or throw `LazyInitializationException` outside the session) and entities leak columns never meant to be public — map to a `dto` record instead.

## Reference files

Read the one that matches the task.

- `references/architecture.md` — bootstrapping, adding a module, module boundaries and verification, correlation-id logging, authorization
- `references/placement.md` — where each kind of code belongs, and the failure modes of getting it wrong, plus the promotion bar for `shared/`
- `references/layer-examples.md` — every layer's real code side by side: entity, rules, use case, repository port, facade, controller, module test
- `references/caching-and-messaging.md` — Redis cache keys and invalidation; RabbitMQ topology, the outbox, idempotent consumers
- `references/api-contract.md` — `ProblemDetail` error shape, error codes, pagination, versioning
- `references/deployment.md` — profiles, layered Docker image, compose file, Testcontainers, migrations
- `references/checklist.md` — pre-production review

## Verify before handing over

A structure that doesn't compile or doesn't respect its own boundaries is worse than no structure:

```bash
mvn -q -DskipTests package
mvn -q test -Dtest=ModularityTests   # ApplicationModules.of(...).verify()
mvn -q verify                         # full test suite, Testcontainers included
```
