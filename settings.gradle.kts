plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// R-003: one name for one application, the same as the API title and the frontend package.
rootProject.name = "financial-agent-chain"

// R-004: the frontend is a Gradle subproject only so `./gradlew check` reaches it. npm still builds it.
include("backend", "frontend")
