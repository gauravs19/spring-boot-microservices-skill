# Testing

A test strategy that gives real confidence without being slow or brittle. The guiding
idea: test at the lowest level that gives confidence, use real dependencies for
integration via Testcontainers, and mock sparingly.

## Table of contents
- [The test pyramid for Spring Boot](#the-test-pyramid-for-spring-boot)
- [Unit tests](#unit-tests)
- [Slice tests](#slice-tests)
- [Integration tests with Testcontainers](#integration-tests-with-testcontainers)
- [Testing messaging](#testing-messaging)
- [Contract testing](#contract-testing)
- [What good coverage looks like](#what-good-coverage-looks-like)

## The test pyramid for Spring Boot

- **Many** fast unit tests of business logic (no Spring context).
- **Some** slice tests that load a focused part of the context (`@WebMvcTest`,
  `@DataJpaTest`).
- **Fewer** full integration tests (`@SpringBootTest`) backed by real infrastructure
  via Testcontainers.
- **A few** end-to-end / contract tests at the edges.

Inverting this (mostly slow full-context tests, few unit tests) gives slow, flaky
suites that teams eventually ignore. Push logic down where it can be unit-tested.

## Unit tests

Test business logic as plain objects with JUnit 5 + AssertJ, no Spring context, no
database. This is where the bulk of your assertions about behavior live, and it runs in
milliseconds. Mock only the collaborators you own (with Mockito); don't mock what you
don't own or value objects. Keep the domain logic on domain objects precisely so it's
unit-testable without infrastructure.

## Slice tests

Spring Boot's test slices load only the relevant part of the context, so they're much
faster than a full boot:

- **`@WebMvcTest`** — the web layer only (controllers, JSON, validation, exception
  handling), with `MockMvc` and mocked services. Ideal for asserting status codes,
  request/response mapping, validation errors, and the Problem Details contract.
- **`@DataJpaTest`** — the persistence layer against a real database (see
  Testcontainers below), for repository queries, mappings, and catching N+1 by counting
  queries.
- **`@JsonTest`**, **`@RestClientTest`** — serialization and outbound client slices.

## Integration tests with Testcontainers

Test against **real** dependencies (the actual Postgres/Kafka/Redis version you run in
prod) in disposable Docker containers, rather than in-memory fakes (H2) that behave
differently and give false confidence. Spring Boot has first-class Testcontainers
support, and `@ServiceConnection` wires the container to Spring's config
automatically — no manual URL/credential plumbing:

```java
@SpringBootTest
@Testcontainers
class OrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvcTester mvc;   // or WebTestClient / TestRestTemplate

    @Test
    void placesOrder() { /* real DB, real migrations, real queries */ }
}
```

This is the single biggest lever for integration-test realism. Prefer it over H2 for
anything that touches the database. You can also reuse containers across tests and use
Spring Boot's Testcontainers-at-`main` support for a real local dev runtime.

## Testing messaging

For Kafka/RabbitMQ, use the corresponding Testcontainers module (a real broker) or, for
lighter tests, Spring Kafka's embedded broker. Assert the important async properties:
that the event is published (outbox relayed), that the consumer is **idempotent** (send
the same message twice, assert one effect), and that poison messages land in the DLQ.
These properties are exactly the ones that break in production, so test them explicitly.

## Contract testing

When services integrate, contract tests catch breaking changes before deploy without a
full end-to-end environment. **Spring Cloud Contract** (or Pact) lets the provider
publish a contract and generates tests on both sides, so the consumer's expectations and
the provider's behavior are verified independently. This is high-value in a
multi-service estate where a provider change can silently break a consumer. Adopt it as
the number of inter-service dependencies grows.

## What good coverage looks like

Coverage percentage is a weak proxy — 90% coverage of trivial getters proves nothing,
and a low number on critical logic is dangerous. Aim instead for: every business rule
has a unit test, every endpoint has a web-slice test including its failure/validation
paths, every non-trivial query has a `@DataJpaTest`, and the critical happy-path plus
key failure modes have a Testcontainers integration test. In review mode, the absence of
tests around core logic or a habit of mocking the database instead of using
Testcontainers are findings worth raising.
