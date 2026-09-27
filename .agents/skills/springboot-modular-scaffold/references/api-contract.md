# API contract

## Error shape — `ProblemDetail` (RFC 7807), built into Spring 6 / Spring Boot 3

Don't invent a bespoke error envelope — Spring already ships one, and every client library that understands `application/problem+json` understands it for free.

```java
package com.acme.shop.core.exception;

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    ProblemDetail handle(AppException ex) {
        var detail = ProblemDetail.forStatus(ex.getStatus());
        detail.setTitle(ex.getErrorCode());
        detail.setDetail(ex.getMessage());
        detail.setProperty("errorCode", ex.getErrorCode());   // stable, i18n-key-shaped, machine-matchable
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setProperty("errors", ex.getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage())).toList());
        return detail;
    }
}
```

`errorCode` (e.g. `IDENTITY_USER_NOT_FOUND`) is the same kind of stable, i18n-lookup-friendly string `fastapi-modular-scaffold`'s api-contract.md specifies — resolve its human-facing text via `MessageSource` keyed on that same string, not by hardcoding English into `AppException`.

## Wire format

Jackson serializes Java's own `camelCase` field names as-is — unlike the Python side, there's no `alias_generator`/`to_camel` step to remember, because the wire format already matches the language's own naming convention. The one thing worth being deliberate about: don't let `@Entity` fields leak (see SKILL.md's "never return an entity" rule) — DTO records control the wire shape independently of the JPA column names.

## Pagination

Use Spring Data's `Page<T>`/`Pageable` rather than a hand-rolled envelope — it already carries `totalElements`, `totalPages`, `number`, `size`, and Spring MVC binds `?page=&size=&sort=` from a plain `Pageable` controller parameter automatically, no extra annotation required:

```java
@GetMapping
Page<UserResponse> list(Pageable pageable) {
    return identity.list(pageable).map(UserResponse::from);
}
```

If the project also generates OpenAPI docs via `springdoc-openapi-starter-webmvc-ui`, add `@ParameterObject` (from that same dependency, `org.springdoc.core.annotations.ParameterObject`) so `page`/`size`/`sort` render as individual query parameters instead of one opaque `Pageable` schema — it's a docs-quality annotation, not something Spring MVC's binding itself requires.

## Versioning

Prefer a URI prefix (`/api/v1/...`) over a media-type/header scheme for a modular monolith with one deployable — it's visible in logs, curl-able without extra headers, and easy to route in a gateway later if the monolith is ever split. Reserve content negotiation for the rare endpoint that genuinely needs to serve two representations of the same resource simultaneously.

## SSE / streaming

`SseEmitter` or, on Spring 6+, a controller method returning `Flux<ServerSentEvent<T>>` (WebFlux) if the module is reactive; a plain servlet module can still stream via `SseEmitter` without adopting WebFlux for the whole app. Bind correlation-id logging (see `architecture.md#correlation-id-logging`) into the emitter's error/completion callbacks too — a dropped SSE connection should still show up in logs under the request that opened it.
