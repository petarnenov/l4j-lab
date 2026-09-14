# syntax=docker/dockerfile:1
# Built by the project's own Gradle build, so the packaged application is the one under test.
# From the repository root:  docker build -f token-issuer/Dockerfile .

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts ./
COPY gradle/ gradle/
# settings.gradle.kts configures every subproject, so each build file must exist even though
# nothing of the others is built here.
COPY frontend/build.gradle.kts frontend/.nvmrc frontend/
COPY backend/build.gradle.kts backend/
COPY mcp-server/build.gradle.kts mcp-server/
COPY legacy-billing-api/build.gradle.kts legacy-billing-api/
COPY token-issuer/build.gradle.kts token-issuer/
COPY token-issuer/ token-issuer/
RUN --mount=type=cache,target=/root/.gradle ./gradlew :token-issuer:installDist --no-daemon

FROM eclipse-temurin:25-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 app
COPY --from=build /src/token-issuer/build/install/token-issuer/ /app/
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["/app/bin/token-issuer"]
