# Architecture Reference

## Dependency Direction

```
controller -> usecase -> repository -> entity
    |           |             |
    |      rules, util   integrations/cache
    |
facade (calls usecases or internal services — the only cross-module entrypoint)
```

Modules depend on `core/` mechanism and on `integrations/`. `core/` and `integrations/` depend on no domain module. Domain modules reach each other only sideways, through the base package's **Facade** (`<Module>Management.java`) and types exposed via `@NamedInterface`.

```
modules.identity (Facade) <------ modules.billing (UseCase)
        |
   (package-info with @NamedInterface("dto"))
```

`usecase` orchestrates business logic: it calls domain `rules`, coordinates `repository` persistence, delegates cache calls to `integrations/cache`, and publishes application events. A use case depends on Spring Data's `JpaRepository` interface directly (which is already the abstraction). The use case is the transaction boundary via `@Transactional`.

Nothing points back up. Domain rules in `internal/rules/` import nothing from Spring, web, or repositories — they are pure Java. Repositories never import use cases or controllers. If an import needs to go upward, the logic is sitting in the wrong layer.

No two packages within a module import each other circularly. If two classes seem to need each other, move the shared data structure down a tier (into constants, exceptions, or DTO records) or delegate via application events.

---

## Avoiding the "God Module" Trap (When and How to Split)

A common architecture failure in modular monoliths is creating a **God Module** — most notoriously by shoving half the system into `identity`.

When you see:
- `identity` owning audit logs (`internal/audit/`)
- `identity` owning mail dispatch (`internal/mail/`)
- `identity` owning branch/organization management (`Branch`, `transferBranch`)
- `identity` owning complex RBAC matrices (Roles, Permissions, PermissionGroups, GroupAssignments)
- `identity` owning dashboard metric aggregations (`DashboardController`)

**Stop immediately.** That is no longer an application module; it is a mini-monolith hiding behind a single `@ApplicationModule` annotation.

### The Bounded Context Test:
Ask: **What is the single core responsibility of this module?**
- **`modules.identity`**: Authenticating identities and managing credentials.
  - *Owns*: `Account`, password hashes, login/logout, refresh tokens, session revocation.
  - *Does NOT own*: Audit log persistence, sending emails, branch CRUD, dashboard stats.

### How to Split the God Module:

```
src/main/java/com/eduerp/modules/
├── identity/          # ONLY accounts, credentials, auth sessions, profile
│   ├── IdentityManagement.java
│   ├── internal/model/Account.java
│   └── usecase/ (Login, Logout, Register, ResetPassword)
│
├── access/            # RBAC: roles, permissions, permission groups, assignments
│   ├── AccessManagement.java
│   ├── internal/model/ (Role, Permission, RolePermission, AccountRole)
│   └── usecase/ (AssignRole, CreateRole, ComputeEffectivePermissions)
│
├── organization/      # Branches, departments, campus hierarchy
│   ├── OrganizationManagement.java
│   ├── internal/model/ (Branch, Department)
│   └── usecase/ (CreateBranch, UpdateBranch, TransferAccountBranch)
│
├── audit/             # Audit trail, event history
│   ├── internal/listener/ (Listens to IdentityEvents, AccessEvents via @ApplicationModuleListener)
│   ├── internal/model/AuditLog.java
│   └── usecase/QueryAuditLogs.java
│
└── dashboard/         # Aggregated read-models & executive statistics
    └── web/DashboardController.java (queries Facades of other modules)
```

### Communication between Split Modules:
1. **Asynchronous reactions via Events**:
   - `identity` registers an account and publishes `IdentityEvents.AccountRegistered(id, email, branchId)`.
   - `audit` module listens with `@ApplicationModuleListener` and writes an audit row.
   - `mail` integration listens with `@ApplicationModuleListener` and sends a welcome email.
   - `identity` has **zero imports** of `audit` or `mail`!
2. **Synchronous checks via Facade**:
   - When `identity` needs to know if a user has permission to log in or perform an admin action, it queries `AccessManagement.getEffectivePermissions(accountId)`.
   - `identity.Account` stores `UUID branchId`, but never imports `Branch` entity or `BranchRepository`. When validating a branch during registration, it calls `OrganizationManagement.existsBranch(branchId)`.

---

## Intra-Module Package Tiers

