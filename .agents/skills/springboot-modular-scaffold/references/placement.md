# Placement Reference

The single question this file answers: **when you write a piece of code, where does it go?**

---

## The Test

Ask **who owns the concept**, not what shape the code has.
- `MAX_SEATS_PER_CLASS` is an integer, and `slugify()` is a method, but neither fact decides placement.
- What decides placement is that `MAX_SEATS_PER_CLASS` is a business rule about enrollment, and `slugify()` is a generic text transform.

A second question resolves almost everything else:
**If this module were extracted into its own independent microservice tomorrow, would this code go with it?**
- If **yes**, it belongs inside that domain module.
- If it would have to be duplicated or left behind because other modules also need the technical mechanism, it is **mechanism** and belongs in `core/` or `integrations/`.

Which file answers "who owns the concept." A separate rule answers *how it is written once it is there*:
- Constants are grouped inside static inner classes of `<Module>Constants.java`.
- Business decisions live inside `<Module>Rules.java`.
- Data transforms live inside `<Module>Util.java` or `util/` sub-package.
- Never write loose, un-scoped constants or global utility dumping grounds.

---

## The Placement Decision Matrix

| Code / Concept | Location | Why |
|---|---|---|
| `AccountStatus`, `EnrollmentStatus` | `<module>/<Module>Constants.java` (as an `enum`) | The enum is a core domain vocabulary owned by this module. |
| `MAX_LOGIN_ATTEMPTS`, `PAGE_MAX_SIZE` | `<module>/<Module>Constants.java` (nested class `Limits`) | Business limit that changes with domain policy. |
| `ErrorCode` string constants | `<module>/<Module>Constants.java` (nested class `ErrorCodes`) | Client-facing error catalog owned by this module. |
| `AccountNotFoundException`, `EmailTakenException` | `<module>/<Module>Exception.java` (or sub-package) | Domain exceptions extending `core.exception.AppException`. |
| `AppException`, `ConflictException` base | `core/exception/` (published via `@NamedInterface`) | Shared HTTP and error mechanism; knows no domain names. |
| `jwt.secret`, `token.access-ttl` | `<module>/<Module>Properties.java` (`@ConfigurationProperties`) | Settings only this module reads; prefixed with module name (`identity.*`). |
| `spring.datasource.*`, `server.port`, CORS | `application.yml` root, bound in `core/config/` | System-wide mechanism; the application process needs them to start. |
| `canActivateAccount()`, `isEligibleForDiscount()` | `<module>/internal/rules/<Module>Rules.java` | Pure business decision; zero I/O, no database, no Spring annotations. |
| `normalizeEmail()`, `formatPhoneNumber()` | `<module>/internal/util/` | Pure data transform; changes only if formatting specifications change. |
| `ProblemDetail`, `GlobalExceptionHandler` | `core/exception/` | Mechanism for RFC 9457 HTTP error serialization. |
| `RequestCorrelationFilter`, MDC keys | `core/web/` | Mechanism for request/correlation ID tracking. |
| Redis key assembly (`CacheKeyBuilder`) | `integrations/cache/CacheKeyBuilder.java` | Infrastructure mechanism for building colon-separated key strings. |
| Redis key namespaces (`blacklist:jti`, `perm:account`) | `<module>/<Module>Constants.CacheNamespaces` | The namespace name belongs to the domain; the assembly belongs to cache. |
| An enum or record needed by 3+ modules (`PermissionScope`) | `shared/SharedConstants.java` | Promoted only after clearing the Rule of Three; never a first draft. |
| JPA `@Entity` (`Account`, `Role`) | `<module>/internal/model/` | Only this module's repository queries these tables. |
| `JpaRepository` sub-interfaces | `<module>/internal/repository/` | Persistence ports; invisible outside this module. |
| Cross-module entrypoint (`IdentityManagement`) | `<module>/` (base package) | The Facade — the only type other modules may import. |
| DTO request/response records | `<module>/dto/` (with `@NamedInterface("dto")`) | Wire contracts and data crossing module boundaries. |

---

## Grouped into a Class (No Bare Floating Constants)

In Java, every variable lives in a class. But placing everything flat in a class or scattering constants across random files causes the same degradation as Python's loose constants.

Organize constants into **named static inner classes** inside `<Module>Constants.java`:

```java
package com.eduerp.modules.identity;

public final class IdentityConstants {

    private IdentityConstants() {}

    public enum Status {
        PENDING, ACTIVE, SUSPENDED, DELETED
    }

    public static final class Limits {
        private Limits() {}
        public static final int MAX_EMAIL_LENGTH = 254;
        public static final int MAX_FAILED_LOGINS = 5;
        public static final int PASSWORD_MIN_LENGTH = 8;
    }

    public static final class Resources {
        private Resources() {}
        public static final String ACCOUNT = "ACCOUNT";
        public static final String ROLE = "ROLE";
        public static final String GROUP = "GROUP";
    }

    public static final class Actions {
        private Actions() {}
        public static final String CREATE = "CREATE";
        public static final String READ = "READ";
        public static final String UPDATE = "UPDATE";
        public static final String DELETE = "DELETE";
    }

    public static final class CacheNamespaces {
        private CacheNamespaces() {}
        public static final String SESSIONS = "sessions:account";
        public static final String PERMISSIONS = "perm:account";
        public static final String BLACKLIST_JTI = "blacklist:jti";
    }

    public static final class ErrorCodes {
        private ErrorCodes() {}
        public static final String ACCOUNT_NOT_FOUND = "IDENTITY_ACCOUNT_NOT_FOUND";
        public static final String EMAIL_ALREADY_EXISTS = "IDENTITY_EMAIL_ALREADY_EXISTS";
        public static final String INVALID_CREDENTIALS = "IDENTITY_INVALID_CREDENTIALS";
    }
}
```

