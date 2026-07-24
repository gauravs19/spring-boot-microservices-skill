# Project Setup

How to create a correct, modern Spring Boot project and structure it well, for both
Maven and Gradle. Ready-to-adapt build files live in `assets/templates/`.

## Table of contents
- [Start from Spring Initializr](#start-from-spring-initializr)
- [Java version & toolchain](#java-version--toolchain)
- [Choosing dependencies](#choosing-dependencies)
- [Maven setup](#maven-setup)
- [Gradle setup](#gradle-setup)
- [Package & module structure](#package--module-structure)
- [The baseline every service needs](#the-baseline-every-service-needs)
- [Virtual threads](#virtual-threads)

## Start from Spring Initializr

Generate the base from **start.spring.io** rather than hand-writing a build file from
memory. Initializr guarantees a coherent, current dependency set with correctly
aligned versions — including the Spring Cloud release train that matches your Boot
version, which is exactly the thing that's easy to get wrong by hand. Then customize.
You can drive it from the CLI:

```bash
curl https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d javaVersion=25 \
  -d bootVersion=4.0.0 \
  -d dependencies=web,actuator,data-jpa,postgresql,validation,testcontainers \
  -d groupId=com.example -d artifactId=orders-service \
  -d packageName=com.example.orders \
  -o orders-service.zip
```

Swap `type=gradle-project` (or `gradle-project-kotlin` for the Kotlin DSL) for
Gradle. If you cannot verify the current `bootVersion`, omit it and let Initializr
pick its default — that's safer than pinning a version you're unsure about.

## Java version & toolchain

Target **Java 25 (LTS)**; accept Java 21 (LTS) as the floor. Pin the version through
the build so every environment compiles and runs identically:

- Maven: set `<java.version>25</java.version>` (the Spring Boot parent maps this to
  the compiler release).
- Gradle: use a **toolchain** so the build downloads/uses the right JDK regardless of
  the machine's default:
  ```kotlin
  java {
      toolchain { languageVersion = JavaLanguageVersion.of(25) }
  }
  ```

## Choosing dependencies

Start minimal and add deliberately — every starter is surface area to maintain and
secure. A typical HTTP-plus-database service needs:

- `spring-boot-starter-web` (MVC + embedded Tomcat) — or `...-webflux` only if you
  have a real reason to go fully reactive. With virtual threads, plain MVC scales well
  for most workloads without the reactive learning curve.
- `spring-boot-starter-actuator` — health, metrics, and management endpoints. Not
  optional for a production service.
- `spring-boot-starter-data-jpa` + a JDBC driver (`postgresql`) — or
  `...-data-r2dbc` for reactive persistence.
- `spring-boot-starter-validation` — Bean Validation for request DTOs.
- `spring-boot-starter-security` — the moment the service is anything but a throwaway.
- `spring-boot-starter-test` (test scope) — JUnit 5, Mockito, AssertJ.
- `spring-boot-testcontainers` + Testcontainers modules (test scope) — real
  dependencies in integration tests.

Add observability (`micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`)
and resilience (`spring-cloud-starter-circuitbreaker-resilience4j`) as the service
grows toward production; see the respective reference files.

## Maven setup

Inherit from the Spring Boot parent so versions are managed for you, and import the
Spring Cloud BOM in `dependencyManagement` when you use Spring Cloud — never pin Cloud
artifact versions individually. See `assets/templates/pom.xml` for a complete,
commented starter. Key points:

- Use the `spring-boot-maven-plugin` for the repackaged executable jar and for
  building OCI images (`spring-boot:build-image`).
- Put the Spring Cloud version in a `<spring-cloud.version>` property matching the
  compatibility matrix for your Boot version, imported as a BOM with
  `<scope>import</scope>` and `<type>pom</type>`.

## Gradle setup

Use the `org.springframework.boot` and `io.spring.dependency-management` plugins so
the BOM manages versions. See `assets/templates/build.gradle.kts`. Key points:

- Apply the dependency-management plugin and import the Spring Cloud BOM via
  `imports { mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion") }`.
- Prefer the Kotlin DSL (`build.gradle.kts`) for type-safety and IDE support even in
  a Java project.
- Use `bootBuildImage` for OCI images.

## Package & module structure

Two structures work; pick based on service size and the team's DDD maturity.

**Package-by-feature (recommended default).** Group by business capability, so a
feature's web/service/domain/persistence classes sit together and boundaries are
visible:

```
com.example.orders
├── OrdersApplication.java
├── order/            # a feature/aggregate
│   ├── OrderController.java
│   ├── OrderService.java
│   ├── Order.java            # domain
│   ├── OrderRepository.java
│   └── dto/
├── payment/
└── shared/           # cross-feature config, error handling, etc.
```

**Package-by-layer** (`controller/`, `service/`, `repository/`, `model/`) is common
and fine for very small services, but it scatters each feature across four packages
and hides boundaries. For anything non-trivial, feature packages age better and make
an eventual split into services trivial. If you're contributing to an existing
codebase, match whatever it already uses — consistency wins.

For a modular monolith, use Spring Modulith and treat each top-level feature package
as a module with an explicit API package; see `architecture-and-design.md`.

## The baseline every service needs

Wire these once, at project creation, so they're never "added later":

1. **Actuator with Kubernetes probes** — expose `health`, `info`, `metrics`,
   `prometheus`; enable liveness/readiness groups. See `containerization-and-k8s.md`
   and `observability.md`.
2. **Global error handling** — a `@RestControllerAdvice` producing Problem Details
   (RFC 9457). See `rest-api-design.md`.
3. **Structured logging** with trace correlation. See `observability.md`.
4. **Externalized config & profiles** — no hardcoded environment values. See
   `configuration-and-profiles.md`.
5. **Virtual threads enabled** (below).

## Virtual threads

On Java 21+, enable virtual threads so each request runs on a lightweight,
cheap-to-block thread — you keep the simple, readable thread-per-request programming
model while scaling to very high concurrency, without adopting reactive code:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Caveats worth knowing: avoid `synchronized` blocks around blocking I/O on hot paths
(they can pin the carrier thread — prefer `ReentrantLock`), and be aware that some
older libraries using thread-locals heavily may behave differently. For the vast
majority of CRUD/HTTP services, virtual threads are the right default and remove most
reasons to reach for WebFlux.
