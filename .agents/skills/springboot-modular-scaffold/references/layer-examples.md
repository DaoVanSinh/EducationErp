# Layer-by-Layer Examples

This reference provides a complete, worked implementation of an **Advanced Application Module**: `identity` under the package `com.eduerp.modules.identity`.

Every layer is written using **Java 21** (`record`, `sealed` classes, pattern matching) and **Spring Boot 3.4 / Spring Modulith 1.3**.

---

## 1. Module Definition: `package-info.java`

Annotates the module boundary, sets display metadata, and declares allowed outgoing dependencies.

```java
// src/main/java/com/eduerp/modules/identity/package-info.java
/**
 * Module Identity &amp; Access — Source of truth for accounts, roles, permissions,
 * and authentication sessions.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Identity & Access",
    allowedDependencies = {"integrations::cache", "shared"}
)
package com.eduerp.modules.identity;
```

---

## 2. Base Package Public API: Constants (`IdentityConstants.java`)

All constants, enums, limits, and namespaces owned by this module are organized into static inner classes.

```java
// src/main/java/com/eduerp/modules/identity/IdentityConstants.java
package com.eduerp.modules.identity;

public final class IdentityConstants {

    private IdentityConstants() {}

    public enum AccountStatus {
        ACTIVE, SUSPENDED, PENDING_ACTIVATION
    }

    public enum PermissionScope {
        PERSONAL(0), BRANCH(1), ORGANIZATION(2);

        private final int rank;

        PermissionScope(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }

    public static final class Limits {
        private Limits() {}
        public static final int MAX_EMAIL_LENGTH = 254;
        public static final int MIN_PASSWORD_LENGTH = 8;
        public static final int MAX_FAILED_ATTEMPTS = 5;
    }

    public static final class Resources {
        private Resources() {}
        public static final String ACCOUNT = "ACCOUNT";
        public static final String ROLE = "ROLE";
        public static final String PERMISSION = "PERMISSION";
        public static final String BRANCH = "BRANCH";
    }

    public static final class Actions {
        private Actions() {}
        public static final String CREATE = "CREATE";
        public static final String READ = "READ";
        public static final String UPDATE = "UPDATE";
        public static final String DELETE = "DELETE";
    }

    public static final class RoleCodes {
        private RoleCodes() {}
        public static final String SUPER_ADMIN = "SUPER_ADMIN";
        public static final String BRANCH_MANAGER = "BRANCH_MANAGER";
        public static final String TEACHER = "TEACHER";
        public static final String STUDENT = "STUDENT";
    }

    public static final class Authorities {
        private Authorities() {}
        public static final String PERMISSION_PREFIX = "PERM:";
    }

    public static final class CacheNamespaces {
        private CacheNamespaces() {}
        public static final String EFFECTIVE_PERMISSIONS = "perm:account";
        public static final String BLACKLIST_JTI = "blacklist:jti";
        public static final String SESSIONS = "sessions:account";
    }

    public static final class ErrorCodes {
        private ErrorCodes() {}
        public static final String ACCOUNT_NOT_FOUND = "IDENTITY_ACCOUNT_NOT_FOUND";
        public static final String EMAIL_ALREADY_EXISTS = "IDENTITY_EMAIL_ALREADY_EXISTS";
        public static final String INVALID_CREDENTIALS = "IDENTITY_INVALID_CREDENTIALS";
        public static final String ACCOUNT_SUSPENDED = "IDENTITY_ACCOUNT_SUSPENDED";
    }
}
```

---

## 3. Module Configuration: `IdentityProperties.java`

Typesafe configuration properties bound specifically to `identity.*`.

```java
// src/main/java/com/eduerp/modules/identity/IdentityProperties.java
package com.eduerp.modules.identity;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity")
public record IdentityProperties(
    String jwtSecret,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    Duration sessionCacheTtl,
    boolean secureCookies
) {
    public IdentityProperties {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalArgumentException("identity.jwt-secret must be at least 32 characters long");
        }
        if (accessTokenTtl == null) accessTokenTtl = Duration.ofMinutes(15);
        if (refreshTokenTtl == null) refreshTokenTtl = Duration.ofDays(30);
        if (sessionCacheTtl == null) sessionCacheTtl = Duration.ofHours(24);
    }
}
```

