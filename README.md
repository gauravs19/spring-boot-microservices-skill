# Spring Boot Microservices Skill

A [Claude Code / Agent Skill](https://docs.anthropic.com/en/docs/agents-and-tools/agent-skills)
for designing, scaffolding, and reviewing **modern Java Spring Boot microservices** —
the way strong teams build them in 2026.

It encodes current, production-grade practice (Spring Boot 4.x on Spring Framework 7,
Java 25 LTS, virtual threads, first-class observability, resilience, event-driven
patterns, and container-native deployment) into a single skill that Claude consults
whenever you're working on a Spring Boot backend.

## What it does

The skill operates in three modes and routes automatically based on what you ask:

| Mode | Use it for |
|---|---|
| **Design** | Service boundaries, API contracts, data ownership, sync-vs-async, modular-monolith-vs-microservices decisions |
| **Scaffold / build** | Generating a correct, modern project and implementing features with the right patterns |
| **Review / audit** | Judging an existing service against modern standards, with prioritized, actionable findings |

It covers the full modern stack: REST API design (Problem Details / RFC 9457,
validation, versioning), persistence (JPA/JDBC/R2DBC, transactions, migrations,
avoiding N+1), Spring Security 6/7 (OAuth2 resource server, JWT), Spring Cloud (Gateway,
Config, discovery — with release-train alignment), resilience (Resilience4j: timeouts,
retries, circuit breakers, bulkheads), event-driven messaging (Kafka, transactional
outbox, idempotency), observability (Micrometer + OpenTelemetry, structured logging),
testing (Testcontainers, the test pyramid), and containerization/Kubernetes (layered
images, GraalVM native, health probes).

## Design principles

- **Modern by default, honest about versions.** Targets the current GA generation and
  explicitly refuses to guess version numbers it can't verify — it points you to the
  compatibility matrix instead.
- **Progressive disclosure.** The `SKILL.md` router stays lean; deep guidance lives in
  `references/` and loads only when a task reaches that topic.
- **Opinionated where it counts.** It pushes back on real anti-patterns (distributed
  monolith, shared databases, `javax.*`/Zuul/Hystrix legacy, secrets in code,
  "we'll add observability later").
- **Both build tools.** Maven and Gradle (Kotlin DSL) templates included.

## Installation

Clone and drop the skill folder into your Claude Code skills directory:

```bash
git clone https://github.com/gauravs19/spring-boot-microservices-skill.git
cp -r spring-boot-microservices-skill/spring-boot-microservices ~/.claude/skills/
```

The skill then triggers automatically on Spring Boot / Java microservice work, or you
can invoke it explicitly.

## Structure

```
spring-boot-microservices/
├── SKILL.md                       # router: modes, version policy, principles, anti-patterns
├── references/                    # depth, loaded on demand
│   ├── architecture-and-design.md
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
│   └── review-checklist.md
└── assets/templates/              # ready-to-adapt starters
    ├── pom.xml
    ├── build.gradle.kts
    ├── Dockerfile
    ├── compose.yaml
    └── application.yml
```

## License

MIT — see [LICENSE](LICENSE).

## Contributing

Spring moves fast. If a version, API, or best practice here has drifted, issues and
PRs are welcome — especially updates to the version policy and the release-train
alignment guidance.
