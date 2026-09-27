# Pre-production checklist

Walk this before calling a module (or the whole project) done.

**Structure**
- [ ] `ModularityTests.verify()` passes — no module reaches into another's internals
- [ ] Every domain module has a single public facade class; everything else is package-private or under `<module>.internal`
- [ ] No table is queried (JPA repository, native query, or join) from outside the module that owns it
- [ ] `shared/` contains only concepts that cleared the three-module promotion bar, and never imports a domain module

**Layers**
- [ ] Rules classes (`<Module>Rules`) have no `@Component`/`@Service` if they don't need DI, and no Spring import at all if they don't need one — they should be instantiable with `new` in a plain JUnit test
- [ ] Use cases are one class, one public `@Transactional` method
- [ ] Controllers contain no business logic — only request/response translation
- [ ] No `@Entity` is returned from a controller — DTO records only

**Config and secrets**
- [ ] Every module-owned setting uses `@ConfigurationProperties(prefix = "<module>")`, not a root-level key
- [ ] No secret is committed in `application*.yml` — all injected via environment variables at deploy time

**Data and caching**
- [ ] Cache keys are built only in `infra/cache/<Entity>CacheKeys`, never inline at the call site
- [ ] Cache eviction happens after commit, not before
- [ ] If RabbitMQ/outbox is in use: consumers are idempotent against redelivery

**Testing**
- [ ] Each module has all three tiers: a no-Spring-context rules test, a Mockito-fake use-case test, and one `@SpringBootTest`/Testcontainers integration test through the real HTTP surface
- [ ] `mvn verify` runs Testcontainers-backed tests in CI, not just on a developer's machine

**Observability**
- [ ] Every log line inside a request carries `requestId` and `correlationId` via MDC
- [ ] Sensitive fields (`password`, `token`, `authorization`, `secret`, `apiKey`, `creditCard`) are redacted in logs, not just in responses

**Build**
- [ ] `mvn -q package`, `mvn -q test -Dtest=ModularityTests`, and `mvn -q verify` all pass before handing over