Classes inside one domain module form strict tiers. A class only imports from a class in an earlier tier; never a sibling in the same tier, never anything later.

| Tier | Package / Class | May import (same module) | Why |
|---|---|---|---|
| **0** | `<Module>Constants.java`, `<Module>Properties.java` | Nothing from this module | The vocabulary, enums, limits, and settings everything else is written in terms of. |
| **1** | `<Module>Exception.java`, `dto/**`, `internal/model/**`, `<Module>Events.java` | Tier 0 | Data shapes, JPA entities, sealed exceptions, and event records defined in terms of that vocabulary; no decisions. |
| **2** | `internal/rules/**`, `internal/util/**` | Tier 0–1 | Business decisions and pure data transforms; no I/O, no database, no HTTP. |
| **3** | `internal/repository/**` | Tier 0–2 | Persistence ports and queries, built from the JPA models and shapes above it. |
| **4** | `usecase/**` | Tier 0–3 | Orchestration — the only place Tier 2 (rules) and Tier 3 (repository) meet. |
| **5** | `web/**` | Tier 0–1 directly, Tier 4 via constructor injection | HTTP translation only (controllers, filters, cookies). Never imports repositories or internal rules directly. |
| **6** | `<Module>Management.java` (Facade in base package) | Tier 0–4 | The facade that other modules see. Orchestrates use cases or internal query services. |

---

## Spring Modulith 1.3 Architecture & Detection Strategies

Spring Modulith derives modules from package structure. In Spring Boot 3.4 and Java 21, the recommended configuration uses **explicit module detection**:

```yaml
# application.yml
spring:
  modulith:
    detection-strategy: explicitly-annotated
```

### Why `explicitly-annotated` is mandatory for grouped domains

By default, Spring Modulith treats each *direct* sub-package of the main application package as a module. If your domains are organized under `com.acme.shop.modules.<domain>`, the default strategy treats `modules` as a single giant module. All domain boundaries collapse, their `internal/` packages become mutually visible, and `ApplicationModules.verify()` passes while providing zero real enforcement!

Setting `detection-strategy: explicitly-annotated` ensures that only packages annotated with `@ApplicationModule` in their `package-info.java` are recognized as modules, regardless of nesting depth (`com.acme.shop.modules.identity`, `com.acme.shop.integrations.cache`).

```java
// src/main/java/com/acme/shop/modules/identity/package-info.java
@org.springframework.modulith.ApplicationModule(
    displayName = "Identity & Access",
    allowedDependencies = {"integrations::cache", "shared"}
)
package com.acme.shop.modules.identity;
```

---

## Modularity Verification in CI

Every project MUST include `ModularityTests.java` in `src/test/java`:

```java
package com.eduerp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(EduErpApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    @Test
    void everyDomainAndIntegrationPackageIsADetectedModule() {
        var detected = modules.stream().map(ApplicationModule::getName).collect(Collectors.toSet());
        assertThat(detected).containsExactlyInAnyOrder(
            "core",
            "modules.identity",
            "modules.access",
            "modules.organization",
            "modules.audit",
            "integrations.cache"
        );
    }

    @Test
    void writesDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
```

---

## Transaction Boundaries

The Use Case is the transaction boundary. Annotate the single public `execute` method with `@Transactional`:

```java
package com.eduerp.modules.identity.usecase;

@Service
public class RegisterAccount {

    private final AccountRepository repository;
    private final IdentityRules rules;
    private final ApplicationEventPublisher events;

    public RegisterAccount(AccountRepository repository, IdentityRules rules, ApplicationEventPublisher events) {
        this.repository = repository;
        this.rules = rules;
        this.events = events;
    }

    @Transactional
    public AccountSummaryResponse execute(RegisterAccountRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        Account account = new Account(request.email(), request.password());
        repository.save(account);

        events.publishEvent(new IdentityEvents.AccountRegistered(account.getId(), account.getEmail()));
        return AccountSummaryResponse.from(account);
    }
}
```

### Spring AOP Visibility Rule
Spring's default proxy-based AOP only wraps **`public`** methods in a transaction. A `protected` or package-private method annotated with `@Transactional` will silently **NOT** execute in a transaction! Always ensure:
1. The use case class is `public`.
2. The `execute(...)` method is `public`.
3. Calls from controllers or facades invoke the public method through the injected Spring bean proxy.
