# syntax=docker/dockerfile:1
# R-004: the backend image is built by the project's own Gradle build, so the packaged application is
# the application under test. Build it from the repository root:
#   docker build -f backend/Dockerfile .

# Stage 1: build. The JDK and the Gradle wrapper exist only here, never in the runtime image.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts ./
COPY gradle/ gradle/
# settings.gradle.kts includes the frontend project, so its build file must exist for Gradle to
# configure. Nothing of the frontend is built here.
COPY frontend/build.gradle.kts frontend/.nvmrc frontend/
COPY backend/ backend/
# installDist writes a launcher script and a lib/ directory: exactly what runs, readable in the image.
RUN --mount=type=cache,target=/root/.gradle ./gradlew :backend:installDist --no-daemon

# Stage 2: runtime. A JRE, curl for the readiness health check (the JRE image has neither curl nor
# wget), and an unprivileged user.
FROM eclipse-temurin:25-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 app
COPY --from=build /src/backend/build/install/backend/ /app/
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["/app/bin/backend"]
