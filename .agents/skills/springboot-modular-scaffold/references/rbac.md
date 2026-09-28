# RBAC: Roles, Permissions, and Scoped Access Control

Access control is a first-class business domain, not a scattered collection of string comparisons. In this architecture, **`modules.identity`** is the single source of truth for accounts, roles, permissions, and sessions.

---

## Core Principles

1. **Permissions over Roles**: Code checks *capabilities* (`hasAuthority('PERM:ACCOUNT:READ')`), never role names (`hasRole('ADMIN')`). Roles are merely admin-assigned bundles of permissions that can be modified at runtime without changing application code.
2. **Never Hardcode Role Strings**: Hardcoding `"ADMIN"` or `"MANAGER"` in controllers or services tightly couples business logic to arbitrary naming, breaking multi-tenant customization.
3. **Scoped Grants**: In enterprise applications (e.g. Education ERP), permissions are scoped:
   - `PERSONAL`: Can only view/edit one's own records.
   - `BRANCH`: Can view/edit records within one's assigned branch.
   - `ORGANIZATION`: Global access across all branches.

---

## Entity Schema (Scoped RBAC)

```
accounts (id, email, full_name, status, branch_id)
    │
    ├── account_roles (account_id, role_id, branch_id)
    │        │
    │        └── roles (id, code, name, description)
    │                 │
    │                 └── role_permissions (role_id, permission_id)
    │                             │
    │                             └── permissions (id, resource, action, scope)
    │
    └── account_groups (account_id, group_id)
             │
             └── permission_groups (id, name)
                      │
                      └── group_permissions (group_id, permission_id)
```

### Permission Model
```java
@Entity
@Table(name = "permissions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"resource", "action", "scope"})
})
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String resource; // e.g. "ACCOUNT", "ENROLLMENT", "GRADE"

    @Column(nullable = false, length = 30)
    private String action;   // e.g. "CREATE", "READ", "UPDATE", "DELETE", "APPROVE"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PermissionScope scope; // PERSONAL, BRANCH, ORGANIZATION
}
```

---

## The Permission Catalog in Constants

Resources and actions are compiled constants in `IdentityConstants`, never arbitrary user-entered strings:

```java
public final class IdentityConstants {

    public static final class Resources {
        private Resources() {}
        public static final String ACCOUNT = "ACCOUNT";
        public static final String ROLE = "ROLE";
        public static final String COURSE = "COURSE";
        public static final String ENROLLMENT = "ENROLLMENT";
    }

    public static final class Actions {
        private Actions() {}
        public static final String CREATE = "CREATE";
        public static final String READ = "READ";
        public static final String UPDATE = "UPDATE";
        public static final String DELETE = "DELETE";
        public static final String APPROVE = "APPROVE";
    }

    public static final class Authorities {
        private Authorities() {}
        public static final String PERMISSION_PREFIX = "PERM:";

        public static String of(String resource, String action) {
            return PERMISSION_PREFIX + resource + ":" + action;
        }
    }
}
```

---

## Effective Permission Calculation & Redis Caching

Resolving a user's permissions on every HTTP request by executing multi-table SQL joins (`accounts -> account_roles -> roles -> role_permissions -> permissions`) creates an immense database bottleneck.

### The Solution: Cached Effective Permissions
1. Calculate effective permissions once during login or upon first query.
2. Flatten into a `Set<String>` (e.g. `["PERM:ACCOUNT:READ:BRANCH", "PERM:ENROLLMENT:CREATE:ORGANIZATION"]`).
3. Cache in Redis under `CacheNamespaces.EFFECTIVE_PERMISSIONS` (`perm:account:<accountId>`) with a 24-hour TTL.
4. **Invalidate immediately** whenever the user's role or permission group assignment is modified via `IdentityManagement.evictPermissionCache(accountId)`.

```java
@Service
public class PermissionCacheService {

    private final StringRedisTemplate redis;
    private final EffectivePermissionCalculator calculator;

    public Set<String> getEffectivePermissions(UUID accountId) {
        String cacheKey = CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.EFFECTIVE_PERMISSIONS, accountId);
        Set<String> cached = redis.opsForSet().members(cacheKey);

        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        Set<String> computed = calculator.calculate(accountId);
        if (!computed.isEmpty()) {
            redis.opsForSet().add(cacheKey, computed.toArray(String[]::new));
            redis.expire(cacheKey, Duration.ofHours(24));
        }
        return computed;
    }

    public void evict(UUID accountId) {
        String cacheKey = CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.EFFECTIVE_PERMISSIONS, accountId);
        redis.delete(cacheKey);
    }
}
```

---

## Spring Security Method Security

Enable global method security in Spring Boot:

```java
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig {}
```

### 1. Simple Permission Check
```java
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    @GetMapping
    @PreAuthorize("hasAuthority('PERM:ACCOUNT:READ')")
    public PageResponse<AccountSummaryResponse> listAccounts(Pageable pageable) {
        return listAccountsUseCase.execute(pageable);
    }
}
```

### 2. Scoped Permission Check (Domain + Branch Evaluation)
Create a dedicated Spring bean `@perm` for SpEL evaluation:

```java
@Component("perm")
public class PermissionEvaluatorService {

    public boolean canAccessBranch(Authentication auth, UUID targetBranchId) {
        if (!(auth.getPrincipal() instanceof AccountPrincipal principal)) {
            return false;
        }

        // Organization-scoped permission grants access to all branches
        if (principal.hasPermission("ACCOUNT", "READ", PermissionScope.ORGANIZATION)) {
            return true;
        }

        // Branch-scoped permission requires branch matching
        if (principal.hasPermission("ACCOUNT", "READ", PermissionScope.BRANCH)) {
            return Objects.equals(principal.branchId(), targetBranchId);
        }

        return false;
    }
}
```

Usage in controllers or use cases:
```java
@GetMapping("/branches/{branchId}/students")
@PreAuthorize("@perm.canAccessBranch(authentication, #branchId)")
public List<StudentResponse> getStudentsByBranch(@PathVariable UUID branchId) {
    return getStudentsUseCase.execute(branchId);
}
```

---

## Anti-Patterns to Avoid

| Anti-Pattern | Why It Fails | Correct Architecture |
|---|---|---|
| `if (user.getRole().equals("ADMIN"))` | Role names cannot be customized or extended; hardcodes hierarchy. | `@PreAuthorize("hasAuthority('PERM:RESOURCE:ACTION')")` |
| Checking permissions in the JPA Repository | Binds database layer to HTTP request context / thread-locals. | Check in Controller or Use Case before calling repository. |
| Querying permissions from DB on every API call | Causes severe N+1 SQL queries and database overload under load. | Cache effective permissions in Redis, evicting on change. |
| Role strings declared loosely in controller files | Typos lead to silent authorization bypasses. | Compile-time constants in `IdentityConstants.Authorities`. |
