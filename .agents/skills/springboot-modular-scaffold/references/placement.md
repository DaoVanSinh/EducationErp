# Placement

The recurring question when adding code isn't "what package convention exists for this?" but **who owns the concept**. If you can name the one module that would break if this code disappeared, that's where it goes.

## Quick table

| Code | Goes to | Not |
|---|---|---|
| `OrderStatus` enum, `MAX_SEATS` limit, an error-code enum | `<module>/<Module>Constants.java`, nested inside one class | A bare `public static final int` scattered in whatever file first needed it |
| `OrderNotFoundException` | `<module>/<Module>Exception.java` (or its own file once the module has several) | `core/exception/` — that file is for the *base* hierarchy only |
| `jwt.secret`, `cache.ttl` scoped to one module | `<module>/<Module>Properties.java`, `@ConfigurationProperties(prefix = "<module>")` | `application.yml`'s root-level keys |
| `spring.datasource.url`, `server.port`, CORS origins | `application.yml` root, bound in `core/config/` | A per-module properties class |
| `canCancelOrder(order, actor)` — a decision that changes when the business changes | `<module>/<Module>Rules.java` | Inline in the controller or the use case |
| `normalizeEmail(raw)` — a transform that doesn't encode a decision | `<module>/util/` | `<Module>Rules.java` — keep rules small and heavily tested, keep utils boring |
| `Page<T>`/`Pageable` wrapping, `ProblemDetail` construction | `core/` — mechanism, no business concept | Reinventing them per module |
| `JpaRepository<X, ID>` subinterface | Inside `<module>/`, next to the entity it queries | A shared `repository/` package at the root |
| A permission enum 3+ modules need to check | `shared/SharedConstants.java` — promotion, see below | A first draft in `shared` before a second module needs it |

`core/exception/` naming a domain entity (`core/exception/OrderNotFoundException.java`) is the earliest visible sign the boundary has leaked — that class belongs in `order/`. So is any `Utils.java`/`Helpers.java` sitting at the application root instead of inside the module whose concept it serves.

## When duplication is correct

Two modules independently defining `PageRequest` validation, or both needing to normalize a phone number the same way, is not automatically a reason to create a shared abstraction. Promote a concept into `shared/` only when **all** of these hold:

1. **Three or more modules** need the exact same thing (two is often coincidence, not a shared concept yet).
2. **It's stable** — the concept hasn't changed shape across the last few times a module touched it.
3. **Divergence would be a bug**, not a legitimate difference — e.g. every module's definition of "an active user" really must agree, versus every module happening to both have a field called `status` that means something different per module.

Fewer than three: leave the duplication where it is. Duplication that's about to diverge is cheaper to carry than a shared abstraction that has to special-case its third caller.

## `shared/` shape

Same shape as any module — its own constants class, its own exceptions if it needs any — and the same size discipline (rule #14 in `SKILL.md`). The moment `shared/` wants an `@Entity` or a `@RestController`, what's living there was a domain concept with an owner all along, not something genuinely shared; move it back into a real module.

Declare the one-way relationship either via `@Modulithic(sharedModules = {"shared"})` on the application class (lets every module depend on it without an explicit `allowedDependencies` entry) or, if `shared` needs to be restricted to a subset of modules, via each dependent module's `allowedDependencies = "shared"`. Either way, `shared` itself must never `import com.acme.shop.<domain module>.*` — `ApplicationModules.verify()` catches the cycle if it ever happens.
