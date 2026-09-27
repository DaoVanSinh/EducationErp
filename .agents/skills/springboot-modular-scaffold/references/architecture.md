# Architecture

## Bootstrapping

```bash
curl https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d bootVersion=3.4.1 \
  -d javaVersion=21 -d groupId=com.acme -d artifactId=shop -d packageName=com.acme.shop \
  -d dependencies=web,data-jpa,postgresql,validation,actuator,modulith \
  -o shop.zip && unzip shop.zip -d shop
```

Add `spring-boot-starter-data-redis`, `spring-modulith-starter-amqp` + `spring-boot-starter-amqp`, or an S3 SDK dependency only when the project needs them — see `caching-and-messaging.md`.

`@Modulithic` goes on the application class once there's more than one module worth naming explicitly:

```java
@Modulithic(systemName = "Shop", sharedModules = {"shared"})
@SpringBootApplication
public class ShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
```

## Adding a module

There's no generator script — a module is small enough to hand-write correctly once the rules are clear, and generating Java from a template tends to fight the IDE. Steps, in order:

1. Create the package: `com.acme.shop.<module>`.
2. Add the entity (`@Entity`), its repository (`interface <X>Repository extends JpaRepository<X, ID>`), and a Flyway/Liquibase migration for its table — see `layer-examples.md`.
3. Add `<Module>Constants` (enums, error codes, limits — one class, rule #10) if the module has any yet. Add `<Module>Properties` with `@ConfigurationProperties(prefix = "<module>")` only once the module actually has its own settings — most new modules don't need one on day one.
4. Add domain exceptions extending `core.exception.AppException` only for failure modes the module actually has (a bare create/read use case with no real failure beyond Bean Validation needs none yet) — add them when the first real one shows up, not speculatively.
5. Add `<Module>Rules` only once there's an actual decision to encode (a `boolean can...()`/`validate...()` that isn't just field validation) — see `placement.md` for the line between a rule and a plain transform. Skip it if the module is CRUD-shaped so far.
6. Write the use case(s) under `<module>/usecase/` — one class, one **public** `@Transactional` method (proxy-based AOP requires public visibility — see `layer-examples.md`'s note on this), constructor-injected repository.
7. Add the facade — a single public class (commonly named `<Module>Management`) in the module's *base* package that is the *only* type other modules are allowed to reference. Everything else either lives under `<module>.internal`/`<module>.usecase` or, in a module with no sub-packages, is simply not `public`. If the facade's methods take or return a DTO from a sub-package (e.g. `<module>.dto`), expose that sub-package with `@NamedInterface` — otherwise `ApplicationModules.verify()` fails on a type the facade itself requires callers to reference.
8. Add the controller, thin, delegating to the use case.
9. Run `ModularityTests` — it will fail loudly if step 7's `@NamedInterface` step was skipped and a DTO the facade exposes leaked in from a hidden sub-package, or if something non-facade leaked into another module's imports.

## Module boundaries and verification

Spring Modulith derives modules from package structure with no annotation required, in two shapes:

**Simple module** (no sub-packages): every `public` type is the API, every package-private type is internal.

```
identity/
├── IdentityManagement.java   public → API
└── IdentityInternalCache.java (package-private) → hidden
```

**Advanced module** (has sub-packages): the base package is the API, every sub-package is internal by default.

```
identity/                     ← API package, e.g. IdentityManagement.java
identity/internal/            ← hidden from every other module
```

To deliberately expose one extra package (a webhook payload contract, an SPI another bounded context needs), annotate its `package-info.java`:

```java
@org.springframework.modulith.NamedInterface("spi")
package com.acme.shop.identity.spi;
```

and reference it from the dependent module's own `package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(allowedDependencies = "identity :: spi")
package com.acme.shop.billing;
```

Verify the whole arrangement in a plain JUnit test, checked into `src/test/java` and run in CI like any other test:

```java
class ModularityTests {
    ApplicationModules modules = ApplicationModules.of(ShopApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    @Test
    void writesDocumentation() {
        new Documenter(modules).writeDocumentation(); // module canvas + C4 diagrams, optional
    }
}
```

A failing `verify()` names the exact offending dependency — treat it the same way a failed `lint-imports`/`check_module_boundaries.py` run is treated in `fastapi-modular-scaffold`: fix the boundary, don't suppress the check.

For stricter enforcement *within* a module (e.g. "adapters may call the port, never the domain rules directly"), layer `spring-modulith-module-archunit` on top rather than inventing bespoke ArchUnit rules from scratch.

## Correlation-id logging

Bind a request id (this hop) and a correlation id (the whole flow) to every log line the way `fastapi-modular-scaffold`'s `RequestIdMiddleware` does, using a `OncePerRequestFilter` and SLF4J's MDC:

```java
public class RequestCorrelationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var requestId = UUID.randomUUID().toString();
        var correlationId = Optional.ofNullable(req.getHeader("X-Correlation-ID")).orElse(requestId);
        try (var ignored1 = MDC.putCloseable("requestId", requestId);
             var ignored2 = MDC.putCloseable("correlationId", correlationId)) {
            res.setHeader("X-Request-ID", requestId);
            res.setHeader("X-Correlation-ID", correlationId);
            chain.doFilter(req, res);
        }
    }
}
```

Pair it with `logstash-logback-encoder` for JSON logs in staging/prod (plain pattern layout in dev) and add `micrometer-tracing-bridge-otel` if the project also wants `traceId`/`spanId` merged into every line — that dependency alone is enough; Spring Boot wires the MDC bridge automatically, no manual OTel merge code needed (a place this is simpler than the FastAPI equivalent). Redact `password`/`token`/`authorization`/`secret`/`apiKey`/`creditCard` fields the same way — a Logback `TurboFilter` or a Jackson mixin on the log encoder, not scattered `if` checks at each call site.

## Authorization

Roles/permissions are owned by whichever module is their source of truth (usually `identity`), exposed as an enum in its `Constants` class, never hardcoded as string literals at call sites. Enforce with Spring Security method security:

```java
@PreAuthorize("hasAuthority('ORDER_CANCEL')")
public void execute(CancelOrderCommand command) { ... }
```

on the use-case method, not the controller — the same "push the check to the layer that owns the decision" instinct as `fastapi-modular-scaffold`'s `rules.py`. Org/department-scoped access (not just a flat role) is a rule in `<module>Rules`, evaluated inside the use case with the caller's `Authentication` passed in explicitly — don't reach for `SecurityContextHolder` inside a repository or rules class, since that couples pure decision logic to the servlet request thread.
