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
microservices the way strong teams do it in 2026. Three jobs — pick the one the
request needs:

- **Design** — boundaries, API contracts, data ownership, communication style.
- **Scaffold / build** — generate a correct modern project; implement features well.
- **Review / audit** — judge an existing service against modern standards.

Most requests blend these. This file is the router; **it stays loaded, so it's kept
lean on purpose.** Depth lives in `references/` — load a reference only when the task
actually reaches that topic, and see the [reference map](#reference-map) at the bottom.

## Orient, and match effort to the task

Before acting, settle three things — getting them wrong is the top cause of correct-
but-useless advice: **(1) which mode** (ask if genuinely ambiguous); **(2) the version
generation** — check `pom.xml`/`build.gradle` and the JDK, never assume (see
[Version policy](#version-policy)); **(3) architecture context** — greenfield, one
service in an existing estate, or a modular monolith. For existing code, actually read
the build file, main class, a representative controller/service/repository, and
`application.yml` before forming an opinion.

Then calibrate how hard to lean on this skill — this matters because a capable model
already writes correct idiomatic Spring Boot (adding `@Valid`, returning a 404, wiring
a `SecurityFilterChain`) and loading references for those just spends context:

- **Narrow, well-specified change** (one endpoint, one clear bug, an obvious idiom):
  apply the fix directly; do **not** deep-read references. If the request or a failing
  test already fully specifies the answer, just do it.
- **Open-ended / multi-concern / ambiguous work** (designing, choosing sync vs async,
  reviewing unfamiliar code, "why is this slow/flaky", "is this production-ready"):
  *this* is where the skill pays off — load the relevant references and the decision
  tables/playbooks in `references/decisions-and-playbooks.md`.

## Version policy

Quoting a stale or mismatched version is worse than quoting none.

- **Default:** Spring Boot **4.x** / Spring Framework 7 / **Java 25 (LTS)**. Java 21 is
  the floor; below 21 is legacy to plan off.
- **Conservative baseline:** Spring Boot **3.5.x** is fully supported — work *with* it,
  don't reflexively push an upgrade unless asked.
- **Namespace:** current generation is **Jakarta** (`jakarta.*`), never `javax.*`.
  `javax.*` in a "modern" service is itself a finding.
- **Spring Cloud:** never pick its version independently — each Boot generation pins a
  release **train**; mismatches are a classic painful bug. Resolve the train from the
  official compatibility matrix and let the BOM manage it (`references/spring-cloud-infra.md`).
- **When unsure of an exact version, say so** and point to the build file / matrix
  rather than inventing a number.

## Mode 1 — Design

Deciding *what to build* / *how to structure it*. Work these as deep as the request
needs; details in `references/architecture-and-design.md`.

1. **Boundaries first** — around business capabilities and data ownership, not
   technical layers. If the domain isn't clearly decomposed, prefer a **modular
   monolith** (Spring Modulith) and split later; premature splitting is the most
   expensive mistake here.
2. **API contract** — resource model, error model (Problem Details/RFC 9457),
   pagination, versioning — decided before implementation (`references/rest-api-design.md`).
3. **Communication style** — sync vs async **per interaction**; default async for
   cross-service state propagation (`references/decisions-and-playbooks.md`).
4. **Data ownership & consistency** — one owner per datum; sagas/outbox, never
   distributed transactions (`references/persistence-and-data.md`).
5. **Cross-cutting concerns as platform** — auth, config, observability, resilience
   consistent across the estate (gateway / shared starter / mesh).

Deliverable: a concise writeup or ADR. For formal diagrams/C4, hand off to the
`enterprise-architecture` skill rather than reinventing it here.

## Mode 2 — Scaffold / build

Keep this mode **lean** — a capable model already writes good implementation code, so
don't front-load reference reading; reach for a reference only when a concrete build
decision is genuinely open. The leverage here is getting the baseline right and
steering the few real forks.

**New project:** (1) confirm Maven or Gradle and Java version (default Java 25);
(2) generate the base from **Spring Initializr**, then adjust — it guarantees a coherent
dependency set incl. the right Spring Cloud train; (3) wire the non-negotiable baseline:
Actuator + K8s health probes, structured logging, externalized config, a global error
handler, virtual threads. See `references/project-setup.md` and `assets/templates/`.

**Feature in an existing project:** (1) **match the surrounding code** — its layout,
naming, idioms beat personal preference; (2) implement the vertical slice with
validation, error handling, tests, and observability included, not bolted on; (3) write
tests as you go, Testcontainers for anything touching a real dependency.

Load the reference matching what you're implementing:

| Working on... | Read |
|---|---|
| An ambiguous choice, or an underspecified symptom | `references/decisions-and-playbooks.md` |
| Project layout, build files, dependencies, profiles | `references/project-setup.md`, `references/configuration-and-profiles.md` |
| Controllers, DTOs, validation, errors, versioning | `references/rest-api-design.md` |
| JPA/Hibernate, R2DBC, migrations, transactions | `references/persistence-and-data.md` |
| AuthN/AuthZ, OAuth2, JWT, method security | `references/security.md` |
| Gateway, config server, service discovery | `references/spring-cloud-infra.md` |
| Circuit breakers, retries, timeouts, HTTP clients | `references/resilience-and-communication.md` |
| Kafka, events, outbox, idempotency | `references/messaging-and-events.md` |
| Metrics, tracing, logging, Actuator | `references/observability.md` |
| Tests, Testcontainers | `references/testing.md` |
| Dockerfile, images, Kubernetes, native | `references/containerization-and-k8s.md` |

## Mode 3 — Review / audit

For "review this", "is this production-ready", "what's wrong", "modernize this".

1. **Read before judging** — build file, main class, config, a representative slice of
   controllers/services/repositories/tests. Blind checklists are the mark of a bad review.
2. **Check correctness and intent FIRST — before any standards checklist.** Trace what
   each critical method *does* vs. what it *intends* (names, comments, flow). Hunt for
   results fetched then ignored, logic that contradicts its comment, wrong
   identifier/field, dead branches, boundary/null mistakes. Run first because once
   you're auditing conventions you glide past a method that compiles, follows every
   idiom, and still does the wrong thing — the most damaging bug. Functional wrongness
   outranks every style finding.
3. **Then work the dimensions and use the report format** in `references/review-checklist.md`.
4. **Verify, don't assume** — confirm each finding against the code; separate confirmed
   from suspected.
5. **Prioritize by real impact** — severity order (correctness/security → reliability →
   maintainability → style). Five things that matter beat forty nitpicks.

Interaction with your `perf-review-be` skill: that one owns the **DB/query-performance**
lens (N+1, indexing, pooling); defer to it for the DB layer rather than duplicating.

## Principles & anti-patterns

Apply in every mode; reasoning in `references/principles-and-anti-patterns.md`.

- **Principles:** observability from day one; design for failure (timeouts + breakers);
  externalized config/secrets; virtual threads by default; tests are part of "done";
  stateless/12-factor; least surprise (follow the project's idioms).
- **Push back on:** distributed monolith; shared DB across services; leaky layering
  (entities as DTOs, logic in controllers, field injection); legacy stack shown as
  current (`javax.*`, Zuul/Hystrix/Ribbon, Java 8/11, `WebSecurityConfigurerAdapter`);
  swallowed exceptions / generic 500s; security theater; "we'll add it later".

## Reference map

Load on demand:

- `architecture-and-design.md` — boundaries, modular monolith vs microservices, DDD-lite.
- `decisions-and-playbooks.md` — **decision tables for the ambiguous forks + diagnostic playbooks; the highest-leverage file on open-ended work.**
- `project-setup.md` — Initializr, Maven & Gradle, structure, dependencies, virtual threads.
- `configuration-and-profiles.md` — externalized config, profiles, config server, secrets.
- `rest-api-design.md` — resources, DTOs, validation, Problem Details, pagination, versioning, OpenAPI.
- `persistence-and-data.md` — JPA/Hibernate, R2DBC, transactions, migrations, data ownership.
- `security.md` — Spring Security 6/7, OAuth2 resource server, JWT, method security.
- `spring-cloud-infra.md` — Gateway, Config Server, discovery, release-train alignment.
- `resilience-and-communication.md` — Resilience4j, timeouts/retries/bulkheads, HTTP clients.
- `messaging-and-events.md` — Kafka, event-driven patterns, transactional outbox, idempotency.
- `observability.md` — Micrometer metrics, tracing→OpenTelemetry, structured logging, Actuator.
- `testing.md` — test pyramid, slice tests, Testcontainers, contract testing.
- `containerization-and-k8s.md` — layered/buildpack images, Dockerfile, GraalVM native, K8s probes.
- `principles-and-anti-patterns.md` — the through-lines and anti-patterns, with reasoning.
- `review-checklist.md` — canonical audit checklist + report format for Mode 3.

`assets/templates/` holds ready-to-adapt `pom.xml`, `build.gradle.kts`, `Dockerfile`,
`compose.yaml`, and `application.yml` starters.
