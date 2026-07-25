# Caching

Caching is nearly universal in microservices — for latency, to shield slow dependencies,
and for the local read-models the architecture doc recommends. It's also where subtle
correctness bugs live, because the hard part isn't storing data, it's knowing when it's
stale. Deep DB query tuning belongs to `perf-review-be`; this file is the application
caching design.

## Table of contents
- [When to cache — and when not](#when-to-cache--and-when-not)
- [The Spring Cache abstraction](#the-spring-cache-abstraction)
- [Local vs distributed](#local-vs-distributed)
- [Caching patterns](#caching-patterns)
- [Invalidation — the hard part](#invalidation--the-hard-part)
- [Stampede / thundering herd](#stampede--thundering-herd)
- [Pitfalls to flag](#pitfalls-to-flag)

## When to cache — and when not

Cache data that is **read far more than it changes** and where slight staleness is
acceptable (reference data, expensive computed results, responses from a slow/rate-limited
dependency). Do **not** cache when correctness demands the latest value (account
balances, inventory you're about to sell), when writes dominate, or when the underlying
call is already fast — a cache you add "for performance" that you then have to invalidate
perfectly can cost more (in bugs) than it saves. Reach for caching to solve a *measured*
latency or load problem, not preemptively.

## The Spring Cache abstraction

Spring's declarative cache keeps caching out of business logic:

```java
@Cacheable(cacheNames = "product", key = "#sku")
Product byId(String sku) { ... }              // populated on miss, served on hit

@CacheEvict(cacheNames = "product", key = "#p.sku")
void update(Product p) { ... }                // evict on write

@CachePut(cacheNames = "product", key = "#p.sku")
Product save(Product p) { ... }               // update cache with the write's result
```

Enable with `@EnableCaching` and back it with a real `CacheManager` (below). Keep keys
explicit and low-cardinality (see pitfalls). The abstraction lets you swap the backing
store without touching the annotated code.

## Local vs distributed

- **Local (in-process)** — Caffeine. Fastest, no network hop, but each replica has its
  own copy, so entries can be inconsistent across pods and eviction on one doesn't
  evict on others. Fine for small, short-TTL, tolerant-of-staleness data.
- **Distributed** — Redis. One shared cache across all replicas, so invalidation is
  global and consistent; costs a network hop and adds Redis as a dependency (with its own
  resilience needs — a cache outage must degrade to hitting the source, not failing).

Choose local for small hot reference data where per-pod staleness is acceptable;
distributed when invalidation must be consistent across replicas or entries are large.
A two-tier (local in front of Redis) setup exists but only add it for a real reason.

## Caching patterns

- **Cache-aside (lazy)** — app checks cache, on miss loads from source and populates.
  The default; what `@Cacheable` does. Simple and resilient (cache down → just slower).
- **Read-through / write-through** — the cache layer loads/writes the source itself.
  More moving parts; usually unnecessary with the Spring abstraction.
- **Write-behind** — async write to source. Powerful but risks data loss; use rarely and
  deliberately.
- **Event-driven local read-model** — a service keeps its own queryable copy fed by the
  owner's events (see `architecture-and-design.md`); a durable form of "cache" whose
  invalidation *is* the event stream.

## Invalidation — the hard part

Stale cache is a correctness bug that looks like a data bug, so design invalidation
first, not last:

- **Evict on write** — the owning write path evicts/updates the entry (`@CacheEvict`).
  Straightforward within one service.
- **Cross-service invalidation** — when another service caches your data, it can't see
  your writes. Feed invalidation via **events** ("product updated" → consumers evict), or
  accept bounded staleness with a **short TTL**. Never assume another service's cache
  will magically reflect your change.
- **TTL as a safety net** — even with explicit eviction, a TTL bounds how wrong you can
  be if an eviction is missed. Pick it from how much staleness the data tolerates.
- **Beware partial/derived caches** — if you cache a computed aggregate, every input
  change must invalidate it; these are the entries most often forgotten.

## Stampede / thundering herd

When a hot key expires, many concurrent requests all miss and hammer the source at once —
sometimes enough to take it down. Defenses: a **per-key lock / request coalescing** so
only one request recomputes while others wait; **early/probabilistic recomputation**
(refresh before expiry); and staggered TTLs (jitter) so many keys don't expire
simultaneously. Flag an unprotected hot cache in review — it's a latent outage.

## Pitfalls to flag

- **Unbounded key cardinality** — caching per-user or per-request-id blows up memory and
  gets ~0% hit rate. Cache low-cardinality, high-reuse keys.
- **Caching writes or non-idempotent results** — leads to stale or wrong data served as
  truth.
- **Cache as source of truth** — a cache is a disposable copy; the system must be correct
  (if slower) with the cache empty. If losing the cache loses data, it's not a cache.
- **No degradation on cache outage** — a Redis blip should fall back to the source, not
  fail requests. Wrap distributed-cache calls accordingly.
- **Caching sensitive data** without considering where it lives (a shared Redis with
  PII — see `compliance-and-data-privacy.md`).
