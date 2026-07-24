# Decisions & Playbooks

This file is where the skill earns its keep: the **ambiguous forks** where a strong
model tends to give a generic or default-biased answer, and the **underspecified
symptoms** where it helps to have a systematic diagnostic path rather than a guess.
Reach for this on open-ended work; skip it for narrow, well-specified changes.

## Table of contents
- [How to use this file](#how-to-use-this-file)
- [Decision: monolith vs microservices vs modular monolith](#decision-monolith-vs-microservices-vs-modular-monolith)
- [Decision: synchronous vs asynchronous per call](#decision-synchronous-vs-asynchronous-per-call)
- [Decision: JPA vs JDBC vs R2DBC](#decision-jpa-vs-jdbc-vs-r2dbc)
- [Decision: MVC + virtual threads vs WebFlux](#decision-mvc--virtual-threads-vs-webflux)
- [Decision: split a service vs keep together](#decision-split-a-service-vs-keep-together)
- [Decision: config server vs Kubernetes config](#decision-config-server-vs-kubernetes-config)
- [Playbook: "this endpoint is slow"](#playbook-this-endpoint-is-slow)
- [Playbook: "intermittent 500s / timeouts under load"](#playbook-intermittent-500s--timeouts-under-load)
- [Playbook: "flaky behaviour / works locally, fails in prod"](#playbook-flaky-behaviour--works-locally-fails-in-prod)
- [Playbook: "is this service production-ready?"](#playbook-is-this-service-production-ready)

## How to use this file

Each decision gives you the *criteria that actually decide it*, a default, and the
trap to avoid — not a both-sides essay. State your recommendation and the one or two
facts it hinges on, then move. If the deciding facts aren't known, ask the one
question that resolves it rather than hedging.

## Decision: monolith vs microservices vs modular monolith

| Situation | Choose | Why |
|---|---|---|
| Small team, domain not yet well understood | **Modular monolith** (Spring Modulith) | Get boundary discipline without distributed-systems cost; split later when seams are proven |
| Multiple teams needing independent deploy cadence | Microservices | Team autonomy is the real driver, more than tech scaling |
| One clear scaling hotspot in an otherwise simple app | Monolith + scale that part (or extract just it) | Don't distribute the whole app for one bottleneck |
| "We want microservices" but can't name the boundaries | **Modular monolith** first | Premature splitting is the most expensive mistake in this space |

Default when unsure: **modular monolith**. The trap: splitting on day one and building
a distributed monolith that has every microservices cost and none of the benefits.

## Decision: synchronous vs asynchronous per call

Decide per interaction, not once for the whole system.

| The caller... | Use | Notes |
|---|---|---|
| needs the answer now to continue (a query, a user-facing command) | **Synchronous** (REST/`@HttpExchange`, gRPC) | Add timeout + circuit breaker; you've accepted temporal coupling |
| is propagating a state change others react to ("order placed") | **Asynchronous** event | Publisher doesn't wait or know consumers; needs idempotent consumers + outbox |
| triggers slow/expensive work whose result isn't needed inline | Async (queue/event) | Return 202 and process off the request thread |

Default for cross-service state propagation: **async events**. The trap: synchronous
call chains three-deep, so one slow dependency stalls the whole request path.

## Decision: JPA vs JDBC vs R2DBC

| Want | Choose | Trap to avoid |
|---|---|---|
| Productivity, rich mapping, standard case | **Spring Data JPA** | Must understand fetching/persistence-context or it emits surprise queries (N+1) |
| Predictability, explicit SQL, no lazy-loading magic | Spring Data JDBC | Don't reach for it just to avoid learning JPA |
| Genuinely reactive, non-blocking DB under high I/O concurrency | R2DBC | Only in a fully-reactive (WebFlux) service — huge complexity otherwise |

Default: **JPA**, unless the team explicitly values predictability over ORM
convenience (then JDBC). The trap: adopting R2DBC "for performance" in an otherwise
blocking app — virtual threads give you scalable blocking access without it.

## Decision: MVC + virtual threads vs WebFlux

| Situation | Choose |
|---|---|
| Typical CRUD/HTTP service, Java 21+ | **MVC + `spring.threads.virtual.enabled=true`** — simple thread-per-request model that now scales |
| Already-reactive ecosystem, streaming, backpressure, or massive fan-out I/O | WebFlux |
| Team new to reactive, no concrete reactive need | MVC + virtual threads |

Default: **MVC + virtual threads**. Virtual threads removed most of the historical
reason to pay WebFlux's learning-curve and debuggability cost. The trap: choosing
WebFlux for "performance" without a workload that needs it.

## Decision: split a service vs keep together

Split when **all** of these hold; otherwise keep together:

- The two parts own genuinely different data and rarely need each other's data
  synchronously.
- They have different scaling or deployment-cadence needs.
- A team boundary or clear bounded context separates them.

Signals you should **not** split (or should merge back): they always change in the
same PR; one can't function without synchronously calling the other; they'd share a
database. The trap: splitting by technical layer, or because "microservices are
best practice."

## Decision: config server vs Kubernetes config

| Situation | Choose |
|---|---|
| Running on Kubernetes, straightforward needs | **ConfigMaps + Secrets** — the platform already gives you a config plane |
| Many services/environments, want git-backed audited config, off-K8s | Spring Cloud Config Server |
| Need dynamic refresh without redeploy across the estate | Config Server + Bus, or a config-watch mechanism |

Default on K8s: **native ConfigMaps/Secrets**. The trap: standing up and securing a
config server you don't need because a tutorial used one.

---

## Playbook: "this endpoint is slow"

A bare model tends to guess. Work it in order and stop when you find the cause:

1. **Confirm where the time goes** — is it the DB, a downstream call, serialization,
   or CPU? Look at a trace (Micrometer Tracing) or add timing around the suspects.
   Don't optimize before you've localized.
2. **DB first (most common):** check for **N+1** (turn on SQL logging and count
   queries), `EAGER` associations, missing indexes, unbounded result sets / no
   pagination, and fetching more columns/rows than needed. Deep query/index tuning →
   hand to the `perf-review-be` skill.
3. **Downstream calls:** is a remote call on the hot path synchronous and untimed? Is
   it inside a transaction (holding a DB connection across the network)? Cache or
   parallelize where safe.
4. **Serialization / payload:** giant responses, serializing lazy JPA graphs, no
   pagination.
5. **Connection pool saturation** — requests queuing on a too-small (or, paradoxically,
   oversized) Hikari pool. Check pool metrics.
6. **Startup vs steady-state** — if it's only slow after deploy/scale, it's warmup
   (JIT/first-hit), not the endpoint; consider AOT/CDS.

## Playbook: "intermittent 500s / timeouts under load"

1. **Resource exhaustion** is the usual culprit — DB connection pool or thread pool
   drained. Check Hikari active/pending metrics and whether remote calls (untimed) are
   holding threads/connections.
2. **Missing timeouts** — a slow dependency with no timeout turns into piled-up
   threads → cascading failure. Every remote call needs a timeout + circuit breaker.
3. **Non-idempotent retries** amplifying load (retry storms) — check backoff+jitter and
   idempotency.
4. **GC / memory** — container memory limit too tight → OOM kills, or heap pressure
   causing long pauses. Check limits vs actual usage.
5. **A single poison input / edge case** hitting only some requests — look at what the
   failing requests share (a trace id, a tenant, a payload shape).

## Playbook: "flaky behaviour / works locally, fails in prod"

1. **Config/profile drift** — a value set locally but missing/different in prod; a
   profile not active. Same image, different injected config is the whole point — verify
   what's actually injected.
2. **Externalized dependency differences** — H2 locally vs real Postgres in prod
   (behaviour differs; this is why Testcontainers matters), or a downstream that's
   mocked locally.
3. **Time/locale/timezone**, and reliance on ordering that isn't guaranteed (Kafka
   partitions, `Set`/`Map` iteration, DB row order without `ORDER BY`).
4. **Concurrency** — races that only surface under prod concurrency; missing optimistic
   locking (`@Version`), shared mutable state on a bean.
5. **Startup ordering / readiness** — traffic hitting the pod before dependencies are
   ready; liveness vs readiness misconfigured.

## Playbook: "is this service production-ready?"

Route to Mode 3 (`review-checklist.md`) for the full audit, but the fast triage — the
five things whose absence should block ship — is:

1. **Secrets** externalized (nothing sensitive in code/config/image).
2. **AuthN/AuthZ** present and default-deny; sensitive Actuator endpoints not public.
3. **Every remote call** has a timeout (and ideally a breaker).
4. **Observability** wired: metrics, tracing, structured logs with trace correlation,
   K8s health probes.
5. **Schema** managed by migrations with `ddl-auto=validate|none` in prod.

If any of these is missing, it isn't production-ready yet — say so plainly.
