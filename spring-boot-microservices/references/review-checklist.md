# Review / Audit Checklist

The canonical checklist for Mode 3. Work the dimensions in order — they're roughly
sorted by blast radius. For each finding, cite `file:line`, say why it matters, and
give the fix. Verify against the actual code before reporting, and rank by severity
(Critical → High → Medium → Low). Remember to also call out what's genuinely good.

Performance/DB depth (query plans, indexing, pool sizing) belongs to the
`perf-review-be` skill — defer there rather than duplicating it.

## Report format

Use this structure so reviews are consistent and scannable:

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

## 0. Correctness & intent (do this BEFORE the rest)

Run this pass first, deliberately, before any convention check — because once you're
in "standards checklist" mode you start pattern-matching idioms and stop reading the
code as a program, and that's exactly how a method that compiles, follows every
convention, and still does the wrong thing sails through review. Functional wrongness
outranks every finding below it.

For each method in the critical path, trace what it actually does and compare to what
it clearly intends (from names, comments, surrounding flow), looking for:

- **Ignored results** — a value fetched from a DB/remote call/computation and then
  never used, while the code proceeds as if it had acted on it. (This is the exact bug
  the earlier review missed: an inventory response was fetched and discarded, yet the
  order was confirmed anyway.)
- **Logic that contradicts its comment or name** — `isValid()` that can't return false,
  a "check stock" that never checks.
- **Wrong identifier/field** — keying a lookup by `customerId` when it should be a
  product/SKU; comparing the wrong two things.
- **Dead / unreachable branches**, swallowed conditions, and inverted booleans.
- **Boundary / off-by-one** errors and empty-collection / null edge cases.
- **Missing steps** the flow implies but never performs (validation promised in a
  comment, a state transition that never happens).

Report anything here as Critical/High regardless of how clean the surrounding style
is. A beautifully-layered service that computes the wrong answer is still broken.

## 1. Version currency & stack

- Spring Boot generation — current (4.x / 3.5.x supported) or end-of-life?
- Java version — 21+/25, or legacy 8/11/17-below-EOL?
- **`javax.*` imports** → not migrated to Jakarta. Flag.
- End-of-life components: **Zuul 1, Hystrix, Ribbon**, `WebSecurityConfigurerAdapter`,
  `RestTemplate`-only patterns. Flag with the modern replacement.
- Spring Cloud train **matches** the Boot version (via BOM), not pinned independently.
- Dependency vulnerabilities scanned (Dependabot/OWASP/Snyk)? Known CVEs present?

## 2. Architecture & boundaries

- Are service boundaries around business capabilities, or is this a **distributed
  monolith** (chatty synchronous coupling, services that always deploy together)?
- **Shared database** across services? (Critical — breaks ownership.)
- Sync vs async chosen appropriately; async for state propagation where it fits.
- Cross-service consistency handled deliberately (saga/outbox), not attempted
  distributed transactions.
- Layering clean: thin controllers, logic in services, no business logic in the web
  layer.

## 3. API design

- Resource-oriented URLs, correct HTTP verbs and status codes.
- **DTOs at the boundary**, not JPA entities exposed over the wire.
- Request validation present (`@Valid` + Bean Validation) with clean 400/422.
- **Problem Details (RFC 9457)** error model via a global `@RestControllerAdvice`; no
  stack traces / SQL leaked to clients.
- Pagination on list endpoints (no unbounded collections).
- Versioning strategy defined and consistent.

## 4. Persistence & transactions

- `@Transactional` on the service layer; read-only marked; no remote calls inside
  transactions.
- **N+1 queries** — `EAGER` associations, missing fetch joins, per-row lazy loads.
- **`ddl-auto`** is `validate`/`none` in prod (not `update`/`create`). Critical if not.
- Schema managed by **Flyway/Liquibase**, versioned in git.
- No `@Transactional` self-invocation bugs.
- HikariCP configured with sane pool size and timeouts.

## 5. Security

- **No secrets in code/config/images.** (Critical.)
- Default-deny authorization; no accidental `permitAll()` blanket.
- Stateless auth; **OAuth2 resource server + JWT** with signature, **issuer, audience,
  expiry** all validated.
- Method-level security for fine-grained/ownership checks, not just URL rules.
- CSRF setting correct for the app type; CORS scoped, not `*` on credentialed APIs.
- TLS in transit; service-to-service calls authenticated (not "trust the network").
- Actuator sensitive endpoints not publicly exposed.

## 6. Resilience & communication

- **Every remote call has a timeout.** (High if not.)
- Circuit breakers (Resilience4j) on external dependencies; fallbacks on critical
  paths.
