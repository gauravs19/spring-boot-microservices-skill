# Spring Cloud Infrastructure

The distributed-systems supporting cast: API gateway, centralized configuration, and
service discovery. First, the rule that prevents the most painful class of bug in this
whole area.

## Table of contents
- [Release-train alignment (read first)](#release-train-alignment-read-first)
- [Modern vs legacy component map](#modern-vs-legacy-component-map)
- [API Gateway](#api-gateway)
- [Configuration server](#configuration-server)
- [Service discovery](#service-discovery)
- [Do you need all of this?](#do-you-need-all-of-this)

## Release-train alignment (read first)

Spring Cloud is released as a **release train** — a curated set of modules with one
version name — and each train targets a specific Spring Boot generation. **Mixing a
Spring Cloud version with a mismatched Spring Boot version is a classic, hard-to-debug
failure** (missing beans, `NoSuchMethodError`, startup failures). Rules:

- Resolve the correct train from the official Spring Cloud compatibility matrix for
  your exact Boot version. Do not guess a train name/number — if you can't verify it,
  say so and point the user to the matrix or to Spring Initializr (which picks it for
  you).
- Import the Spring Cloud **BOM** and let it manage every `spring-cloud-*` version.
  Never pin individual Spring Cloud artifact versions by hand.
- When you upgrade Spring Boot, you upgrade the Spring Cloud train in lockstep.

## Modern vs legacy component map

Netflix OSS defined the first generation and much of it is now end-of-life. Use the
current equivalents, and flag the legacy ones in reviews:

| Concern | Modern (use) | Legacy (flag / migrate off) |
|---|---|---|
| API gateway | Spring Cloud Gateway | Zuul 1 (EOL), Netflix Zuul |
| Circuit breaker | Resilience4j (via Spring Cloud CircuitBreaker) | Hystrix (EOL) |
| Client load balancing | Spring Cloud LoadBalancer | Netflix Ribbon (EOL) |
| Service discovery | Eureka (still maintained), Consul, or K8s-native | — |
| Declarative HTTP client | Spring `@HttpExchange` interface clients, OpenFeign | RestTemplate-only patterns |
| Config | Spring Cloud Config Server or K8s ConfigMaps | — |

## API Gateway

**Spring Cloud Gateway** is the current gateway. It provides a single entry point for
routing, cross-cutting filters (auth, rate limiting, header manipulation), and request
aggregation, keeping those concerns out of every individual service. Note it now ships
in two flavors — a reactive **Server WebFlux** variant (the original, Netty-based) and
a **Server MVC** variant (servlet-based) — pick the one matching your stack; the MVC
variant is convenient when the rest of your services are servlet/virtual-thread based.

Typical responsibilities to put at the gateway:
- Route external paths to internal services (by path, host, header).
- Coarse authentication/token relay at the edge (services still validate; see
  `security.md`).
- Rate limiting and request size limits.
- Cross-cutting headers, CORS, and request/response logging with trace propagation.

Keep business logic out of the gateway — it's an edge router, not a service.

## Configuration server

**Spring Cloud Config Server** centralizes configuration, typically backed by a git
repo so config changes are versioned and auditable. Services pull config at startup via
`spring.config.import=configserver:...`, and you can refresh without redeploying using
Actuator `refresh`/Spring Cloud Bus. It earns its place across many services and
environments, especially outside Kubernetes. Inside Kubernetes, weigh it against plain
ConfigMaps/Secrets, which many teams find sufficient — see
`configuration-and-profiles.md`.

## Service discovery

Services need to find each other without hardcoded hostnames. Options:

- **Eureka** (Spring Cloud Netflix) — still maintained and widely used; a service
  registry where instances register and clients look up. Pair with Spring Cloud
  LoadBalancer for client-side load balancing.
- **Consul** — discovery plus KV config and health checking.
- **Kubernetes-native** — in K8s you often skip a discovery server entirely: Services
  and DNS provide discovery, and the platform load-balances. This is the simplest
  option when you're already on K8s, and increasingly the default.

Choose based on platform: on Kubernetes, prefer native discovery; off it, Eureka or
Consul.

## Rate limiting & quotas

Protect services from overload and abuse, and enforce fair use. Where it lives matters:

- **At the gateway (edge)** — the natural place for coarse, cross-cutting limits: per-API
  key / per-client request rates, per-IP throttling, request-size caps. Spring Cloud
  Gateway ships a `RequestRateLimiter` filter (commonly Redis-backed, token-bucket) so
  the limit is shared across gateway replicas.
- **In the service** — finer, business-aware quotas (per-tenant monthly limits, per-user
  action caps) that the gateway can't see. Resilience4j provides a `RateLimiter` for
  in-process limiting (`resilience-and-communication.md`).

Design the response deliberately: return **429 Too Many Requests** with a `Retry-After`
header so clients back off correctly, and prefer a **shared/distributed** limiter (Redis)
over per-instance counters, or your real limit is N× what you intended across N replicas.
Rate limiting is also a genuine security control (brute-force, scraping, cost-DoS), not
just capacity management.

## Service mesh

A service mesh (Istio, Linkerd) pushes cross-cutting network concerns into a sidecar/proxy
layer *outside* your application code: **mTLS** between services (identity + encryption on
every internal hop), traffic policy (retries, timeouts, circuit breaking, canary/traffic
splitting at the platform level), and uniform telemetry. The appeal is consistency without
per-service libraries and without touching code; the cost is real operational complexity
and per-hop latency. It overlaps with app-level resilience (Resilience4j) and requires a
clear split of responsibility — decide what the mesh owns (transport security, coarse
traffic policy) vs. what stays in the app (business-aware fallbacks, domain retries).
Adopt a mesh when you have enough services that consistent mTLS and traffic policy across
them is worth the operational weight — not for a handful of services, where it's
over-engineering.

## Do you need all of this?

Not always, and adding infrastructure you don't need is its own anti-pattern — each of
these is a component to run, secure, monitor, and upgrade. A small estate on Kubernetes
may need only a gateway (or even just an ingress) and lean on the platform for config
and discovery. Recommend the minimum that solves the actual problem, and add components
as the estate grows and the pain is real.
