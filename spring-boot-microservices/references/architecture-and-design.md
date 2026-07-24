# Architecture & Design

Guidance for shaping services and the system around them. The goal is to make the
expensive-to-change decisions well, because architecture is precisely the set of
decisions that are costly to reverse later.

## Table of contents
- [Do you even need microservices?](#do-you-even-need-microservices)
- [Finding service boundaries](#finding-service-boundaries)
- [Modular monolith first (Spring Modulith)](#modular-monolith-first-spring-modulith)
- [Data ownership](#data-ownership)
- [Synchronous vs asynchronous communication](#synchronous-vs-asynchronous-communication)
- [Cross-service consistency](#cross-service-consistency)
- [Layering inside a service](#layering-inside-a-service)
- [Where cross-cutting concerns live](#where-cross-cutting-concerns-live)

## Do you even need microservices?

Microservices buy independent deployability, independent scaling, and team
autonomy — at the cost of distributed-systems complexity: network failures, eventual
consistency, distributed tracing, versioned contracts, and operational overhead. That
trade is worth it when you have multiple teams, genuinely independent scaling needs,
or clearly separable business capabilities. It is *not* worth it for a small team
building a product whose domain isn't yet well understood. In that case the honest
recommendation is a well-structured monolith, and you should say so even if the user
came in asking for microservices — leading them into premature distribution is a
disservice.

## Finding service boundaries

Draw boundaries around **business capabilities and their data**, using
Domain-Driven Design's bounded contexts as the guide. A good boundary is one where
the service can make most of its decisions and own most of its data locally, so that
a typical operation doesn't fan out into synchronous calls to several peers.

Signals you've drawn the line in the wrong place:
- A single user action requires synchronous calls across many services (chatty,
  temporally coupled — a distributed monolith).
- Two "services" always change together in the same pull request.
- Services share database tables or reach into each other's schema.

Boundaries by technical layer ("the DAO service", "the business-logic service") are
almost always wrong — they maximize coupling and minimize independent value.

## Modular monolith first (Spring Modulith)

When the domain isn't fully understood, the lowest-regret path is a **modular
monolith**: a single deployable with strong internal module boundaries that can be
split into services later once the seams are proven. **Spring Modulith** supports
this directly — it lets you declare modules, verify at build/test time that modules
only talk through allowed interfaces, publish in-process domain events that can later
become real messages, and generate documentation of the module structure. This gives
you most of the design discipline of microservices with a fraction of the operational
cost, and the eventual split is far safer because the boundaries are already enforced.

Recommend this pattern whenever the user is early, small, or unsure. It is the
single most impactful architectural steer in this skill.

## Data ownership

Each piece of data has exactly one owning service. Other services get a copy via
events or ask the owner via its API — they never read or write the owner's database
directly. A shared database is the fastest way to destroy the independence that
justified splitting in the first place, because now a schema change requires
coordinating every service that touches it.

Read models that other services need can be maintained locally by subscribing to the
owner's events (CQRS-lite), trading strong consistency for autonomy and speed.

## Synchronous vs asynchronous communication

Choose per interaction, not once globally:

- **Synchronous (REST/HTTP, gRPC):** use when the caller genuinely needs an answer
  now to proceed (a query, a command whose result the user is waiting on). The cost
  is temporal coupling — the callee must be up and fast, and failures cascade unless
  you add resilience.
- **Asynchronous (events over Kafka/RabbitMQ):** default for propagating state
  changes across services ("order placed", "user updated"). The publisher doesn't
  know or care who consumes, failures don't cascade synchronously, and services stay
  independently available. The cost is eventual consistency and the need for
  idempotent consumers.

A healthy estate leans async for state propagation and reserves sync for true
request/response. See `resilience-and-communication.md` and `messaging-and-events.md`.

## Cross-service consistency

There are no distributed ACID transactions across services — two-phase commit across
HTTP is an anti-pattern. Maintain consistency with:

- **Saga pattern** — model a multi-service operation as a sequence of local
  transactions, each publishing an event that triggers the next, with compensating
  actions to undo on failure. Choreographed (event-driven) sagas are simpler for a
  few steps; orchestrated sagas (a coordinator) scale better to complex flows.
- **Transactional outbox** — to publish an event reliably *and* commit your local DB
  change atomically, write the event to an outbox table in the same transaction, then
  relay it to the broker. This closes the "wrote to DB but crashed before publishing"
  gap. See `messaging-and-events.md`.
- **Idempotency** — every consumer must tolerate duplicate delivery, because
  at-least-once is the realistic delivery guarantee.

## Layering inside a service

Keep a thin, testable layering even in a small service:

- **Web/adapter layer** (`@RestController`) — HTTP concerns only: bind and validate
  input, map to/from DTOs, translate results and errors. No business logic here.
- **Application/service layer** (`@Service`) — orchestrates the use case, owns the
  transaction boundary (`@Transactional`), coordinates domain objects and
  repositories.
- **Domain** — the entities and business rules. Keep behavior on the domain objects,
  not scattered in service methods (avoid the "anemic domain model" where entities are
  just getters/setters).
- **Persistence layer** (`@Repository`) — data access; the rest of the app depends on
  its interface, not on JPA specifics.

Never expose JPA entities directly as API request/response bodies — it couples your
wire contract to your storage schema and creates lazy-loading and serialization
hazards. Map to DTOs at the boundary. See `rest-api-design.md`.

## Where cross-cutting concerns live

Decide these at the estate level so services are consistent:

- **Edge concerns** (routing, rate limiting, coarse auth, request aggregation) → API
  Gateway. See `spring-cloud-infra.md`.
- **Identity** → a central OAuth2/OIDC authorization server; services are resource
  servers validating tokens. See `security.md`.
- **Config** → config server or Kubernetes ConfigMaps/Secrets, not per-service files.
- **Observability & resilience defaults** → a shared internal starter/BOM so every
  service gets the same Micrometer, tracing, and Resilience4j baseline without
  copy-paste. This is one of the highest-leverage investments a platform team makes.
