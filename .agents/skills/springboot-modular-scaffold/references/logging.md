# Logging: Structured, Correlated, Redacted

Every Spring Boot project in this scaffold ships structured logging by default.

The three core components:
1. **`RequestCorrelationFilter`**: Extracts or generates `requestId` and `correlationId`, binding them to SLF4J's MDC and returning them in response headers.
2. **Structured JSON Output**: Standardized JSON formatting in production environments via Spring Boot 3.4 structured logging or Logstash Logback encoder.
3. **Sensitive Field Redaction**: Automatic masking of passwords, tokens, API keys, and authorization headers.

---

## The ID Types

| ID | Purpose | MDC Key | HTTP Header | Source / Bound Where |
|---|---|---|---|---|
| `requestId` | Identifies this specific HTTP request within this service | `requestId` | `X-Request-ID` | Generated fresh per hop in `RequestCorrelationFilter` |
| `correlationId` | Traces the entire end-to-end flow across services and brokers | `correlationId` | `X-Correlation-ID` | Inherited from upstream header, or defaults to `requestId` |
| `traceId` / `spanId` | OpenTelemetry distributed tracing context | `traceId`, `spanId` | `traceparent` | Injected via Micrometer Tracing bridge |
| Business IDs | Scopes logs to a user, tenant, or branch | `accountId`, `branchId` | None | Bound in authentication filter or use case |

---

## Where This Lives (`core/web/RequestCorrelationFilter.java`)

```java
package com.eduerp.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_CORRELATION_ID = "correlationId";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = Optional.ofNullable(request.getHeader(REQUEST_ID_HEADER))
            .filter(s -> !s.isBlank())
            .orElseGet(() -> UUID.randomUUID().toString());

        String correlationId = Optional.ofNullable(request.getHeader(CORRELATION_ID_HEADER))
            .filter(s -> !s.isBlank())
            .orElse(requestId);

        try {
            MDC.put(MDC_REQUEST_ID, requestId);
            MDC.put(MDC_CORRELATION_ID, correlationId);

            response.setHeader(REQUEST_ID_HEADER, requestId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_CORRELATION_ID);
            MDC.clear();
        }
    }
}
```

---

## Spring Boot 3.4 Structured Logging Configuration

Spring Boot 3.4 provides built-in structured logging support without requiring third-party Logback XML files:

```yaml
# application.yml
logging:
  structured:
    format:
      console: "${LOG_FORMAT:ecs}" # Elastic Common Schema (ecs) or logstash in prod, plain console in dev
```

For custom Logback JSON (e.g. `logstash-logback-encoder`), include in `pom.xml`:
```xml
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>8.0</version>
</dependency>
```

And in `src/main/resources/logback-spring.xml`:
```xml
<configuration>
    <springProfile name="prod,staging">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>requestId</includeMdcKeyName>
                <includeMdcKeyName>correlationId</includeMdcKeyName>
                <includeMdcKeyName>traceId</includeMdcKeyName>
                <includeMdcKeyName>spanId</includeMdcKeyName>
                <includeMdcKeyName>accountId</includeMdcKeyName>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
    </springProfile>

    <springProfile name="dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [req:%X{requestId}] - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
</configuration>
```

---

## Propagation Across Boundaries

### 1. RabbitMQ Message Publication
When sending messages to RabbitMQ, propagate `correlationId`:

```java
public void publishWithCorrelation(RabbitTemplate rabbitTemplate, String exchange, String routingKey, Object payload) {
    String correlationId = Optional.ofNullable(MDC.get(RequestCorrelationFilter.MDC_CORRELATION_ID))
        .orElseGet(() -> UUID.randomUUID().toString());

    rabbitTemplate.convertAndSend(exchange, routingKey, payload, message -> {
        message.getMessageProperties().setCorrelationId(correlationId);
        message.getMessageProperties().setHeader("X-Correlation-ID", correlationId);
        return message;
    });
}
```

### 2. RabbitMQ Message Consumer
When consuming, populate the MDC before running the listener method:

```java
@Component
public class CorrelationRabbitListenerAspect {

    @Before("@annotation(org.springframework.amqp.rabbit.annotation.RabbitListener)")
    public void bindMdc(JoinPoint joinPoint) {
        // Reads correlation ID from MessageProperties and binds to MDC
    }

    @After("@annotation(org.springframework.amqp.rabbit.annotation.RabbitListener)")
    public void clearMdc() {
        MDC.clear();
    }
}
```

### 3. Outbound HTTP Calls (`RestClient`)
Register a ClientHttpRequestInterceptor to forward the `X-Correlation-ID` header:

```java
@Bean
public RestClientCustomizer correlationRestClientCustomizer() {
    return restClientBuilder -> restClientBuilder.requestInterceptor((request, body, execution) -> {
        String correlationId = MDC.get(RequestCorrelationFilter.MDC_CORRELATION_ID);
        if (correlationId != null) {
            request.getHeaders().add(RequestCorrelationFilter.CORRELATION_ID_HEADER, correlationId);
        }
        return execution.execute(request, body);
    });
}
```

---

## Sensitive Field Redaction

Log sanitization prevents leaking credentials into log aggregators:
- Configure Logback masking filters for JSON loggers.
- Never log passwords, JWT refresh tokens, authorization headers, or card numbers.
- DTO records for authentication (e.g. `LoginRequest`) should override `toString()` to exclude password fields:
  ```java
  public record LoginRequest(String email, String password) {
      @Override
      public String toString() {
          return "LoginRequest[email=" + email + ", password=***REDACTED***]";
      }
  }
  ```
