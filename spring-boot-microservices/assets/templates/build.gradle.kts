// Modern Spring Boot microservice starter (Gradle, Kotlin DSL).
//
// Version note: verify the plugin/BOM versions against start.spring.io and the
// Spring Cloud compatibility matrix. springCloudVersion MUST be the release
// train matching the Spring Boot generation — never chosen independently.

plugins {
    java
    id("org.springframework.boot") version "4.0.0"            // verify current GA
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    // Toolchain: build uses this JDK regardless of the machine default.
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

extra["springCloudVersion"] = "2025.0.0"   // MUST match the Boot generation above

repositories { mavenCentral() }

dependencies {
    // Web (MVC). Use spring-boot-starter-webflux only if fully reactive.
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Operational endpoints, health probes, metrics
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Persistence (swap for data-r2dbc if reactive)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Bean Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security: stateless OAuth2 resource server
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Schema migrations
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Observability: metrics + tracing -> OpenTelemetry
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Resilience
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")

    // OpenAPI / Swagger UI (verify current springdoc version)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.springframework.security:spring-security-test")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

tasks.withType<Test> { useJUnitPlatform() }

// `./gradlew bootBuildImage` builds an optimized OCI image via buildpacks.