This discipline provides instant clarity:
- `IdentityConstants.Limits.MAX_EMAIL_LENGTH`
- `IdentityConstants.CacheNamespaces.PERMISSIONS`
- `IdentityConstants.ErrorCodes.ACCOUNT_NOT_FOUND`

---

## `internal/rules/` vs. `internal/util/`

Both contain pure Java methods with no database I/O, so developers frequently lump them into a single `Utils` class. This is a fatal mistake.

| Dimension | `internal/rules/` | `internal/util/` |
|---|---|---|
| **Question answered** | *May this action proceed?* (Decision) | *What does this look like in another format?* (Transform) |
| **Examples** | `canCancelOrder()`, `isEligibleForPromotion()` | `normalizeEmail()`, `slugify()`, `maskCard()` |
| **Why it changes** | Business policy or product rules change. | Encoding or formatting standards change. |
| **Testing** | Heavy unit testing of edge cases and business scenarios. | Deterministic input/output unit tests. |

Keeping them separate guarantees:
1. When product owners ask "what are our business validation rules for accounts?", you open `<Module>Rules.java`.
2. Normalizing an email never accidentally changes account eligibility logic.
3. Neither file ever grows into a grab-bag dumping ground.

---

## Why Domain Exceptions Stay Inside the Module

The temptation to declare all application exceptions in `core/exception/` is strong and destructive.

- `AccountNotFoundException` encodes domain knowledge: it knows accounts exist, what identifies them, and when an operation cannot proceed.
- If all domain exceptions are moved to `core/exception/`, `core` gradually accumulates knowledge of students, teachers, grades, invoices, enrollments, and payments.
- Changing an invoice error requires modifying a shared core file that every module depends on.

### The Correct Division
1. **`core/exception/`** owns the **mechanism**:
   - Base `AppException` (abstract class with `errorCode`, `status`, `message`).
   - Standard HTTP bases: `NotFoundException`, `ConflictException`, `ForbiddenException`, `BadRequestException`.
   - `GlobalExceptionHandler` and RFC 9457 `ProblemDetail` mapping.
2. **`<module>/<Module>Exception.java`** owns the **domain catalog**:
   - Sealed class hierarchy extending `AppException`:
     ```java
     public sealed class IdentityException extends AppException
         permits AccountNotFoundException, EmailAlreadyExistsException, InvalidCredentialsException {
         protected IdentityException(String errorCode, HttpStatus status, String message) {
             super(errorCode, status, message);
         }
     }
     ```

Base classes are centralized; error catalogs are distributed.

---

## Integrations Are Modules, Not Utilities

Infrastructure dependencies like Redis cache, RabbitMQ/Kafka messaging, S3 storage, or OpenTelemetry tracing are **first-class modules** under `integrations/`.

```
integrations/
├── cache/
│   ├── CacheKeyBuilder.java
│   ├── CacheConfig.java
│   ├── CacheProperties.java
│   └── package-info.java (@ApplicationModule)
├── messaging/
│   ├── TopologyConfig.java
│   ├── EventRelay.java
│   ├── OutboxCleaner.java
│   └── package-info.java (@ApplicationModule)
└── storage/
    ├── S3ClientConfig.java
    ├── S3StorageService.java
    └── package-info.java (@ApplicationModule)
```

They are modules in every sense, differing from domain modules only in what they omit:
- No `@Entity` classes (they own no relational database tables).
- No `@RestController` classes (they expose no HTTP endpoints).

Swapping Redis for another caching provider or replacing S3 with MinIO touches only `integrations/` and does not ripple into any domain module.

---

## Warning Signs (Boundary Leaks)

If any of the following appear during code review, the modular boundary has leaked:

1. A `Utils.java` or `CommonUtils.java` appears at the root or under `core/`.
2. `core/exception/AppException.java` mentions an `Account`, `Student`, or any domain entity by name.
3. Global `application.yml` contains properties owned by a single domain (e.g. `jwt-secret` placed at root instead of under `identity.*`).
4. A class in `modules.billing` imports a class from `modules.identity.internal.*`.
5. A class in `modules.billing` executes a SQL `JOIN` against the `identities` table.
6. A `@Repository` calls `flush()` or `@Transactional` is omitted from a multi-step use case.
7. A domain controller returns a JPA `@Entity` directly to the HTTP client.
8. Two domain modules import the same class from a third domain module that is neither's owner.

---

## When Duplication is Correct (The Rule of Three)

Sharing code creates invisible coupling. When two modules share a constant or helper, any future change to that code requires synchronizing both domains.

- If Module A and Module B both have `MAX_RETRY_COUNT = 3`, keep them separate in `ModuleAConstants.Limits` and `ModuleBConstants.Limits`. Their business requirements will diverge over time.
- **The Rule of Three**: Duplicate until **three or more modules** require the exact same concept, AND it is completely stable, AND divergence between them would represent a critical system bug.
- Only then promote the concept into `shared/SharedConstants.java`. Never make `shared/` a first draft.
