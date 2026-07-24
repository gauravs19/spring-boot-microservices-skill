---
name: spring-boot-microservices
description: >-
  Design, scaffold, and review modern Java Spring Boot microservices. Use this
  skill whenever the user is working on a Spring Boot backend, Java microservice,
  REST API in Java, Spring Cloud system, or asks to "design a service", "scaffold
  a Spring Boot project", "set up a microservice", "review my Spring Boot code",
  "add resilience / a gateway / config server / tracing", "containerize a Java
  service", or "is this service production-ready". Trigger it even when the user
  never says the word "microservice" — any Spring Boot / Spring Cloud / Jakarta
  EE / Spring Data / Spring Security / Resilience4j / Micrometer / Testcontainers
  work counts. Targets the current GA generation (Spring Boot 4.x on Spring
  Framework 7, Java 25 LTS) with Maven and Gradle. Do NOT use it for non-Spring
  Java, Android, Kotlin-only, or pure frontend work.
---

# Spring Boot Microservices

A practitioner's skill for building and reviewing **production-grade** Spring Boot
microservices the way strong teams do it in 2026: current Spring generation,
Java 21+/25 LTS, virtual threads, first-class observability, resilience, and
container-native deployment. It covers three jobs, and you pick the one the user
actually needs:

- **Design** — shape the service and the system: boundaries, API contracts, data
  ownership, communication style, cross-cutting concerns.
- **Scaffold / build** — generate a correct, modern project and implement features
  inside it with the right patterns.
- **Review / audit** — read an existing service and judge it against modern
  standards, producing prioritized, actionable findings.

Most real requests blend these. Read the request, decide which mode dominates,
and route to the right workflow below. Keep this file in context; **load a
`references/` file only when the task actually reaches that topic** — that is how
this skill stays exhaustive without flooding the context window.

---

## First: orient before you act

Before writing or judging anything, establish three things. Getting these wrong is
the single biggest cause of advice that is technically correct but useless to the
user, so spend a moment here even under time pressure.

1. **Which mode** — design, scaffold, or review. If ambiguous, ask one short
   question rather than guessing; the workflows diverge sharply.
2. **The version generation** — this decides syntax, package names, and available
   features. Do not assume. Check `pom.xml` / `build.gradle(.kts)` and the JDK.
   See "Version policy" below.
3. **The architecture context** — is this a greenfield service, one service inside
   an existing estate, or a modular monolith that may split later? Advice that
   ignores the surrounding system (shared libraries, existing gateway, existing
   auth) creates friction, not value.

For an existing codebase, actually read the build file, the main application
class, a representative controller/service/repository, and the config
(`application.yml`) before forming an opinion. Modern-standards advice given
without reading the code reads as generic and erodes trust.

---

## Version policy (read this before quoting any version)

"Modern" is a moving target, and quoting a stale or mismatched version is worse
than quoting none. Follow these rules:

- **Default generation:** Spring Boot **4.x** on **Spring Framework 7**, built and
  run on **Java 25 (LTS)**. Java 21 (LTS) is the acceptable floor; treat anything
  below 21 as legacy that should be planned off.
- **Conservative baseline:** Spring Boot **3.5.x** is still fully supported and a
  reasonable choice for teams not ready for the 4.x jump. When you see it, work
  *with* it — don't reflexively push an upgrade unless the user asked.
- **Namespace:** the current generation is **Jakarta EE** — imports are
  `jakarta.*` (e.g. `jakarta.persistence`, `jakarta.validation`), never
  `javax.*`. Seeing `javax.*` in a supposedly-modern service is itself a finding.
- **Spring Cloud:** never choose a Spring Cloud version independently. Each Spring
  Boot generation pins a specific Spring Cloud **release train**; picking
  mismatched versions is a classic, painful bug. Always resolve the train from the
  official compatibility matrix for the project's exact Boot version, and let the
  BOM manage it. See `references/spring-cloud-infra.md`.
- **When unsure of an exact minor version, say so** and point to the build file or
  the compatibility matrix rather than inventing a number. Being honest about a
  version you can't verify is more useful than confident precision that's wrong.

---

## Mode 1 — Design

Use when the user is deciding *what to build* or *how to structure it* before (or
independent of) writing code: service boundaries, API shape, data ownership,
sync-vs-async communication, or "should this even be a separate service".

Work through these, but only as deep as the request needs:

1. **Boundaries first.** Draw service boundaries around business capabilities and
   data ownership, not around technical layers or team org charts. A service that
   can't own its data and make decisions without synchronously calling three others
   isn't a microservice — it's a distributed monolith, which is the worst of both
   worlds. If the domain isn't clearly decomposed yet, strongly consider a **modular
   monolith** (Spring Modulith) first and split later; premature splitting is the
   most expensive mistake in this space. See `references/architecture-and-design.md`.
