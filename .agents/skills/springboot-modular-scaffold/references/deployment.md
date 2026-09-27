# Deployment

## Profiles instead of `.env` files

Spring's profile mechanism (`application.yml` + `application-{profile}.yml`, activated via `SPRING_PROFILES_ACTIVE`) replaces the `.env` matrix `fastapi-modular-scaffold` uses — one base file for shared defaults, one override file per environment:

```
src/main/resources/
├── application.yml            defaults + ${ENV_VAR} placeholders, no secrets
├── application-dev.yml        local Postgres/Redis/RabbitMQ on localhost
├── application-staging.yml
└── application-prod.yml
```

Secrets stay out of every one of these files — inject them as environment variables (`IDENTITY_JWT_SECRET`, `SPRING_DATASOURCE_PASSWORD`) at deploy time, matching the module-prefixed env var rule from `SKILL.md`.

## Layered Docker image

Spring Boot's built-in `layertools` splits the jar into layers (dependencies, resources, application classes) so a code change only invalidates the top layer, not the whole image:

```dockerfile
FROM eclipse-temurin:21-jre AS build
WORKDIR /app
COPY target/shop.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

Enable Java 21 virtual threads (`spring.threads.virtual.enabled=true`) before reaching for a reactive rewrite — it gets most of the throughput benefit of non-blocking I/O for a servlet-stack app with zero code changes, as long as nothing synchronizes on a lock across a blocking call.

## Local development — `compose.yaml`

```yaml
services:
  postgres:
    image: postgres:16
    environment: { POSTGRES_DB: shop, POSTGRES_PASSWORD: shop }
    ports: ["5432:5432"]
  redis:
    image: redis:7
    ports: ["6379:6379"]
  rabbitmq:
    image: rabbitmq:3-management
    ports: ["5672:5672", "15672:15672"]
```

## Migrations

Flyway (`spring-boot-starter-data-jpa` + `flyway-core`) with one migration folder, `db/migration/`, versioned `V{n}__description.sql` — each domain module contributes its own migrations for the tables it owns (rule #3: one table, one owning module), but they share the single migration history table since this is one deployable, not one database per module.

## Testcontainers for integration tests

`@ApplicationModuleTest` and `@SpringBootTest` integration tests use Testcontainers instead of an in-memory H2 substitute — H2's SQL dialect drift from real Postgres is exactly the kind of "tests passed, prod broke" gap worth avoiding:

```java
@Testcontainers
@SpringBootTest
class IdentityControllerIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
}
```

`@ServiceConnection` (Spring Boot 3.1+) auto-wires `spring.datasource.url`/`username`/`password`/`driver-class-name` from the container in one annotation — prefer it over hand-registering individual properties with `@DynamicPropertySource`, which is easy to leave incomplete (username/password silently falling back to the image's defaults instead of being set deliberately).

## Actuator

`spring-boot-starter-actuator` + `spring-modulith-starter-insight` (bundles `spring-modulith-actuator` and `spring-modulith-observability` — check the Spring Modulith BOM for your version if the starter artifact id has changed) exposes the application module structure itself as an actuator endpoint (`/actuator/modulith`) alongside the usual `/actuator/health`, `/actuator/metrics` — useful for confirming in a running system that the boundaries `ModularityTests` verified at build time are the ones actually deployed.
