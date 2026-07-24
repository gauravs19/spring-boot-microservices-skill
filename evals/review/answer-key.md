# Answer key — `flawed-service`

Planted defects, by severity. Used to score recall/precision of a review.

## Critical (security / correctness)

1. **Secrets in `application.properties`** — DB password and `ak_live_` API key
   committed. (`application.properties`)
2. **Security disabled** — `anyRequest().permitAll()`; every endpoint public.
   (`SecurityConfig.java`)
3. **Actuator fully exposed** — `management.endpoints.web.exposure.include=*`, which
   (with #2) leaks the secrets in #1 via `/actuator/env`. (`application.properties`)
4. **`ddl-auto=update` in prod** — Hibernate mutating the production schema.
   (`application.properties`)
5. **Functional/logic bug** — the inventory response is fetched into `stock` and then
   **ignored**; the order is confirmed regardless. It's also keyed by `customerId`
   (stock is not per-customer) and uses a hardcoded URL instead of the configured
   `inventory.api.url`. (`OrderController.java`) — *the standards-only pass tends to
   miss this; correctness-first should catch it.*

## High (reliability / data integrity)

6. **No timeout** on the `RestTemplate` remote call. (`OrderController.java`)
7. **Remote call inside `@Transactional`** — holds a DB connection across the network.
   (`OrderController.java`)
8. **Swallowed/opaque exception** — `catch (Exception)` rewrapped into a generic
   RuntimeException, leaking `getMessage()`; no error model. (`OrderController.java`)
9. **`findById(id).get()`** → 500 instead of 404 for a missing order.
   (`OrderController.java`)
10. **JPA entity exposed as the API** (request + response) with mass-assignment risk.
    (`OrderController.java`)
11. **`EAGER` `@OneToMany`** on `lines` → over-fetch / N+1. (`Order.java`)

## Medium (modernization / maintainability)

12. **EOL stack** — Spring Boot 2.7.5, Java 8, Hystrix, `WebSecurityConfigurerAdapter`
    (removed in Security 6), `javax.*` not migrated to Jakarta. (`build.gradle`,
    `Order.java`, `SecurityConfig.java`)
13. **Spring Cloud dep pinned by hand** (Hystrix `2.2.10.RELEASE`) instead of via a
    release-train BOM. (`build.gradle`)
14. **No request validation** on `@RequestBody Order`. (`OrderController.java`)
15. **Unbounded `findAll()`** — no pagination. (`OrderController.java`)
16. **Field injection** (`@Autowired` field) instead of constructor injection.
    (`OrderController.java`)
17. **No observability** — no metrics/tracing/structured logging; `show-sql=true` as a
    stand-in. (whole project)

## Low / nits

- Virtual threads not enabled (relevant post-migration).
- `Order` entity has no `equals`/`hashCode` / `@Version` (optimistic locking).
- `OrderRepository` / `OrderLine` referenced but absent (a good reviewer notes they
  couldn't be verified rather than inventing findings about them).

## Scoring notes

- **Recall** over items 1–17. Finding #5 (the logic bug) is weighted heavily — it's
  the differentiator.
- **Precision**: subtract for fabricated issues (e.g. inventing bugs about the missing
  `OrderRepository`).
- **Prioritisation**: items 1–4 must appear as Critical/top.
