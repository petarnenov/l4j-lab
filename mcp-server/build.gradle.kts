plugins {
    id("io.micronaut.application") version "5.0.2"
}

// Feature 007. The MCP server: protocol revision 2026-07-28 on top of the MCP Java SDK.
version = "0.1.0"
group = "dev.l4jlab.mcp"

repositories {
    mavenCentral()
}

// Constitution: the Java release is declared once, through the toolchain.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

micronaut {
    // Constitution: Micronaut is pinned once, through the Micronaut Platform BOM.
    version = "5.1.5"
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(false)
        annotations("dev.l4jlab.mcp.*")
    }
}

dependencies {
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    annotationProcessor("io.micronaut.data:micronaut-data-processor")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")
    // R-014: generates the JSON Schema for each @Tool's arguments and return type at compile time.
    annotationProcessor("io.micronaut.jsonschema:micronaut-json-schema-processor")

    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.flyway:micronaut-flyway")
    // FR-029 and the load balancer's startup order both read /health/readiness.
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut.reactor:micronaut-reactor")
    implementation("jakarta.validation:jakarta.validation-api")
    // FR-015: this server verifies its own tokens; it shares no secret with the issuer.
    implementation("com.nimbusds:nimbus-jose-jwt")

    // FR-028 and FR-029: the MCP server is hosted by the Micronaut MCP integration, which the
    // Platform BOM above already manages. It brings the MCP Java SDK (mcp-core) transitively,
    // plus compile-time @Tool discovery, argument binding, schema generation, a
    // JsonSchemaValidator, and protocol error mappers. Inventoried in research.md R-014.
    //
    // Only the seven 2026-07-28 deltas listed in R-014 are written by hand, each against an
    // advertised Micronaut seam and each naming the upstream issue that would retire it.
    implementation("io.micronaut.mcp:micronaut-mcp-server-java-sdk")
    // @Tool generates inputSchema/outputSchema from the method's parameter and return types.
    implementation("io.micronaut.jsonschema:micronaut-json-schema-annotations")

    // No micronaut-openapi. The MCP server's external contract is the five committed JSON Schema
    // tool definitions, not an HTTP API description; see plan.md Constitution Check.

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.yaml:snakeyaml")

    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("org.assertj:assertj-core")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")

    // T006, FR-030: test scope only. LangChain4j has no MCP server; its one contribution here is a
    // compatibility test proving a real legacy client gets the documented diagnostic, because its
    // DefaultMcpClient always sends `initialize` and so cannot speak this revision.
    // R-014: taken from the Micronaut integration, which exists for exactly this purpose, rather
    // than wiring dev.langchain4j:langchain4j-mcp by hand.
    testImplementation("io.micronaut.mcp:micronaut-mcp-client-langchain4j")
}

application {
    mainClass = "dev.l4jlab.mcp.Application"
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.compilerArgs.addAll(
        listOf(
            "-parameters",
            "-Amicronaut.processing.group=dev.l4jlab.mcp",
        ),
    )
}

// T042, revised by R-014: the committed contracts are the *oracle*, not a runtime resource.
// @Tool generates the schemas from the Java types; a contract test asserts the generated schema
// matches the committed JSON, so Principle III's ordering holds and drift fails the build.
val copyToolContracts = tasks.register<Copy>("copyToolContracts") {
    from(rootProject.layout.projectDirectory.dir("specs/007-mcp-billing-server/contracts/tools"))
    into(layout.buildDirectory.dir("resources/test/contracts"))
    include("*.json")
}
tasks.named("processTestResources") { finalizedBy(copyToolContracts) }
tasks.named("testClasses") { dependsOn(copyToolContracts) }

// T007: the acceptance scenarios against the three-replica stack. A separate source set and task,
// never a flag — the same rule backend/build.gradle.kts applies to liveTest.
val topologyTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
    runtimeClasspath += output + compileClasspath
}

configurations["topologyTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["topologyTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

tasks.register<Test>("topologyTest") {
    description = "Acceptance scenarios against three MCP replicas behind the proxy. Requires `make mcp-up`."
    group = "verification"
    testClassesDirs = topologyTest.output.classesDirs
    classpath = topologyTest.runtimeClasspath
    useJUnitPlatform()
    systemProperty("micronaut.environments", "topology")
    // The outcome depends on a running stack, which Gradle does not track.
    outputs.upToDateWhen { false }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    systemProperty("micronaut.environments", "test")
}
