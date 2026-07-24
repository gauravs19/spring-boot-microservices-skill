# Spring Boot Microservices Skill

> A [Claude Code / Agent Skill](https://docs.anthropic.com/en/docs/agents-and-tools/agent-skills)
> for **designing, scaffolding, and reviewing modern Java Spring Boot microservices** —
> the way strong teams build them in 2026.

![version](https://img.shields.io/badge/version-1.2.0-blue)
![license](https://img.shields.io/badge/license-MIT-green)
![stack](https://img.shields.io/badge/Spring%20Boot-4.x%20%7C%203.5.x-brightgreen)
![java](https://img.shields.io/badge/Java-25%20LTS%20(21%2B)-orange)
![build](https://img.shields.io/badge/build-Maven%20%2B%20Gradle-lightgrey)

It encodes current, production-grade practice — Spring Boot 4.x on Spring Framework 7,
Java 25 LTS, virtual threads, first-class observability, resilience, event-driven
patterns, and container-native deployment — into a single skill that Claude consults
whenever you're working on a Spring Boot backend. It ships with its **own evals** so its
value is measured, not asserted (see [Validation](#validation)).

## Contents

- [Why this skill](#why-this-skill)
- [What it does — the three modes](#what-it-does--the-three-modes)
- [What it covers](#what-it-covers)
- [Installation](#installation)
- [Usage](#usage)
- [How it's built (design principles)](#how-its-built-design-principles)
- [Repository structure](#repository-structure)
- [Reference library](#reference-library)
- [Validation](#validation)
- [Requirements](#requirements)
- [Versioning & compatibility](#versioning--compatibility)
- [Contributing](#contributing)
- [License](#license)

## Why this skill

A capable model already writes correct, idiomatic Spring Boot for narrow, well-specified
tasks. This skill isn't trying to help with those — it's built for the places a bare
model tends to be *generic or wrong*: **estate-level judgment** (where do service
boundaries go, sync vs async, is this a distributed monolith), **cross-cutting rigor**
(observability, resilience, and security wired in from day one rather than bolted on),
and **review depth** (catching functional bugs and prioritizing them, not just linting
conventions). It's also deliberately honest about versions — it refuses to invent a
version number or a Spring Cloud release train it can't verify.

## What it does — the three modes

The skill routes automatically based on what you ask; most real requests blend the modes.

| Mode | Use it for | Example prompts |
|---|---|---|
| **Design** | Service boundaries, API contracts, data ownership, sync-vs-async, modular-monolith-vs-microservices | *"Should orders and payments be one service or two?"* · *"Design the API contract for a notifications service"* |
| **Scaffold / build** | Generating a correct, modern project; implementing a feature with the right patterns | *"Scaffold a Spring Boot service with Postgres and OAuth2"* · *"Add a paginated search endpoint with validation"* |
| **Review / audit** | Judging an existing service against modern standards, with prioritized findings | *"Review this service"* · *"Is this production-ready?"* · *"Modernize this Boot 2.7 app"* |

Review mode runs a **correctness-first pass** — it traces what the code actually *does*
versus what it *intends* before checking any conventions, so functional bugs (a result
fetched then ignored, the wrong identifier used) surface ahead of style nits.

## What it covers

Full modern-stack depth, organized so only the relevant slice loads per task:

- **Architecture & design** — bounded contexts, modular monolith (Spring Modulith) vs
  microservices, data ownership, saga/outbox consistency, DDD-lite.
- **REST API design** — resource modeling, DTOs (never entities on the wire), Bean
  Validation, **Problem Details (RFC 9457)** error model, pagination, API versioning,
  OpenAPI.
- **Persistence** — JPA/Hibernate, Spring Data JDBC, R2DBC; transaction boundaries,
  N+1 avoidance, Flyway/Liquibase migrations, HikariCP, per-service data ownership.
- **Security** — Spring Security 6/7 (lambda DSL, `SecurityFilterChain`), stateless
  OAuth2 resource server + JWT (signature/issuer/audience/expiry), method security,
  service-to-service auth, secure defaults.
- **Spring Cloud** — Spring Cloud Gateway, Config Server vs K8s config, service
  discovery, and strict **release-train alignment** via the BOM.
- **Resilience & communication** — Resilience4j (timeouts, retries with backoff+jitter,
  circuit breakers, bulkheads, fallbacks); modern HTTP clients (`@HttpExchange`,
  `RestClient`).
- **Messaging / event-driven** — Kafka vs RabbitMQ, event design, the **transactional
  outbox**, idempotent consumers, dead-letter handling, schema evolution.
- **Observability** — Micrometer metrics, distributed tracing → **OpenTelemetry**,
  structured JSON logging with trace correlation, Actuator, K8s health probes.
- **Testing** — the test pyramid, slice tests (`@WebMvcTest`/`@DataJpaTest`),
  **Testcontainers** with `@ServiceConnection`, contract testing.
- **Containerization & Kubernetes** — layered/buildpack images, non-root Dockerfiles,
  GraalVM native / AOT+CDS startup, probes, graceful shutdown, resource limits.
- **Decision tables & playbooks** — the ambiguous forks (sync vs async, JPA/JDBC/R2DBC,
  split-vs-keep) and diagnostic playbooks ("slow endpoint", "intermittent 500s",
  "flaky in prod", "is this production-ready").

## Installation

### Option A — Claude Code plugin marketplace (recommended)

This repo is itself a Claude Code plugin marketplace, so you can install it without
cloning:

```
/plugin marketplace add gauravs19/spring-boot-microservices-skill
/plugin install spring-boot-microservices@gauravs19-skills
```

Updates then flow through `/plugin` like any other marketplace plugin.

### Option B — manual copy

```bash
git clone https://github.com/gauravs19/spring-boot-microservices-skill.git
cp -r spring-boot-microservices-skill/spring-boot-microservices ~/.claude/skills/
```

Either way, the skill triggers automatically on Spring Boot / Java microservice work,
or you can invoke it explicitly.

## Usage

Once installed, just work normally — the skill activates when your request touches
Spring Boot, Spring Cloud, or Java microservices. A few concrete examples:

- **Design:** *"I'm building an orders platform. Walk me through where the service
  boundaries should be and whether to start with a modular monolith."*
- **Scaffold:** *"Create a new Spring Boot service (Maven, Java 25) with a Postgres
  repository, a validated REST endpoint, and Actuator health probes."*
- **Feature:** *"Add a circuit-breaker-protected call to the inventory service with a
  sensible timeout and fallback."*
- **Review:** *"Review `order-service/` and tell me what's not production-ready, worst
  first."*

For narrow one-line changes the skill intentionally stays out of the way and just
applies the idiom; it leans in on open-ended design/review work.

## How it's built (design principles)

- **Modern by default, honest about versions.** Targets the current GA generation and
  refuses to guess versions or Spring Cloud release trains it can't verify — it points
  to the compatibility matrix instead.
- **Progressive disclosure.** The `SKILL.md` router is deliberately lean (~180 lines) so
  the always-loaded cost is small; all depth lives in `references/` and loads only when
  a task reaches that topic.
- **Effort-matched.** It calibrates: apply the idiom directly on narrow tasks, bring the
  full references and playbooks on ambiguous ones.
- **Opinionated where it counts.** It actively pushes back on real anti-patterns —
  distributed monolith, shared databases, `javax.*`/Zuul/Hystrix legacy, secrets in
  code, "we'll add observability later".
- **Both build tools.** Maven and Gradle (Kotlin DSL) templates included.

## Repository structure

```
spring-boot-microservices/          # the drop-in skill
├── SKILL.md                         # lean router: modes, version policy, effort triage
├── references/                      # depth, loaded on demand (15 guides)
│   ├── architecture-and-design.md
│   ├── decisions-and-playbooks.md   # decision tables + diagnostic playbooks
│   ├── project-setup.md
│   ├── configuration-and-profiles.md
│   ├── rest-api-design.md
│   ├── persistence-and-data.md
│   ├── security.md
│   ├── spring-cloud-infra.md
│   ├── resilience-and-communication.md
│   ├── messaging-and-events.md
│   ├── observability.md
│   ├── testing.md
│   ├── containerization-and-k8s.md
│   ├── principles-and-anti-patterns.md
│   └── review-checklist.md          # audit checklist + report format
└── assets/templates/                # ready-to-adapt starters
    ├── pom.xml
    ├── build.gradle.kts
    ├── Dockerfile
    ├── compose.yaml
    └── application.yml

evals/                               # reproducible evaluations (see evals/README.md)
├── bugfix/                          # objective, mvn-test-graded bug-fix suite (regression guard)
│   ├── tasks/                        # 3 Spring Boot apps, each with a real bug + failing test
│   └── grade.sh                      # objective grader (runs mvn test, checks tests untouched)
├── review/                          # labelled flawed service + answer key (where lift shows)
└── RESULTS.md                       # measured results across versions
```

## Reference library

| Reference | What's in it |
|---|---|
| `architecture-and-design.md` | Boundaries, modular monolith vs microservices, data ownership, saga/outbox |
| `decisions-and-playbooks.md` | Decision tables for ambiguous forks + diagnostic playbooks — **highest leverage on open-ended work** |
| `project-setup.md` | Spring Initializr, Maven & Gradle, structure, dependencies, virtual threads |
| `configuration-and-profiles.md` | Externalized config, profiles, config server vs K8s, secrets |
| `rest-api-design.md` | Resources, DTOs, validation, Problem Details, pagination, versioning, OpenAPI |
| `persistence-and-data.md` | JPA/JDBC/R2DBC, transactions, N+1, migrations, pooling, data ownership |
| `security.md` | Spring Security 6/7, OAuth2 resource server, JWT, method security |
| `spring-cloud-infra.md` | Gateway, Config Server, discovery, release-train alignment |
| `resilience-and-communication.md` | Resilience4j, timeouts/retries/breakers/bulkheads, HTTP clients |
| `messaging-and-events.md` | Kafka/RabbitMQ, event design, transactional outbox, idempotency, DLQ |
| `observability.md` | Micrometer, tracing → OpenTelemetry, structured logging, Actuator, probes |
| `testing.md` | Test pyramid, slice tests, Testcontainers, contract testing |
| `containerization-and-k8s.md` | Layered/buildpack images, GraalVM native, K8s probes, graceful shutdown |
| `principles-and-anti-patterns.md` | The cross-cutting through-lines and anti-patterns, with reasoning |
| `review-checklist.md` | Canonical audit checklist + the review report format |

## Validation

The skill ships its own evals (`evals/`) with a results log (`evals/RESULTS.md`) — the
point being honesty: a skill should show what it does and doesn't improve, measured.

**Bug-fix suite (regression guard).** Three Spring Boot apps, each with a real bug and a
failing JUnit test; graded objectively by `mvn test`. Across versions the skill resolves
**3/3 with no test tampering** — confirming it doesn't regress or mislead on concrete
code. No pass-rate *lift* is expected here (a strong model already fixes well-specified
bugs), and none is claimed.

**Token overhead — measured and improved.**

| Run | Resolved | Avg tokens/task | Overhead vs baseline |
|---|---|---|---|
| Baseline (no skill) | 3/3 | ~40,200 | — |
| v1.1 skill-on | 3/3 | ~48,500 | +21% |
| **v1.2 skill-on (slimmed body)** | 3/3 | **~45,500** | **+13%** |

v1.2 cut the always-loaded `SKILL.md` body by ~48%, which removed roughly a third of the
skill's token overhead with correctness unchanged.

**Review suite (where the lift is).** Reviewing a labelled flawed service (17 planted
defects), the skill catches the standards issues *and*, thanks to the correctness-first
pass, the functional logic bug — ranking it the #1 Critical finding — that a
convention-only review misses.

## Requirements

- **To use the skill:** Claude Code (or a compatible Agent-Skills host).
- **To run the bug-fix evals:** JDK 21+ and Maven (the fixtures build Spring Boot 3.3.5
  apps). See `evals/README.md`.

## Versioning & compatibility

The skill targets the **current GA generation** (Spring Boot 4.x / Spring Framework 7 /
Java 25 LTS) and treats **Spring Boot 3.5.x** as a fully-supported conservative baseline.
It will not invent a Spring Cloud release-train version — it resolves that from the
official compatibility matrix for your exact Boot version and lets the BOM manage it.
See [`CHANGELOG.md`](CHANGELOG.md) for release history.

## Contributing

Spring moves fast. If a version, API, or best practice here has drifted, issues and PRs
are welcome — especially updates to the version policy and release-train alignment. If
you change skill content, please re-run the evals in `evals/` and update
`evals/RESULTS.md` so claims stay measured.

## License

MIT — see [LICENSE](LICENSE). Copyright © 2026 Gaurav Sharma.
