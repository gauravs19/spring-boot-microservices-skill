# Principles & Anti-patterns

The cross-cutting through-lines that apply in every mode, and the anti-patterns worth
actively pushing back on. These are referenced from `SKILL.md`; the one-line versions
live there, the reasoning lives here.

## Table of contents
- [Cross-cutting principles](#cross-cutting-principles)
- [Anti-patterns to actively push back on](#anti-patterns-to-actively-push-back-on)

## Cross-cutting principles

These separate a service that merely runs from one a team can operate at 2am. Weave
them in everywhere rather than treating them as a final checklist.

- **Observability is not optional.** Every service ships with metrics (Micrometer),
  distributed tracing (Micrometer Tracing → OpenTelemetry), and structured logs
  correlated by trace ID from day one. Adding it after an incident is too late. See
  `observability.md`.
- **Design for failure.** Every remote call has a timeout, a retry policy where safe,
  and a circuit breaker. The default assumption is that dependencies *will* be slow or
  down. See `resilience-and-communication.md`.
- **Configuration and secrets are externalized.** No secrets in code, images, or git.
  Config comes from the environment / config server / K8s; profiles keep environments
  separated. See `configuration-and-profiles.md`.
- **Virtual threads by default.** On Java 21+, prefer virtual threads
  (`spring.threads.virtual.enabled=true`) for the simple, scalable thread-per-request
  model, unless there's a specific reason to go reactive.
- **Tests are part of "done".** A feature without tests isn't finished. Favor fast
  slice tests plus Testcontainers-backed integration tests over brittle mocks of
  infrastructure. See `testing.md`.
- **Statelessness & the 12-factor mindset.** Services should be horizontally scalable
  and disposable; state lives in datastores, not in memory.
- **Least surprise.** Follow Spring idioms and the project's own conventions. Clever,
  non-idiomatic code is a liability in a service many people maintain.

## Anti-patterns to actively push back on

Naming these matters because they're common, they look reasonable, and letting them
slide is how services rot. When you see one, say so and explain the cost — don't
silently work around it.

- **Distributed monolith** — services so chatty and synchronously coupled they must
  deploy together. Fix by rethinking boundaries and going async.
- **Shared database across services** — kills independent evolution and ownership.
  Each service owns its schema.
- **Anemic + leaky layering** — entities used as API DTOs, business logic in
  controllers, `@Autowired` field injection. Use constructor injection, keep the web
  layer thin, don't expose JPA entities over the wire.
- **Legacy stack presented as current** — `javax.*`, Zuul 1, Hystrix, Netflix Ribbon,
  Java 8/11, `WebSecurityConfigurerAdapter`. These are end-of-life; flag them.
- **Swallowed exceptions & generic 500s** — no error model, `catch (Exception e) {}`,
  stack traces leaked to clients. Use Problem Details and a global handler.
- **Security theater** — `permitAll` everywhere, secrets in `application.properties`,
  JWTs validated loosely. Security is a first-class concern.
- **"We'll add observability/tests/resilience later."** Later rarely comes; the cost of
  retrofitting is much higher than building it in.
