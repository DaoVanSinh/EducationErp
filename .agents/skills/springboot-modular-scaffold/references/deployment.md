# Deployment Reference

## Layered Dockerfile (Java 21 + Spring Boot 3.4)

Spring Boot's layered JAR layout separates application dependencies from application code. Dependencies change infrequently and are cached across builds, while application classes change on every commit.

```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw clean package -DskipTests -B
RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination target/extracted

# Runtime stage
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Add unprivileged user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy layers in order of frequency of change (least to most frequent)
COPY --from=builder /workspace/target/extracted/dependencies/ ./
COPY --from=builder /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/target/extracted/application/ ./

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dspring.threads.virtual.enabled=true"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
```

---

## Local Development: `compose.yaml`

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: eduerp-postgres
    environment:
      POSTGRES_DB: eduerp
      POSTGRES_USER: eduerp
      POSTGRES_PASSWORD: eduerp_password
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U eduerp -d eduerp"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: eduerp-redis
    ports:
      - "6379:6379"
    command: ["redis-server", "--save", "", "--appendonly", "no", "--maxmemory", "256mb", "--maxmemory-policy", "allkeys-lru"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  mailpit:
    image: axllent/mailpit:latest
    container_name: eduerp-mailpit
    ports:
      - "1025:1025" # SMTP port
      - "8025:8025" # Web UI
    restart: unless-stopped

volumes:
  pgdata:
```

---

## Environment Variable Matrix

Spring Boot's **relaxed binding** translates uppercase underscored environment variables to lowercase dot/dash properties:

| Environment Variable | Target Spring Property | Description | Production Example |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | JDBC connection string | `jdbc:postgresql://db.prod:5432/eduerp` |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | DB user | `eduerp_app` |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | DB password (from secrets manager) | `${SECRET_DB_PASSWORD}` |
| `SPRING_DATA_REDIS_HOST` | `spring.data.redis.host` | Redis host | `redis.prod` |
| `SPRING_DATA_REDIS_PORT` | `spring.data.redis.port` | Redis port | `6379` |
| `IDENTITY_JWT_SECRET` | `identity.jwt-secret` | 256-bit HMAC secret key | `v3ry-l0ng-s3cr3t-k3y-m1n-32-byt3s!!` |
| `IDENTITY_ACCESS_TOKEN_TTL` | `identity.access-token-ttl` | Duration | `15m` |
| `IDENTITY_SECURE_COOKIES` | `identity.secure-cookies` | HTTPS-only cookies | `true` |

---

## Database Migrations (Flyway)

All schema changes are versioned using Flyway in `src/main/resources/db/migration`:

```
src/main/resources/db/migration/
├── V1__init_core_and_identity.sql
├── V2__create_access_control_tables.sql
└── V3__create_organization_branches.sql
```

### Migration Rules:
1. **Never edit an applied migration**: Changing an already executed SQL script breaks checksum validation and blocks application startup.
2. **Backward-compatible changes**: Add columns with `NULL` or default values. Drop columns only after code that references them has been fully deployed.
3. **Hibernate Validation**: Keep `spring.jpa.hibernate.ddl-auto=validate` in production so Hibernate validates that JPA entity mappings strictly match Flyway tables.

---

## HikariCP Connection Pool Sizing

Default pool size (10 connections) is suitable for small workloads. For production:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      idle-timeout: 300000
      connection-timeout: 20000
      max-lifetime: 1200000
```

Formula for sizing connection pools:
$$\text{pool size} = T_n \times (\text{core count}) + \text{effective disk spindle count}$$
Do not oversize database pools — 20–30 connections per pod handle thousands of req/sec with HikariCP.

---

## Actuator Health Probes (Kubernetes)

Configure Kubernetes liveness and readiness probes in `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
```

Kubernetes Pod spec:
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 15
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```