- Retries only on idempotent ops, with backoff + jitter and a cap.
- Modern HTTP client (`@HttpExchange`/`RestClient`/`WebClient`), not raw
  `RestTemplate`.

## 7. Messaging (if event-driven)

- **Transactional outbox** (or CDC) for reliable publish — no naive dual-write.
- **Idempotent consumers** (at-least-once tolerated).
- Dead-letter handling for poison messages; retries with backoff.
- Event schema versioned and evolved compatibly.

## 8. Configuration & profiles

- Config externalized; same artifact across environments.
- Profiles used for environment deltas, not branching business logic.
- Type-safe `@ConfigurationProperties` (validated) over scattered `@Value`.

## 9. Observability

- Actuator health/metrics enabled; sensitive endpoints restricted.
- **Micrometer metrics** exported (Prometheus/OTLP); meaningful business metrics,
  controlled tag cardinality.
- **Distributed tracing** wired (Micrometer Tracing → OpenTelemetry), context
  propagated across hops.
- **Structured (JSON) logging** with trace-id correlation; no secrets/PII logged.
- K8s liveness/readiness probes distinct and correct.

## 10. Testing

- Test pyramid shape: many unit, some slice, fewer integration — not inverted.
- **Testcontainers** for integration (real DB/broker), not H2 as a stand-in.
- Endpoints tested including validation/error paths; core business rules unit-tested.
- Idempotency and DLQ behavior tested for consumers.
- Contract tests where services integrate (as the estate grows).

## 11. Build & deployment

- Executable image via buildpacks or a **layered**, **non-root**, JRE-based Dockerfile;
  no secrets baked in; images scanned.
- Graceful shutdown enabled; startup/liveness/readiness probes set; resource
  requests/limits set.
- CI runs tests + security/dependency scans; reproducible builds (pinned versions).

## 12. CI/CD & supply chain

- Pipeline is the only path to prod; gates **fail the build** (tests, scans), not warn.
- Dependency + image **vulnerability scanning** with a fail-on-high/critical policy.
- **SBOM** generated; images **signed** (cosign) with provenance; only signed images
  admitted to the cluster.
- Build once, **promote the same artifact** across environments (no per-env rebuild).
- CI secrets in a store (prefer short-lived OIDC), never plaintext in workflow/logs.

## 13. Deployment & migration safety

- Changes are **backward-compatible** (old + new versions run together during rollout).
- Destructive schema changes use **expand/contract**, never drop/rename a column in the
  same release that stops using it. (Critical — a top cause of outages/data loss.)
- Migrations forward-only in prod, non-locking/batched; schema changes **decoupled** from
  code deploys.
- Readiness flips to down on SIGTERM + graceful shutdown → no dropped requests on deploy.
- Rollback path exists (and DB changes don't block it).

## 14. Caching correctness

- Caching only where read-heavy and staleness-tolerant (not for must-be-current data).
- **Invalidation designed, not forgotten** — evict on write; cross-service via events or
  short TTL; a TTL safety net exists.
- Hot keys protected against **stampede** (locking/coalescing/jitter).
- Low-cardinality keys (not per-user/per-request); cache is not the source of truth;
  degrades to source on cache outage.

## 15. Async & scheduling

- `@Scheduled` in a multi-replica service is guarded (**ShedLock**/leader election) or
  provably safe to run on every pod. (Common bug: job fires N times.)
- Long-running work is off the request thread (202 + queue/worker), not blocking.
- Jobs are **idempotent**, retryable, observable (metrics + "hasn't run in N hours" alert).
- `@Async` exceptions handled; context propagation considered.

## 16. Compliance, privacy & audit

- PII classified and minimized; not copied/propagated to services that don't need it.
- **No PII/secrets in logs** (watch `toString()`, `show-sql`, structured fields).
- **Audit log** (who/what/when) for sensitive actions — durable, separate from app logs.
- Retention + **deletion/erasure** path exists; tenant isolation enforced at a layer that
  can't be forgotten per-query and is tested.

## Severity guide

- **Critical** — data loss/corruption risk, security exposure, shared DB, secrets in
  repo, prod `ddl-auto=update`, **destructive same-release DB migration**,
  unauthenticated sensitive endpoints, **PII/secrets written to logs**, unsigned/unscanned
  images promoted to prod.
- **High** — missing timeouts, N+1 on hot paths, no error model, missing tests on core
  logic, non-idempotent consumers, **`@Scheduled` unguarded in a multi-replica service**,
  **cache with no invalidation strategy**, no audit trail for sensitive actions.
- **Medium** — legacy-but-working components (with no upgrade plan), weak observability /
  no SLOs, config not externalized, thin test coverage, unprotected hot-key cache.
- **Low / nits** — naming, minor idiom deviations, style.
