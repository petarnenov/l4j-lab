plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// R-003: one name for one application, the same as the API title and the frontend package.
rootProject.name = "financial-agent-chain"

// R-004: the frontend is a Gradle subproject only so `./gradlew check` reaches it. npm still builds it.
include("backend", "frontend")

// Feature 007: the MCP billing server and the two services it needs to be interesting — a legacy REST
// API that owns the data and enforces entitlements, and a development-only token issuer. Three
// subprojects rather than one, because the topology is the lesson (specs/007-mcp-billing-server/plan.md).
include("mcp-server", "legacy-billing-api", "token-issuer")
