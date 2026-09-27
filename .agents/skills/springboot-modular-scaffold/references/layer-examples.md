# Layer examples

One worked module, `identity`, every layer side by side. Package: `com.acme.shop.identity`.

## Entity

```java
package com.acme.shop.identity;

@Entity
@Table(name = "identities")
class Identity {
    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    private IdentityConstants.Status status;

    // package-private no-arg ctor for JPA, a real ctor for the module's own code, getters — omitted for brevity
}
```

Package-private class: it's an internal detail of `identity`, never referenced by name outside this module. Only `IdentityRepository` and the use cases inside `identity` touch it directly.

## Constants (rule #10 — everything nested inside one class)

```java
package com.acme.shop.identity;

public final class IdentityConstants {
    private IdentityConstants() {}

    public enum Status { PENDING, ACTIVE, SUSPENDED }

    public static final int MAX_EMAIL_LENGTH = 254;
    public static final Duration TOKEN_TTL = Duration.ofMinutes(15);
}
```

## Module-owned config

```java
package com.acme.shop.identity;

@ConfigurationProperties(prefix = "identity")
public record IdentityProperties(String jwtSecret, Duration tokenTtl) {}
```

`application.yml`:

```yaml
identity:
  jwt-secret: ${IDENTITY_JWT_SECRET}
  token-ttl: 15m
```

## Exceptions

```java
package com.acme.shop.identity;

public sealed class IdentityException extends AppException
        permits UserNotFoundException, EmailTakenException {
    protected IdentityException(String errorCode, String message) { super(errorCode, message); }
}

public final class UserNotFoundException extends IdentityException {
    public UserNotFoundException(UUID id) {
        super("IDENTITY_USER_NOT_FOUND", "No user with id " + id);
    }
}
```

`AppException` (in `core/exception/`) carries `errorCode` + a `HttpStatus`; `GlobalExceptionHandler` maps it to a `ProblemDetail` — see `api-contract.md`.

## Rules — pure decision, no I/O, no Spring annotation

```java
package com.acme.shop.identity;

final class IdentityRules {
    boolean canActivate(Identity identity) {
        return identity.getStatus() == IdentityConstants.Status.PENDING;
    }
}
```

Testable with `new IdentityRules().canActivate(...)` — no `@SpringBootTest`, no database, no mocks.

## DTO

```java
package com.acme.shop.identity.dto;

public record RegisterUserRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12) String password
) {}

public record UserResponse(UUID id, String email, IdentityConstants.Status status) {}
```

`dto/` is a sub-package, so Spring Modulith's advanced-module rule hides it from other modules by default — but the facade below returns `UserResponse` and accepts `RegisterUserRequest`, so any caller of the facade necessarily references these types across the module boundary. Expose the package the same way `architecture.md`'s `spi` example does, with its own `package-info.java`:

```java
@org.springframework.modulith.NamedInterface("dto")
package com.acme.shop.identity.dto;
```

Skipping this step is the single most common way a module fails `ApplicationModules.verify()`: the build passes locally in the IDE (nothing stops a plain compile) and only fails on the boundary-verification test.

## Repository — the port *is* `JpaRepository`, no hand-rolled abstraction (rule #6)

```java
package com.acme.shop.identity;

interface IdentityRepository extends JpaRepository<Identity, UUID> {
    Optional<Identity> findByEmail(String email);
}
```

A unit test for a use case that depends on this interface doesn't need a `Fake` subclassing anything — Mockito mocks the interface directly:

```java
@Mock IdentityRepository repository;
```

## Use case — one class, one method, `@Transactional` is the unit of work (rule #6)

```java
package com.acme.shop.identity.usecase;

@Service
public class RegisterUser {
    private final IdentityRepository repository;
    private final IdentityRules rules;
    private final ApplicationEventPublisher events;

    RegisterUser(IdentityRepository repository, IdentityRules rules, ApplicationEventPublisher events) {
        this.repository = repository;
        this.rules = rules;
        this.events = events;
    }

    @Transactional
    public UserResponse execute(RegisterUserRequest request) {
        if (repository.findByEmail(request.email()).isPresent()) {
            throw new EmailTakenException(request.email());
        }
        var identity = new Identity(request.email(), request.password());
        repository.save(identity);
        events.publishEvent(new IdentityEvents.UserRegistered(identity.getId(), identity.getEmail()));
        return new UserResponse(identity.getId(), identity.getEmail(), identity.getStatus());
    }
}
```

The method either fully commits (row saved, event durably queued once `spring-modulith-starter-jdbc` is present) or fully rolls back — no separate `UnitOfWork.commit()` call to forget.

**The class and its `@Transactional` method must both be `public`.** Spring's default proxy-based AOP only intercepts public method invocations — a package-private or protected method carrying `@Transactional` is silently *not* wrapped in a transaction at all (no error, it just doesn't run in one), which would quietly defeat rule #6. This is why `RegisterUser` is `public` here even though nothing outside the module is meant to call it directly by name; visibility for AOP purposes and visibility for the module-boundary facade rule (rule #1) are two different concerns; a use case can be `public` for the proxy's sake while still never appearing in another module's imports. The same rule applies to `@ApplicationModuleListener` and `@Async` methods below.

## Events

```java
package com.acme.shop.identity;

public final class IdentityEvents {
    private IdentityEvents() {}
    public record UserRegistered(UUID userId, String email) {}
}
```

Another module listens without ever importing `identity`'s internals:

```java
package com.acme.shop.billing;

@Component
class OpenBillingAccountOnRegistration {
    @ApplicationModuleListener
    public void on(IdentityEvents.UserRegistered event) {
        // opens a billing account — billing never called identity's repository directly
    }
}
```

## Facade — the only cross-module entry point

```java
package com.acme.shop.identity;

@Service
public class IdentityManagement {
    private final RegisterUser registerUser;

    IdentityManagement(RegisterUser registerUser) { this.registerUser = registerUser; }

    public UserResponse register(RegisterUserRequest request) {
        return registerUser.execute(request);
    }
}
```

`Identity`, `IdentityRepository` and `IdentityRules` are package-private — genuinely invisible outside the module. `RegisterUser` is `public` for the AOP reason above, but it isn't part of the module's *intended* API: it lives in `usecase/`, a sub-package hidden by Spring Modulith's advanced-module rule regardless of the class's own visibility modifier. `IdentityManagement` (in the base package) plus the `@NamedInterface`-exposed `dto`/events/exceptions are the surface another module — or `ApplicationModules.verify()` — actually treats as reachable.

## Controller — thin, HTTP-only

```java
package com.acme.shop.identity;

@RestController
@RequestMapping("/api/identities")
class IdentityController {
    private final IdentityManagement identity;

    IdentityController(IdentityManagement identity) { this.identity = identity; }

    @PostMapping
    ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(identity.register(request));
    }
}
```

## Module test

```java
@ApplicationModuleTest
class IdentityModuleTests {
    @Test
    void registersUser(Scenario scenario) {
        // @ApplicationModuleTest boots only this module + its declared dependencies,
        // and fails if the test ends up reaching into another module's internals.
    }
}
```

Pair with a plain `IdentityRulesTest` (no Spring context, instant), a `RegisterUserTest` (Mockito fake for `IdentityRepository`, no database), and one `IdentityControllerIT` per module using `@SpringBootTest` + Testcontainers Postgres for the real end-to-end path — the same three-tier test pyramid `fastapi-modular-scaffold` uses (`test_rules.py` / `test_services.py` / `test_router.py`).
