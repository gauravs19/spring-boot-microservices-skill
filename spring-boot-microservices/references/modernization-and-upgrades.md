# Modernization & Upgrades

An actionable path off legacy, because flagging "Spring Boot 2.7 / `javax.*` / Hystrix"
is only half the job — teams need the *how*. Covers the JVM and Boot upgrade ladder, the
`javax`→`jakarta` migration, the Netflix-OSS→modern mapping, and monolith→services.

## Table of contents
- [The upgrade ladder](#the-upgrade-ladder)
- [javax → jakarta](#javax--jakarta)
- [Netflix OSS → modern equivalents](#netflix-oss--modern-equivalents)
- [Tooling: OpenRewrite & migrators](#tooling-openrewrite--migrators)
- [Order of operations & risk management](#order-of-operations--risk-management)
- [Monolith → microservices (strangler fig)](#monolith--microservices-strangler-fig)

## The upgrade ladder

Upgrade **one major step at a time**, with a green test suite between each — never jump
2.7 → 4.x in one leap. The typical ladder:

1. **Get current on your major first** — e.g. latest 2.7.x — so you start from a clean,
   patched baseline.
2. **Java 8/11 → 17** — required for Spring Boot 3. Fix removed APIs, illegal reflective
   access.
3. **Spring Boot 2.7 → 3.x** — the big one: Jakarta namespace, Spring Security 6,
   config-property changes, Hibernate 6. Do this as its own focused effort.
4. **Java 17 → 21 (→ 25)** — pick up virtual threads and later LTS features.
5. **Spring Boot 3.x → 4.x** (Spring Framework 7) — once 3.x is stable.

Between each rung: run the full test suite (this is where Testcontainers integration
tests pay for themselves), and read the official *release notes and migration guide* for
that step — they enumerate breaking changes.

## javax → jakarta

Spring Boot 3+ moved from Java EE (`javax.*`) to Jakarta EE (`jakarta.*`). Every
`javax.persistence`, `javax.validation`, `javax.servlet`, etc. import must change to
`jakarta.*`, plus any third-party libs must have Jakarta-compatible versions. Don't do
this by hand across a large codebase — use OpenRewrite (below), which rewrites imports
and bumps compatible dependencies mechanically. Watch for libraries with no
Jakarta-ready release; those are the real blockers and must be found early.

## Netflix OSS → modern equivalents

Map the retired components to their supported replacements (also flagged in
`review-checklist.md` and `spring-cloud-infra.md`):

| Legacy (EOL) | Modern replacement |
|---|---|
| Zuul 1 | Spring Cloud Gateway |
| Hystrix | Resilience4j (Spring Cloud CircuitBreaker) |
| Ribbon | Spring Cloud LoadBalancer |
| `WebSecurityConfigurerAdapter` | `SecurityFilterChain` bean |
| `RestTemplate`-only | `RestClient` / `@HttpExchange` |
| Feign (as-is) | keep, or move to `@HttpExchange` interface clients |

These are behavior-preserving swaps, but each has config differences — migrate and test
one at a time, not all at once.

## Tooling: OpenRewrite & migrators

- **OpenRewrite** — automated, recipe-driven refactoring. There are maintained recipes for
  `UpgradeSpringBoot_3_x`, the Jakarta migration, JUnit 4→5, and more. Run the recipe, review
  the diff, run tests. It does the mechanical 80% so you focus on the genuinely hard 20%.
- **Spring Boot Properties Migrator** — add `spring-boot-properties-migrator` temporarily;
  at startup it logs renamed/removed properties so you catch config drift.
- **`jdeps`/`jdeprscan`** — find removed/deprecated JDK APIs before the JVM bump.

## Order of operations & risk management

Do it on a branch, behind good tests, incrementally: one rung of the ladder per PR,
green build required, deploy and soak before the next rung. Keep each PR reviewable.
The two things that derail upgrades are (a) a transitive dependency with no
Jakarta/Boot-3 release — find these first with a dependency scan — and (b) trying to
combine an upgrade with feature work, which makes failures impossible to bisect. Upgrade,
then build.

## Monolith → microservices (strangler fig)

Don't rewrite; **strangle**. Stand the new service alongside the monolith and route a
single capability's traffic to it (via the gateway), leaving the rest in place; repeat
capability by capability until the monolith is hollowed out. Start with a capability that
has clean data ownership and few dependencies. This keeps every step shippable and
reversible, versus a big-bang rewrite that carries all the risk at the end. If the domain
boundaries aren't proven yet, extract to a **modular monolith** first (see
`architecture-and-design.md`) — the seams are cheaper to move before they're network calls.
