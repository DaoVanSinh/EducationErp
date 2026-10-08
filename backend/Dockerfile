# ── Build stage ───────────────────────────────────────────────────────────
# Không có mvnw trong repo (xem AGENTS.md/CLAUDE.md) nên build image chứa sẵn Maven thay vì gọi
# wrapper. Copy mỗi pom.xml trước rồi go-offline: layer phụ thuộc chỉ build lại khi pom.xml đổi,
# không phải mỗi lần sửa một dòng code.
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B clean package -DskipTests
RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination target/extracted

# ── Runtime stage ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy theo thứ tự ít đổi -> đổi nhiều: dependencies gần như không đổi giữa các lần build, code của
# chính app (application/) đổi ở mọi commit — layer nào không đổi thì Docker cache lại, không build lại.
COPY --from=builder /workspace/target/extracted/dependencies/ ./
COPY --from=builder /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/target/extracted/application/ ./

USER appuser

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dspring.threads.virtual.enabled=true"

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
