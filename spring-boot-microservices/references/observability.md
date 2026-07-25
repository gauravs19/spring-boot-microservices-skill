# Observability

The three pillars — metrics, traces, logs — wired in from day one and correlated by
trace id. In a distributed system you cannot debug what you cannot see, and a single
request crosses many services, so observability isn't a nice-to-have; it's how you
operate at all.

## Table of contents
- [The modern stack](#the-modern-stack)
- [Actuator](#actuator)
- [Metrics with Micrometer](#metrics-with-micrometer)
- [Distributed tracing](#distributed-tracing)
- [Structured logging & correlation](#structured-logging--correlation)
- [Health probes for Kubernetes](#health-probes-for-kubernetes)
- [What to actually measure](#what-to-actually-measure)

## The modern stack

Spring Boot's observability is built on **Micrometer** (metrics facade) and
**Micrometer Tracing** (tracing facade), which bridge to real backends via
**OpenTelemetry** — the vendor-neutral standard you should target so you're not locked
to one vendor. A common concrete stack:

- Metrics → Micrometer → Prometheus → Grafana.
- Traces → Micrometer Tracing → OpenTelemetry → an OTel-compatible backend (Tempo,
  Jaeger, or a vendor like Honeycomb/Datadog).
- Logs → structured JSON → a log aggregator (Loki, ELK/OpenSearch), correlated to
  traces by trace id.

Dependencies: `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, and
`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` for tracing.

## Actuator

`spring-boot-starter-actuator` exposes operational endpoints (`health`, `info`,
`metrics`, `prometheus`, `env`, `loggers`, etc.). Configure exposure deliberately — some
endpoints are sensitive:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      probes:
        enabled: true
```

Never expose `env`, `heapdump`, `threaddump`, or `loggers` (write access) to the public
internet — over-exposed Actuator is a real security finding (see `security.md`). In
production, ideally serve management endpoints on a separate port restricted to the
internal network.

## Metrics with Micrometer

Micrometer auto-instruments a great deal out of the box: HTTP server request timings,
JVM memory/GC/threads, datasource/HikariCP pool stats, and (when present) Resilience4j
circuit-breaker state and Kafka client metrics. Add domain metrics for what matters to
*your* service:

```java
meterRegistry.counter("orders.placed", "channel", channel).increment();
Timer.builder("orders.fulfillment").register(meterRegistry).record(duration);
```

Prefer a few meaningful business metrics over dozens of noise metrics. Watch
cardinality — tags with unbounded values (user id, order id) explode your metrics
store; keep tag values low-cardinality.

## Distributed tracing

Tracing stitches one logical request together as it hops across services, so you can
see where latency and errors actually originate. Micrometer Tracing propagates a
**trace id** and per-hop **span ids** across service boundaries automatically (via
`@HttpExchange`/`RestClient`/`WebClient`, Kafka, etc.) using the W3C Trace Context
standard, and exports spans over OTLP. Set a sampling rate appropriate to volume
(sampling everything is expensive at scale; sampling too little hides rare problems) —
often tail-based sampling at the collector is the sweet spot. The single most valuable
debugging artifact in a microservices incident is a trace showing the full call path.

## Structured logging & correlation

Log as **JSON**, not free text, so logs are queryable in aggregation. Spring Boot has
built-in structured logging (ECS / Logstash / GELF formats) — enable it rather than
hand-rolling:

```yaml
logging:
  structured:
    format:
      console: ecs
```

Crucially, the **trace id and span id are added to the logging context (MDC)
automatically** when tracing is on, so logs and traces cross-link — from a slow trace
you can jump to the exact logs, and vice versa. Log at appropriate levels, never log
secrets or PII, and include correlation context on errors so a support ticket maps to
real evidence.

## Health probes for Kubernetes

Actuator provides Kubernetes-aware health groups so the platform can manage the pod
lifecycle correctly:

- **Liveness** (`/actuator/health/liveness`) — is the app broken beyond recovery? A
  failing liveness probe restarts the pod. Keep it cheap and don't fail it for
  downstream outages (you don't want a dependency blip to restart-loop every pod).
- **Readiness** (`/actuator/health/readiness`) — can the app serve traffic right now? A
  failing readiness probe removes the pod from the load balancer without killing it —
  correct for "starting up" or "dependency temporarily unavailable".

Getting liveness vs readiness right is a common mistake; conflating them causes
restart storms. See `containerization-and-k8s.md` for the probe wiring.

## What to actually measure

Anchor dashboards and alerts on signals that reflect user pain:

- **RED** for request-driven services: **R**ate, **E**rrors, **D**uration.
- **USE** for resources: **U**tilization, **S**aturation, **E**rrors.
- Add the golden business metrics for the service (orders/sec, payment success rate).

Alert on symptoms users feel (error rate up, latency SLO breached), not on every
low-level metric — noisy alerting trains people to ignore alerts, which is worse than
none.

## SLOs, error budgets & alerting

Metrics are the raw material; an **SLO** turns them into a target you can be held to and
alert on meaningfully.

- **SLI** — a Service Level *Indicator*: a measured ratio of good events (e.g. % of
  requests under 300ms, % non-5xx). **SLO** — the *Objective*: the target for that SLI
  over a window (e.g. 99.9% of requests succeed over 30 days). Define SLOs from what users
  actually need, not from what's easy to measure.
- **Error budget** — the allowed failure (a 99.9% SLO permits 0.1% failures). It reframes
  reliability as a budget you spend: within budget, ship features; budget exhausted, stop
  and stabilize. It turns "how reliable is enough?" into a number both product and
  engineering agree on.
- **Alert on burn rate, not raw metrics.** Page when the error budget is burning fast
  enough to matter (multi-window burn-rate alerts) rather than on every blip. This is how
  you get alerts that mean "a human must act now" instead of noise. Everything else is a
  dashboard or a ticket, not a page.

Keep the paging surface small and symptom-based; route low-urgency signals to dashboards.
An on-call rotation that trusts its pager is worth more than perfect metric coverage
nobody looks at.