2. **API contract.** Define the external contract deliberately — resource model,
   error model (Problem Details / RFC 9457), pagination, versioning strategy — before
   implementation, because the contract is the expensive-to-change part. See
   `references/rest-api-design.md`.
3. **Communication style.** Choose synchronous (REST/HTTP, gRPC) vs asynchronous
   (events over Kafka/RabbitMQ) per interaction, and justify it. Default to
   async/event-driven for cross-service state propagation to avoid tight temporal
   coupling; use sync for genuine request/response. See
   `references/resilience-and-communication.md` and `references/messaging-and-events.md`.
4. **Data ownership & consistency.** One service owns each piece of data. Decide how
   consistency is maintained across services (sagas, outbox pattern, eventual
   consistency) — distributed transactions across services are an anti-pattern. See
   `references/persistence-and-data.md`.
5. **Cross-cutting concerns as platform, not per-service code.** Auth, config,
   observability, and resilience should be consistent across the estate. Decide
   where they live (gateway, shared starter, service mesh, sidecar).

Deliverable: a concise design writeup or ADR. If the user wants diagrams or a
formal architecture document, the separate `enterprise-architecture` skill is the
better tool — hand off rather than reinventing C4/ADR machinery here.

---

## Mode 2 — Scaffold / build

Use when the user wants a new project created, or a feature implemented inside an
existing one.

**For a brand-new project:**

1. Confirm build tool (Maven or Gradle) and Java version if not obvious; both are
   fully supported here. Default to Java 25 LTS.
2. Prefer generating the base from **Spring Initializr** (`start.spring.io`) rather
   than hand-writing a build file from memory — it guarantees a coherent, current
   dependency set. Then adjust. `references/project-setup.md` has the correct
   dependency choices, ready-to-use `pom.xml` / `build.gradle.kts` templates in
   `assets/templates/`, and the standard package/layer structure.
3. Wire the non-negotiable baseline every service needs: Actuator with K8s
   health probes, structured logging, externalized config, a sane error handler,
   and virtual threads enabled. `references/project-setup.md` and
   `references/observability.md`.

**For a feature inside an existing project:**

1. **Match the surrounding code.** Read neighboring classes first and follow the
   project's existing package layout, naming, and idioms. A technically-superior
   pattern that clashes with the codebase's conventions is the wrong pattern here —
   consistency beats personal preference.
2. Implement the vertical slice (controller → service → repository, or the reactive
   equivalent) with validation, error handling, tests, and observability included —
   not bolted on later.
3. Write the tests as you go using Testcontainers for anything touching a real
   dependency (DB, broker). See `references/testing.md`.

Topic-specific guidance lives in the reference files — load the one that matches
what you're implementing:

| If you're working on... | Read |
|---|---|
| Project layout, build files, dependencies, profiles | `references/project-setup.md`, `references/configuration-and-profiles.md` |
| REST controllers, DTOs, validation, errors, versioning | `references/rest-api-design.md` |
| JPA/Hibernate, R2DBC, migrations, transactions | `references/persistence-and-data.md` |
| AuthN/AuthZ, OAuth2, JWT, method security | `references/security.md` |
| Gateway, config server, service discovery | `references/spring-cloud-infra.md` |
| Circuit breakers, retries, timeouts, HTTP clients | `references/resilience-and-communication.md` |
| Kafka, events, outbox, idempotency | `references/messaging-and-events.md` |
| Metrics, tracing, logging, Actuator | `references/observability.md` |
| Unit/slice/integration tests, Testcontainers | `references/testing.md` |
| Dockerfile, image build, Kubernetes, native image | `references/containerization-and-k8s.md` |

---

## Mode 3 — Review / audit

Use when the user points at an existing service and wants it judged: "review this",
"is this production-ready", "what's wrong with my Spring Boot service", "modernize
this".

**Process:**

1. **Read before judging.** Read the build file, main class, config, and a
   representative slice of controllers/services/repositories/tests. You cannot review
   what you haven't read; generic checklists applied blind are the hallmark of a bad
   review.
2. **Work the dimensions** in `references/review-checklist.md`. It is the canonical
   checklist covering: version currency, architecture & boundaries, API design,
   persistence & transactions, security, resilience, configuration & secrets,
   observability, testing, and build/deployment.
3. **Verify, don't assume.** Before reporting a problem, confirm it against the
   actual code — an unverified finding that turns out wrong costs more trust than a
   missed one. Distinguish confirmed issues from things you suspect but couldn't
   verify.
4. **Prioritize by real impact.** Rank findings by severity (correctness/security
   first, then reliability, then maintainability, then style). A flat list of 40
   nitpicks is far less useful than the 5 things that actually matter, ordered.

**Report format** — use this structure so reviews are consistent and scannable:

