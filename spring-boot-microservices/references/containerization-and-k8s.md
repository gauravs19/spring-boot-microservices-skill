# Containerization & Kubernetes

Turning a Spring Boot service into an efficient container image and running it well on
Kubernetes. Also covers GraalVM native images for fast startup / low memory.

## Table of contents
- [Building the image](#building-the-image)
- [Layered jars & cache-friendly Dockerfiles](#layered-jars--cache-friendly-dockerfiles)
- [Image hygiene](#image-hygiene)
- [Startup optimization: virtual threads, AOT, CDS, native](#startup-optimization-virtual-threads-aot-cds-native)
- [Kubernetes essentials](#kubernetes-essentials)
- [Probes](#probes)
- [Resources, config, and graceful shutdown](#resources-config-and-graceful-shutdown)

## Building the image

Two good options, both better than a naive hand-written Dockerfile:

- **Cloud Native Buildpacks** via `bootBuildImage` (Gradle) or `spring-boot:build-image`
  (Maven) — Spring Boot builds an optimized, layered OCI image with no Dockerfile,
  including sensible JVM settings and a non-root user. This is the easiest way to get a
  good image and the recommended default.
- **A layered Dockerfile** when you need full control (below).

## Layered jars & cache-friendly Dockerfiles

Spring Boot produces **layered jars** that separate slow-changing dependencies from
fast-changing application code, so Docker layer caching only rebuilds/ships the tiny app
layer on a code change. Use a multi-stage build that extracts the layers:

```dockerfile
# build stage
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw -q clean package -DskipTests

# extract layers
FROM eclipse-temurin:25-jre AS layers
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# runtime stage
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd -r -u 1001 appuser
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./
USER appuser
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

See `assets/templates/Dockerfile` for a ready copy.

## Image hygiene

- **Run as non-root** (as above) — a container running as root is an unnecessary risk.
- **Use a JRE, not JDK, at runtime**, and a slim/distroless base to shrink attack
  surface and size.
- **Don't bake secrets or environment config into the image** — inject at runtime (see
  `configuration-and-profiles.md`). A secret in an image layer is a leaked secret.
- **Scan images** for vulnerabilities in CI (Trivy, Grype, or your registry's scanner).
- Pin base image versions rather than floating `latest` for reproducibility.

## Startup optimization: virtual threads, AOT, CDS, native

Fast startup matters for autoscaling and scale-to-zero. Options, cheapest first:

- **Virtual threads** (Java 21+) — enable `spring.threads.virtual.enabled=true` for
  high concurrency with the simple blocking model (see `project-setup.md`).
- **AOT + CDS (Class Data Sharing)** — Spring Boot's AOT processing plus Application CDS
  cuts JVM startup meaningfully with almost no downside; Boot can create and use a CDS
  archive for you. A good default for the JVM path.
- **GraalVM native image** — compiles to a native executable with millisecond startup
  and much lower memory, ideal for serverless/scale-to-zero. The cost is longer builds
  and reflection/proxy constraints (Spring's AOT handles most, but some libraries need
  hints, and testing native adds effort). Choose it when startup/memory genuinely matter;
  otherwise the JVM with AOT+CDS is simpler.

## Kubernetes essentials

A production Deployment needs, at minimum: liveness/readiness/startup probes, resource
requests and limits, externalized config via ConfigMap/Secret, multiple replicas, and a
graceful-shutdown setup. Don't run a bare Pod; use a Deployment (or the appropriate
workload) so the platform manages rollout and self-healing.

## Probes

Wire Actuator's health groups to Kubernetes probes (see `observability.md` for the
liveness-vs-readiness distinction, which is easy to get wrong):

```yaml
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  initialDelaySeconds: 10
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8080 }
startupProbe:                       # protects slow-starting apps from premature liveness kills
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  failureThreshold: 30
  periodSeconds: 5
```

Use a **startup probe** for apps that take a while to boot so the liveness probe doesn't
kill them mid-startup — a common cause of crash-loops.

## Resources, config, and graceful shutdown

- **Requests/limits** — set memory requests/limits and let the JVM respect the
  container memory (modern JDKs are container-aware; size the heap sensibly). No limits
  risks noisy-neighbor eviction; too-tight limits risk OOM kills.
- **Config** — mount ConfigMaps/Secrets as env vars or files; the same image runs in
  every environment (see `configuration-and-profiles.md`).
- **Graceful shutdown** — enable `server.shutdown=graceful` and set a
  `spring.lifecycle.timeout-per-shutdown-phase` so in-flight requests finish during a
  rolling deploy; align it with the pod's `terminationGracePeriodSeconds`. Combined with
  readiness flipping to down on SIGTERM, this gives zero-dropped-request deploys.
