# Configuration & Profiles

Externalized, environment-agnostic configuration with no secrets in code. This is a
12-factor cornerstone: the same build artifact runs in every environment, and only
the injected config differs.

## Table of contents
- [The externalization rule](#the-externalization-rule)
- [application.yml and property precedence](#applicationyml-and-property-precedence)
- [Profiles](#profiles)
- [Type-safe configuration properties](#type-safe-configuration-properties)
- [Config in Kubernetes](#config-in-kubernetes)
- [Spring Cloud Config Server](#spring-cloud-config-server)
- [Secrets](#secrets)

## The externalization rule

Configuration that varies by environment (URLs, credentials, feature flags, pool
sizes) must come from outside the artifact — environment variables, mounted config,
or a config server. Nothing environment-specific and nothing secret belongs in the
jar or the image. The test is simple: you should be able to promote the exact same
image from dev to prod and change only injected config.

## application.yml and property precedence

Prefer YAML (`application.yml`) over `.properties` for nested config readability.
Spring Boot resolves properties from many sources in a well-defined precedence order;
the practical rules you need:

- Command-line args and OS environment variables override file-based config, so
  containers/K8s can override anything.
- Environment variables map to properties by relaxed binding:
  `SPRING_DATASOURCE_URL` → `spring.datasource.url`. This is how you inject config in
  containers without editing files.
- Use `spring.config.import` to pull in additional config (config server, extra
  files, Vault) declaratively.

Reference other properties and provide defaults with `${...}`:
```yaml
server:
  port: ${PORT:8080}
```

## Profiles

Profiles let one artifact carry environment- or purpose-specific slices of config.
Use `application-<profile>.yml` files and activate with `SPRING_PROFILES_ACTIVE`.

Guidance:
- Keep a base `application.yml` with everything common, and thin profile files with
  only the deltas (`application-prod.yml`, `application-local.yml`).
- Reserve a `test` profile for test-only overrides, but prefer Testcontainers +
  `@ServiceConnection` over hardcoding test datasources (see `testing.md`).
- Avoid profile sprawl and profile-specific *code* (`@Profile` on beans) beyond
  genuinely different implementations (e.g. a no-op mailer locally) — logic that
  branches on profile is a smell that config should be handling instead.
- Never bake production credentials into any committed profile file.

## Type-safe configuration properties

Bind related properties into an immutable record rather than scattering `@Value`
injections, which are stringly-typed and hard to validate:

```java
@ConfigurationProperties(prefix = "app.orders")
@Validated
public record OrdersProperties(
        @NotNull Duration reservationTimeout,
        @Positive int maxItemsPerOrder) {}
```

Enable with `@ConfigurationPropertiesScan` on the application class. This gives you
validation at startup (fail fast on bad config), IDE metadata, and one obvious place
per concern. Prefer it strongly over `@Value` for anything beyond a one-off.

## Config in Kubernetes

In Kubernetes the idiomatic sources are **ConfigMaps** (non-secret) and **Secrets**
(sensitive), surfaced to the app as environment variables or mounted files. This is
often all a service needs — many teams no longer run a Spring Cloud Config Server
because K8s already provides the config plane. Spring Boot can also watch mounted
config for changes. Prefer this over a config server unless you have cross-platform
needs or want git-backed config history. See `containerization-and-k8s.md`.

## Spring Cloud Config Server

A centralized, often git-backed config server is useful when you want a single
audited source of truth across many services and environments, especially outside
Kubernetes. Services declare `spring.config.import=configserver:...` to pull config at
startup. Combine with Spring Cloud Bus or Actuator's `refresh` to propagate changes
without redeploying. Weigh it against plain K8s config — it's real infrastructure to
run and secure, so only adopt it if it earns its keep.

## Secrets

Secrets get their own discipline because a leaked secret is a security incident:

- **Never** commit secrets to git, embed them in images, or log them. Scan for
  accidental commits.
- Inject at runtime from a secrets manager: Kubernetes Secrets (ideally backed by an
  external store), HashiCorp Vault (via `spring-cloud-vault`), or the cloud provider's
  secret manager (AWS Secrets Manager, Azure Key Vault, GCP Secret Manager).
- Rotate credentials; prefer short-lived, dynamically-issued secrets (e.g. Vault
  database credentials) where the platform supports it.
- In review mode, secrets in `application.properties`/`.yml` or in the image are a
  Critical finding — call it out first.
