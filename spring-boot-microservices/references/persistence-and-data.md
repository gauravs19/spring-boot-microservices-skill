# Persistence & Data

Data access done well: correct transaction boundaries, no accidental N+1 queries,
versioned schema migrations, and clear data ownership. For deep query-performance
tuning (indexing strategy, execution plans, pool sizing), defer to the dedicated
`perf-review-be` skill — this file covers the application-side design.

## Table of contents
- [Choosing JPA vs JDBC vs R2DBC](#choosing-jpa-vs-jdbc-vs-r2dbc)
- [Entity design](#entity-design)
- [Repositories](#repositories)
- [The N+1 problem](#the-n1-problem)
- [Transactions](#transactions)
- [Schema migrations](#schema-migrations)
- [Connection pooling](#connection-pooling)
- [Data ownership across services](#data-ownership-across-services)

## Choosing JPA vs JDBC vs R2DBC

- **Spring Data JPA (Hibernate)** — the default for most services. Rich mapping,
  productivity, and a huge ecosystem. The cost is a leaky abstraction: you must
  understand fetching, the persistence context, and flushing, or it will generate
  surprising queries.
- **Spring Data JDBC** — a simpler, more predictable alternative when you want
  explicit control and don't need JPA's lazy loading / dirty checking. Maps
  aggregates directly; fewer surprises.
- **R2DBC** — reactive, non-blocking database access, only for fully-reactive
  (WebFlux) services under genuine high-concurrency I/O pressure. With virtual threads
  available, most services get scalable blocking access without R2DBC's complexity, so
  don't reach for it by default.

Recommend JPA unless there's a concrete reason to prefer the others; recommend
JDBC when the team wants predictability over ORM magic.

## Entity design

- Annotate JPA entities with `jakarta.persistence.*` (never `javax.*`).
- Prefer field access, a surrogate key (`@Id @GeneratedValue`), and be explicit about
  fetch types. **Default associations to `LAZY`** — `EAGER`, especially on
  `@ManyToOne`/`@OneToMany`, is a leading cause of over-fetching and N+1.
- Keep entities as domain objects with behavior, not anemic bags of getters/setters,
  and never expose them over the API (map to DTOs — see `rest-api-design.md`).
- Use optimistic locking (`@Version`) for concurrent updates rather than pessimistic
  locks unless you truly need them.

## Repositories

Use Spring Data repository interfaces and derived query methods for the common cases,
`@Query` (JPQL) for anything non-trivial, and `Pageable` for lists:

```java
interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByCustomerId(String customerId, Pageable pageable);

    @Query("select o from Order o join fetch o.lines where o.id = :id")
    Optional<Order> findByIdWithLines(Long id);   // fetch join to avoid N+1
}
```

Keep repositories focused on data access; business logic belongs in the service layer.

## The N+1 problem

The most common Hibernate performance defect: loading a list of N entities then
triggering one extra query per element to load an association (N+1 total). It's
invisible until the data grows. Defenses:

- Use `join fetch` (as above) or an `@EntityGraph` to load associations in one query
  when you know you'll need them.
- Turn on SQL logging in dev (`spring.jpa.show-sql` or, better, the
  `hibernate.SQL` logger) and watch query counts in tests.
- For serialization-time N+1, the DTO-mapping rule already helps: map inside the
  transaction, fetching exactly what the DTO needs.

Flag N+1 explicitly in reviews — it's a High finding because it scales with data.

## Transactions

- Put `@Transactional` on the **service layer**, not on controllers or repositories —
  the service method is the natural unit of work / business transaction.
- Mark read-only operations `@Transactional(readOnly = true)` so the provider can
  optimize (no dirty checking, better connection handling).
- Understand that `@Transactional` is proxy-based: **self-invocation** (a method in the
  same bean calling another `@Transactional` method directly) bypasses the proxy and
  the annotation is silently ignored — a subtle, common bug. Split into separate beans
  if you need it.
- Keep transactions short and don't do remote calls (HTTP, messaging) inside them —
  holding a DB connection open across a network call is how you exhaust the pool. Use
  the outbox pattern to publish events transactionally (see `messaging-and-events.md`).

## Schema migrations

Never let Hibernate auto-generate or update production schema
(`spring.jpa.hibernate.ddl-auto` must be `validate` or `none` in prod — `update`/`create`
in production is a Critical finding). Manage schema with **Flyway** or **Liquibase**:
versioned, reviewed, repeatable migration scripts checked into git and applied on
startup or via CI. This gives you auditable, reversible schema evolution and keeps every
environment identical. Flyway (plain SQL migrations) is the simplest good default;
Liquibase adds database-agnostic changelogs if you need them.

## Connection pooling

Spring Boot uses **HikariCP** by default — fast and reliable. The most important knob
is pool size, and bigger is not better: a pool far larger than the database can
usefully serve just queues work and hides the real bottleneck. Size it deliberately
against the database's capacity, set connection and validation timeouts, and monitor
pool metrics (Actuator exposes them). Deep sizing/tuning is `perf-review-be` territory.

## Data ownership across services

Each service owns its own schema; no other service reads or writes it directly (see
`architecture-and-design.md`). When another service needs the data, it either calls the
owner's API or maintains a local read model fed by the owner's events. Sharing a
database across services is a Critical architectural finding — it silently recouples
everything you split apart.
