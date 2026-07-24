# Resilience & Communication

How services call each other without turning one slow dependency into a system-wide
outage. The governing assumption: remote calls *will* fail, time out, and slow down —
design for it explicitly.

## Table of contents
- [The resilience baseline](#the-resilience-baseline)
- [HTTP clients: @HttpExchange, RestClient, OpenFeign](#http-clients-httpexchange-restclient-openfeign)
- [Timeouts](#timeouts)
- [Retries](#retries)
- [Circuit breakers](#circuit-breakers)
- [Bulkheads & rate limiters](#bulkheads--rate-limiters)
- [Fallbacks & graceful degradation](#fallbacks--graceful-degradation)
- [Putting it together with Resilience4j](#putting-it-together-with-resilience4j)

## The resilience baseline

Every synchronous remote call gets, at minimum, a **timeout**. Beyond that, layer on
**retries** (only where safe), a **circuit breaker**, and a **fallback** as the call's
criticality warrants. A call with no timeout is the single most dangerous pattern in
distributed systems: one hung dependency exhausts your threads/connections and takes
you down with it. In review mode, a remote call with no timeout is a High finding.

## HTTP clients: @HttpExchange, RestClient, OpenFeign

Prefer modern, declarative or fluent clients over raw `RestTemplate`:

- **`@HttpExchange` HTTP interface** (Spring Framework 6+) — declare a Java interface,
  Spring generates the client. Clean, framework-native, no extra dependency. This is
  the recommended default for new code:
  ```java
  @HttpExchange("/inventory")
  interface InventoryClient {
      @GetExchange("/{sku}")
      StockLevel stock(@PathVariable String sku);
  }
  ```
- **`RestClient`** — the modern synchronous fluent client (replaces `RestTemplate` for
  new code) when you want imperative control.
- **`WebClient`** — the reactive client; use in WebFlux services or when you need
  reactive composition.
- **Spring Cloud OpenFeign** — still supported and common in existing estates; fine to
  use, but `@HttpExchange` covers most of what teams used Feign for, without the extra
  dependency.

`RestTemplate` is maintenance-mode; don't write new code against it.

## Timeouts

Set both connection and read timeouts on every client. Choose values from real
latency budgets, not round numbers pulled from the air — a downstream that normally
responds in 50ms shouldn't have a 30s timeout, because that 30s is how long you'll
hang when it breaks. Configure timeouts on the underlying request factory / HTTP client
of your `RestClient`/`@HttpExchange` client.

## Retries

Retries recover from transient blips (a brief network hiccup, a rolling deploy), but
they are dangerous if misused:

- **Only retry idempotent operations.** Retrying a non-idempotent `POST` can
  double-charge a customer. Combine with idempotency keys (see `rest-api-design.md`)
  if you must retry writes.
- **Use exponential backoff with jitter**, not tight immediate retries — a synchronized
  retry storm can amplify an outage (retry amplification) and DDoS your own dependency.
- **Cap the attempts.** Infinite retries just move the pileup.

## Circuit breakers

A circuit breaker stops calling a failing dependency for a cooldown period, failing
fast instead of piling requests onto something already down, then probes to see if it
recovered (closed → open → half-open). This protects both the caller (threads aren't
consumed waiting) and the callee (it isn't hammered while struggling). Use
**Resilience4j** via Spring Cloud CircuitBreaker (Hystrix is EOL — don't use it).

## Bulkheads & rate limiters

- **Bulkhead** — isolate resources (thread pools / concurrency limits) per dependency
  so one slow downstream can't consume all capacity and starve unrelated calls. Named
  for ship compartments that stop one breach from sinking the vessel.
- **Rate limiter** — cap the call rate to a dependency (or to your own endpoints) to
  stay within its capacity and protect it from overload.

Resilience4j provides both as composable decorators.

## Fallbacks & graceful degradation

When a call fails or the breaker is open, decide what "degraded but alive" looks like:
return cached/last-known data, a sensible default, a partial response, or a clear
error — whatever keeps the user's core flow working. A recommendations widget failing
should not break checkout. Design fallbacks per call based on how essential that data
is; not every call needs one, but the critical paths do.

## Putting it together with Resilience4j

Resilience4j is the modern, lightweight fault-tolerance library (the Hystrix
successor). Add `spring-cloud-starter-circuitbreaker-resilience4j` (or the Resilience4j
Spring Boot starter) and configure per-instance in `application.yml`, then apply with
annotations:

```java
@CircuitBreaker(name = "inventory", fallbackMethod = "stockFallback")
@Retry(name = "inventory")
@TimeLimiter(name = "inventory")
StockLevel stock(String sku) { return inventoryClient.stock(sku); }

StockLevel stockFallback(String sku, Throwable t) {
    return StockLevel.unknown(sku);   // graceful degradation
}
```

```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventory:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
  retry:
    instances:
      inventory:
        max-attempts: 3
        wait-duration: 200ms
        enable-exponential-backoff: true
```

Resilience4j exposes metrics to Micrometer, so breaker state and retry counts show up
in your dashboards — wire that in (see `observability.md`) so degradation is visible,
not silent.