---

## 4. Domain Exceptions: Sealed Hierarchy (`IdentityException.java`)

Sealed hierarchy extending `AppException`. Sealed classes guarantee exhaustive compile-time checking.

```java
// src/main/java/com/eduerp/modules/identity/IdentityException.java
package com.eduerp.modules.identity;

import com.eduerp.core.exception.AppException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public sealed class IdentityException extends AppException
    permits AccountNotFoundException, EmailAlreadyExistsException, InvalidCredentialsException {

    protected IdentityException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}

public final class AccountNotFoundException extends IdentityException {
    public AccountNotFoundException(UUID id) {
        super(
            IdentityConstants.ErrorCodes.ACCOUNT_NOT_FOUND,
            HttpStatus.NOT_FOUND,
            "Account with id " + id + " was not found"
        );
    }

    public AccountNotFoundException(String email) {
        super(
            IdentityConstants.ErrorCodes.ACCOUNT_NOT_FOUND,
            HttpStatus.NOT_FOUND,
            "Account with email " + email + " was not found"
        );
    }
}

public final class EmailAlreadyExistsException extends IdentityException {
    public EmailAlreadyExistsException(String email) {
        super(
            IdentityConstants.ErrorCodes.EMAIL_ALREADY_EXISTS,
            HttpStatus.CONFLICT,
            "Email " + email + " is already registered"
        );
    }
}

public final class InvalidCredentialsException extends IdentityException {
    public InvalidCredentialsException() {
        super(
            IdentityConstants.ErrorCodes.INVALID_CREDENTIALS,
            HttpStatus.UNAUTHORIZED,
            "Invalid email or password"
        );
    }
}
```

---

## 5. DTO Package: Request & Response Contracts

The `dto/` package is an explicit sub-package. To allow other modules and controllers to consume these contracts, its `package-info.java` exposes `@NamedInterface("dto")`.

```java
// src/main/java/com/eduerp/modules/identity/dto/package-info.java
@org.springframework.modulith.NamedInterface("dto")
package com.eduerp.modules.identity.dto;
```

Request and response contracts are immutable Java records with Bean Validation:

```java
// src/main/java/com/eduerp/modules/identity/dto/RegisterAccountRequest.java
package com.eduerp.modules.identity.dto;

import com.eduerp.modules.identity.IdentityConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RegisterAccountRequest(
    @NotBlank
    @Email
    @Size(max = IdentityConstants.Limits.MAX_EMAIL_LENGTH)
    String email,

    @NotBlank
    @Size(min = IdentityConstants.Limits.MIN_PASSWORD_LENGTH)
    String password,

    @NotBlank
    @Size(max = 100)
    String fullName,

    UUID branchId
) {}
```

```java
// src/main/java/com/eduerp/modules/identity/dto/AccountSummaryResponse.java
package com.eduerp.modules.identity.dto;

import com.eduerp.modules.identity.IdentityConstants.AccountStatus;
import java.time.Instant;
import java.util.UUID;

public record AccountSummaryResponse(
    UUID id,
    String email,
    String fullName,
    AccountStatus status,
    UUID branchId,
    Instant createdAt
) {}
```

---

## 6. JPA Entity (`internal/model/Account.java`)

Resides in `internal/model/`, completely hidden from external modules.

```java
// src/main/java/com/eduerp/modules/identity/internal/model/Account.java
package com.eduerp.modules.identity.internal.model;

import com.eduerp.modules.identity.IdentityConstants.AccountStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountStatus status;

    @Column
    private UUID branchId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Account() {} // JPA required

    public Account(String email, String passwordHash, String fullName, UUID branchId) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.branchId = branchId;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public AccountStatus getStatus() { return status; }
    public UUID getBranchId() { return branchId; }
    public Instant getCreatedAt() { return createdAt; }

    public void updateProfile(String fullName) {
        this.fullName = fullName;
        this.updatedAt = Instant.now();
    }

    public void suspend() {
        this.status = AccountStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
```

