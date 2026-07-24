# Layered, cache-friendly, non-root Dockerfile for a Spring Boot service.
# For most cases, `mvn spring-boot:build-image` / `./gradlew bootBuildImage`
# (Cloud Native Buildpacks) is easier and produces an equally good image —
# use this when you need explicit control.

# 1) Build
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw -q clean package -DskipTests
# Gradle: RUN ./gradlew clean bootJar -x test

# 2) Extract Spring Boot layers (deps change slowly, app code changes fast)
FROM eclipse-temurin:25-jre AS layers
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# 3) Runtime — JRE only, non-root user, layers copied slow->fast for caching
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd -r -u 1001 appuser
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
