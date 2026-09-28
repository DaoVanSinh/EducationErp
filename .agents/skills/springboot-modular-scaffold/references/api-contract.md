# API Contract Reference

## Wire Format: Strict camelCase

Every REST API contract is serialized in **camelCase**.
- Jackson converts record component names and bean properties directly to camelCase by default.
- Never let SQL database column names (`account_id`, `created_at`) leak through to JSON.

```json
{
  "id": "e6a0d241-766b-4e8c-897b-cf1088c4b2a1",
  "email": "student@eduerp.local",
  "fullName": "Nguyen Van A",
  "status": "ACTIVE",
  "createdAt": "2026-09-28T14:30:00Z"
}
```

---

## Error Envelope: RFC 9457 `ProblemDetail`

All error responses adhere to the standard **RFC 9457 (Problem Details for HTTP APIs)** specification, natively supported in Spring Boot 3+.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Email student@eduerp.local is already registered",
  "instance": "/api/v1/accounts",
  "errorCode": "IDENTITY_EMAIL_ALREADY_EXISTS",
  "timestamp": "2026-09-28T14:30:05.123Z",
  "invalidParams": []
}
```

### Key Fields:
- **`status`**: Standard HTTP status code (400, 401, 403, 404, 409, 422, 500).
- **`errorCode`**: Stable, machine-readable string key (e.g. `IDENTITY_EMAIL_ALREADY_EXISTS`). The frontend translation system (`i18next`) keys on this value.
- **`detail`**: Human-readable debugging message in English. The frontend should **never** display raw `detail` directly to end-users.
- **`invalidParams`**: List of field-level validation errors (if applicable).

---

## Central Error Mapping (`GlobalExceptionHandler.java`)

Located in `core/exception/GlobalExceptionHandler.java`:

```java
package com.eduerp.core.exception;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ProblemDetail> handleAppException(AppException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed for one or more fields"
        );
        problem.setProperty("errorCode", "VALIDATION_FAILED");
        problem.setProperty("timestamp", Instant.now());

        List<InvalidParam> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> new InvalidParam(err.getField(), err.getDefaultMessage()))
            .toList();

        problem.setProperty("invalidParams", errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    public record InvalidParam(String field, String reason) {}
}
```

---

## Pagination Format: `PageResponse<T>`

Never return raw unbounded database queries. Every list endpoint accepts a `Pageable` and returns a standardized `PageResponse<T>` record:

```java
package com.eduerp.core.pagination;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
    List<T> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext(),
            page.hasPrevious()
        );
    }
}
```

HTTP Wire Shape:
```json
{
  "items": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "hasNext": true,
  "hasPrevious": false
}
```

---

## Server-Sent Events (SSE)

For real-time streaming notifications or asynchronous task updates, use Spring's `SseEmitter`:

```java
@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamEvents(@AuthenticationPrincipal AccountPrincipal principal) {
    SseEmitter emitter = new SseEmitter(60_000L); // 60s timeout
    notificationService.registerEmitter(principal.id(), emitter);

    emitter.onCompletion(() -> notificationService.removeEmitter(principal.id(), emitter));
    emitter.onTimeout(() -> notificationService.removeEmitter(principal.id(), emitter));
    return emitter;
}
```

Payload format adheres to standard SSE lines:
```text
event: notification
data: {"id":"123","title":"Grade Published","timestamp":"2026-09-28T14:35:00Z"}

```