```
## Summary
<2-4 sentences: overall health, the single most important thing to fix>

## Critical  (correctness / security — fix before ship)
- <finding> — <file:line> — why it matters — how to fix

## High  (reliability / data integrity)
- ...

## Medium  (maintainability / modernization)
- ...

## Low / nits
- ...

## What's already good
<call out real strengths — a review that only lists problems is demoralizing and
loses credibility; acknowledging what's right shows you actually read it>
```

Note the interaction with your existing `perf-review-be` skill: that one owns the
**database/query performance** lens in depth (N+1, indexing, pooling, slow
endpoints). This skill's review mode covers the broader service. When performance
is the focus, defer to `perf-review-be` for the DB layer rather than duplicating it.

---

## Cross-cutting principles (apply in every mode)

These are the through-lines that separate a service that merely runs from one a
team can operate at 2am. Weave them in everywhere rather than treating them as a
final checklist.

- **Observability is not optional.** Every service ships with metrics
  (Micrometer), distributed tracing (Micrometer Tracing → OpenTelemetry), and
  structured logs correlated by trace ID from day one. Adding it after an incident
  is too late. `references/observability.md`.
- **Design for failure.** Every remote call has a timeout, a retry policy where
  safe, and a circuit breaker. The default assumption is that dependencies *will*
  be slow or down. `references/resilience-and-communication.md`.
- **Configuration and secrets are externalized.** No secrets in code, images, or
  git. Config comes from the environment/config server/K8s; profiles keep
  environments separated. `references/configuration-and-profiles.md`.
- **Virtual threads by default.** On Java 21+, prefer virtual threads
  (`spring.threads.virtual.enabled=true`) for the simple, scalable
  thread-per-request model, unless there's a specific reason to go reactive.
- **Tests are part of "done".** A feature without tests isn't finished. Favor fast
  slice tests plus Testcontainers-backed integration tests over brittle mocks of
  infrastructure. `references/testing.md`.
- **Statelessness & the 12-factor mindset.** Services should be horizontally
  scalable and disposable; state lives in datastores, not in memory.
- **Least surprise.** Follow Spring idioms and the project's own conventions.
  Clever, non-idiomatic code is a liability in a service many people maintain.

---

## Anti-patterns to actively push back on

Naming these explicitly matters because they're common, they look reasonable, and
letting them slide is how services rot. When you see one, say so and explain the
cost, don't just silently work around it.

- **Distributed monolith** — services so chatty and synchronously coupled they must
  deploy together. Fix by rethinking boundaries and going async.
- **Shared database across services** — kills independent evolution and ownership.
  Each service owns its schema.
- **Anemic + leaky layering** — entities used as API DTOs, business logic in
  controllers, `@Autowired` field injection. Use constructor injection, keep the web
  layer thin, don't expose JPA entities over the wire.
- **Legacy stack presented as current** — `javax.*`, Zuul 1, Hystrix, Netflix
  Ribbon, Java 8/11. These are end-of-life; flag them.
- **Swallowed exceptions & generic 500s** — no error model, `catch (Exception e) {}`,
  stack traces leaked to clients. Use Problem Details and a global handler.
- **Security theater** — permitAll everywhere, secrets in `application.properties`,
  JWTs validated loosely. Security is a first-class concern.
- **"We'll add observability/tests/resilience later."** Later rarely comes; the
  cost of retrofitting is much higher than building it in.

---

## Reference map

All depth lives in `references/`. Load on demand:

- `architecture-and-design.md` — boundaries, modular monolith vs microservices, DDD-lite, patterns.
- `project-setup.md` — Initializr, Maven & Gradle, structure, dependencies, virtual threads.
- `configuration-and-profiles.md` — externalized config, profiles, config server, secrets.
- `rest-api-design.md` — resources, DTOs, validation, Problem Details, pagination, versioning, OpenAPI.
- `persistence-and-data.md` — JPA/Hibernate, R2DBC, transactions, migrations (Flyway/Liquibase), data ownership.
- `security.md` — Spring Security 6/7, OAuth2 resource server, JWT, method security.
- `spring-cloud-infra.md` — Gateway, Config Server, discovery, release-train alignment.
- `resilience-and-communication.md` — Resilience4j, timeouts/retries/bulkheads, HTTP interface clients, OpenFeign.
- `messaging-and-events.md` — Kafka, event-driven patterns, transactional outbox, idempotency.
- `observability.md` — Micrometer metrics, tracing→OpenTelemetry, structured logging, Actuator.
- `testing.md` — test pyramid, slice tests, Testcontainers, contract testing.
- `containerization-and-k8s.md` — layered/buildpack images, Dockerfile, GraalVM native, K8s manifests & probes.
- `review-checklist.md` — the canonical audit checklist for Mode 3.

`assets/templates/` holds ready-to-adapt `pom.xml`, `build.gradle.kts`,
`Dockerfile`, `compose.yaml`, and `application.yml` starters.