---

## 7. Repository Port (`internal/repository/AccountRepository.java`)

The port is `JpaRepository`. No hand-rolled abstraction.

```java
// src/main/java/com/eduerp/modules/identity/internal/repository/AccountRepository.java
package com.eduerp.modules.identity.internal.repository;

import com.eduerp.modules.identity.internal.model.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

---

## 8. Domain Rules (`internal/rules/IdentityRules.java`)

Pure business decisions: zero I/O, no database queries, no Spring annotations.

```java
// src/main/java/com/eduerp/modules/identity/internal/rules/IdentityRules.java
package com.eduerp.modules.identity.internal.rules;

import com.eduerp.modules.identity.IdentityConstants.AccountStatus;
import com.eduerp.modules.identity.internal.model.Account;

public final class IdentityRules {

    public boolean canAuthenticate(Account account) {
        return account != null && account.getStatus() == AccountStatus.ACTIVE;
    }

    public boolean canChangePassword(Account account) {
        return account != null && account.getStatus() != AccountStatus.SUSPENDED;
    }

    public boolean canTransferBranch(Account account) {
        return account != null && account.getStatus() == AccountStatus.ACTIVE;
    }
}
```

---

## 9. Transforms / Util (`internal/util/EmailNormalizer.java`)

Pure data transformations. Changes only when format specifications change.

```java
// src/main/java/com/eduerp/modules/identity/internal/util/EmailNormalizer.java
package com.eduerp.modules.identity.internal.util;

import java.util.Locale;

public final class EmailNormalizer {

    private EmailNormalizer() {}

