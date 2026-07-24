# REST API Design

The API contract is the most expensive thing to change once clients depend on it, so
design it deliberately. This covers resource modeling, DTOs, validation, the error
model, pagination, versioning, and documentation.

## Table of contents
- [Resource modeling](#resource-modeling)
- [DTOs, never entities](#dtos-never-entities)
- [Validation](#validation)
- [Error model: Problem Details (RFC 9457)](#error-model-problem-details-rfc-9457)
- [Global exception handling](#global-exception-handling)
- [Pagination, filtering, sorting](#pagination-filtering-sorting)
- [API versioning](#api-versioning)
- [Idempotency & safe methods](#idempotency--safe-methods)
- [Documentation (OpenAPI)](#documentation-openapi)

## Resource modeling

Model around nouns (resources) and use HTTP verbs for actions:
`GET /orders`, `POST /orders`, `GET /orders/{id}`, `PATCH /orders/{id}`,
`DELETE /orders/{id}`. Use plural nouns, hierarchy for containment
(`/orders/{id}/items`), and keep verbs out of paths (`/createOrder` is a smell).
Return correct status codes: 201 + `Location` on create, 200/204 appropriately, 400
for client input errors, 404 for missing resources, 409 for conflicts, 422 for
semantically-invalid input, 5xx only for genuine server faults.

Group endpoints by use-case-oriented resources rather than one giant controller;
aggregate-per-controller keeps each class cohesive.

## DTOs, never entities

Expose request/response DTOs (Java `record`s are ideal — immutable, concise), and map
to/from domain/JPA entities inside the service or a dedicated mapper. Reasons this
matters, not just convention:

- **Decoupling** — your storage schema can evolve without breaking clients, and vice
  versa.
- **Safety** — you don't accidentally expose internal fields, and you avoid
  Jackson serializing lazy JPA associations (a classic source of
  `LazyInitializationException` and N+1 queries at serialization time).
- **Clarity** — request and response shapes are explicit and validated.

Keep mapping simple and explicit (hand-written mappers or MapStruct). Don't reach for
a mapping framework until the boilerplate genuinely hurts.

## Validation

Use Jakarta Bean Validation on request DTOs and trigger it with `@Valid`:

```java
public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<OrderLine> lines) {}

@PostMapping
ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req) { ... }
```

Validation failures should become a clean 400/422 with field-level detail via the
global handler below — never a raw stack trace. For cross-field or business rules that
Bean Validation can't express cleanly, validate in the service layer and throw a
domain exception the handler knows how to translate.

## Error model: Problem Details (RFC 9457)

Standardize errors on **Problem Details** (RFC 9457, the successor to 7807), which
Spring supports natively via `ProblemDetail` and `ErrorResponse`. A consistent,
machine-readable error body across every service is a huge quality-of-life win for
clients and for debugging. Enable Spring's built-in support:

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

A problem response looks like:
```json
{
  "type": "https://errors.example.com/order-not-found",
  "title": "Order not found",
  "status": 404,
  "detail": "No order with id 4821",
  "instance": "/orders/4821"
}
```

Never leak stack traces, SQL, or internal class names to clients — that's both a poor
experience and an information-disclosure risk.

## Global exception handling

Centralize exception-to-response translation in one `@RestControllerAdvice` so
controllers stay clean and behavior is consistent:

```java
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handleNotFound(OrderNotFoundException ex) {
        var pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://errors.example.com/order-not-found"));
        pd.setTitle("Order not found");
        return pd;
    }
    // handle validation, conflicts, and a catch-all that logs + returns a generic 500
}
```

The catch-all must log the real exception (with trace ID) server-side while returning
a generic problem to the client. Swallowing exceptions or returning bare 500s with no
logging is a review Critical.

## Pagination, filtering, sorting

Never return unbounded collections — an endpoint that returns "all orders" is a
latency and memory incident waiting to happen. Use Spring Data's `Pageable`:

```java
@GetMapping
Page<OrderResponse> list(Pageable pageable) { ... }   // ?page=0&size=20&sort=createdAt,desc
```

Set a sane default and maximum page size. For large datasets or infinite scroll,
prefer keyset/cursor pagination over offset pagination, which degrades on deep pages.

## API versioning

Decide a versioning strategy up front, since you'll eventually make a breaking change.
Common approaches: URI path (`/v1/orders`), a custom header, or content negotiation via
media type. Spring Framework 7 / Boot 4 add **built-in API versioning** support
(configurable resolution and `@RequestMapping(version = ...)`), which is worth using
on the current generation. Whichever you pick, be consistent across the estate, and
treat additive changes (new optional fields) as non-breaking so you version rarely.

## Idempotency & safe methods

Honor HTTP semantics: `GET`/`HEAD` are safe (no side effects), `PUT`/`DELETE` are
idempotent, `POST` is neither by default. For `POST`s that must not double-apply (e.g.
payments) under client retries, support an `Idempotency-Key` header and dedupe
server-side. This ties into resilient retries — see `resilience-and-communication.md`.

## Documentation (OpenAPI)

Generate an OpenAPI 3 spec from the code with **springdoc-openapi**, which also serves
Swagger UI. Keep the spec accurate by driving it from annotations and DTOs rather than
maintaining it by hand. A published, current contract is what lets other teams
integrate without reading your source — treat it as a deliverable, and consider
contract testing against it (see `testing.md`).