    public static String normalize(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
```

---

## 10. Application Events (`IdentityEvents.java`)

Immutable event records published across module boundaries.

```java
// src/main/java/com/eduerp/modules/identity/IdentityEvents.java
package com.eduerp.modules.identity;

import java.time.Instant;
import java.util.UUID;

public final class IdentityEvents {

    private IdentityEvents() {}

    public record AccountRegistered(
        UUID accountId,
        String email,
        String fullName,
        UUID branchId,
        Instant occurredAt
    ) {}

    public record AccountSuspended(
        UUID accountId,
        String reason,
        Instant occurredAt
    ) {}
}
```

---

## 11. Use Cases (`usecase/RegisterAccount.java`)

One class = one use case. The transaction boundary via `@Transactional`. Orchestrates repository, rules, hashing, events, and cache.

```java
// src/main/java/com/eduerp/modules/identity/usecase/RegisterAccount.java
package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.EmailAlreadyExistsException;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.dto.AccountSummaryResponse;
import com.eduerp.modules.identity.dto.RegisterAccountRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.util.EmailNormalizer;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterAccount {

    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    public RegisterAccount(
        AccountRepository repository,
        PasswordEncoder passwordEncoder,
        ApplicationEventPublisher events
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    @Transactional
    public AccountSummaryResponse execute(RegisterAccountRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(request.email());

        if (repository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        Account account = new Account(
            normalizedEmail,
            passwordHash,
            request.fullName().trim(),
            request.branchId()
        );

        Account saved = repository.save(account);

        events.publishEvent(new IdentityEvents.AccountRegistered(
            saved.getId(),
            saved.getEmail(),
            saved.getFullName(),
            saved.getBranchId(),
            Instant.now()
        ));

        return toResponse(saved);
    }

    private AccountSummaryResponse toResponse(Account account) {
        return new AccountSummaryResponse(
            account.getId(),
            account.getEmail(),
            account.getFullName(),
            account.getStatus(),
            account.getBranchId(),
            account.getCreatedAt()
        );
    }
}
```

---

## 12. The Facade (`IdentityManagement.java`)

The **ONLY** class other modules are permitted to inject and call.

```java
// src/main/java/com/eduerp/modules/identity/IdentityManagement.java
package com.eduerp.modules.identity;

import com.eduerp.modules.identity.dto.AccountSummaryResponse;
import com.eduerp.modules.identity.internal.permission.PermissionCacheService;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IdentityManagement {

    private final AccountRepository accountRepository;
    private final PermissionCacheService permissionCache;

    IdentityManagement(
        AccountRepository accountRepository,
        PermissionCacheService permissionCache
    ) {
        this.accountRepository = accountRepository;
        this.permissionCache = permissionCache;
    }

    public Optional<AccountSummaryResponse> findAccountById(UUID accountId) {
        return accountRepository.findById(accountId)
            .map(acc -> new AccountSummaryResponse(
                acc.getId(),
                acc.getEmail(),
                acc.getFullName(),
                acc.getStatus(),
                acc.getBranchId(),
                acc.getCreatedAt()
            ));
    }

    public Set<String> getEffectivePermissions(UUID accountId) {
        return permissionCache.getEffectivePermissions(accountId);
    }

    public void evictPermissionCache(UUID accountId) {
        permissionCache.evict(accountId);
    }
}
```

---

## 13. HTTP Controller (`web/AccountController.java`)

Thin HTTP translation layer: takes DTOs, runs Bean Validation, delegates to use cases.

```java
// src/main/java/com/eduerp/modules/identity/web/AccountController.java
package com.eduerp.modules.identity.web;

import com.eduerp.modules.identity.dto.AccountSummaryResponse;
import com.eduerp.modules.identity.dto.RegisterAccountRequest;
import com.eduerp.modules.identity.usecase.RegisterAccount;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {

    private final RegisterAccount registerAccount;

    AccountController(RegisterAccount registerAccount) {
        this.registerAccount = registerAccount;
    }

    @PostMapping
    ResponseEntity<AccountSummaryResponse> register(@Valid @RequestBody RegisterAccountRequest request) {
        AccountSummaryResponse response = registerAccount.execute(request);
        return ResponseEntity
            .created(URI.create("/api/v1/accounts/" + response.id()))
            .body(response);
    }
}
```

---

## 14. Test Suite (Three Tiers of Testing)

### Tier 1: Pure Unit Test for Rules (Sub-millisecond)
```java
package com.eduerp.modules.identity.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.identity.internal.model.Account;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityRulesTest {

    private final IdentityRules rules = new IdentityRules();

    @Test
    void canAuthenticateReturnsTrueForActiveAccount() {
        Account account = new Account("test@eduerp.local", "hash", "Test User", UUID.randomUUID());
        assertThat(rules.canAuthenticate(account)).isTrue();
    }

    @Test
    void canAuthenticateReturnsFalseForSuspendedAccount() {
        Account account = new Account("test@eduerp.local", "hash", "Test User", UUID.randomUUID());
        account.suspend();
        assertThat(rules.canAuthenticate(account)).isFalse();
    }
}
```

### Tier 2: Mockito Use Case Test (No DB, fast)
```java
package com.eduerp.modules.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eduerp.modules.identity.EmailAlreadyExistsException;
import com.eduerp.modules.identity.dto.RegisterAccountRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegisterAccountTest {

    @Mock private AccountRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks private RegisterAccount useCase;

    @Test
    void throwsWhenEmailAlreadyExists() {
        when(repository.existsByEmail("taken@eduerp.local")).thenReturn(true);

        RegisterAccountRequest request = new RegisterAccountRequest(
            "taken@eduerp.local", "Password123!", "Taken User", UUID.randomUUID()
        );

        assertThatThrownBy(() -> useCase.execute(request))
            .isInstanceOf(EmailAlreadyExistsException.class);
    }
}
```

### Tier 3: Spring Modulith Boundary Verification
```java
package com.eduerp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void verifiesModularity() {
        ApplicationModules.of(EduErpApplication.class).verify();
    }
}
```
