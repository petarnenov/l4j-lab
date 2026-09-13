plugins {
    id("io.micronaut.application") version "5.0.2"
}

// R-002: the application version, declared once. The OpenAPI description reads it through the
// api.version expand property below, and :frontend:checkVersion holds package.json to it.
version = "0.1.0"
group = "dev.l4jlab.chain"

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
        // R-001: off on purpose. An incremental compile regenerates the OpenAPI description from the
        // recompiled classes only, and Javadoc is readable from source alone, so every description on
        // an untouched class vanished and the contract check reported drift that was not there. A full
        // compile of this backend takes about two seconds.
        incremental(false)
        annotations("dev.l4jlab.chain.*")
    }
}

// T004: the annotation processor path. Nothing wires without this, and the symptom of a
// missing entry is a bean that does not exist at runtime rather than a compile error.
dependencies {
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    annotationProcessor("io.micronaut.data:micronaut-data-processor")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")
    annotationProcessor("io.micronaut.openapi:micronaut-openapi")

    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.openapi:micronaut-openapi-annotations")
    // Feature 004, R-006 and FR-019: /health/readiness, which the container health check and the load
    // balancer's startup order rely on. micronaut-flyway already brings this module in transitively;
    // it is declared here so readiness does not silently depend on another module's choices.
    implementation("io.micronaut:micronaut-management")
    implementation("jakarta.validation:jakarta.validation-api")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.yaml:snakeyaml")

    // R-002: the Micronaut Platform BOM decides the LangChain4j version. The constraint below
    // restates that choice so the pinned version is visible in this file; it does not override
    // the BOM, so there is still exactly one decision.
    implementation("dev.langchain4j:langchain4j-ollama")
    // Feature 005, research R-001: LangChain4j's agentic orchestration and AI Services (constitution
    // v3.0.0, Principle I). No version here: langchain4j-bom 1.18.0, imported by the platform BOM,
    // manages it as 1.18.0-beta28, the agentic release of the same train. The module is labeled beta;
    // its API may change on the next upgrade, and the agent tests are the guard.
    implementation("dev.langchain4j:langchain4j-agentic")

    testImplementation("io.micronaut.test:micronaut-test-junit5")
    // Feature 004, R-006: test-only. The readiness test calls /health/readiness over HTTP, which is how
    // the container health check reaches it; a bean-level call would pass even if it were not exposed.
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("org.assertj:assertj-core")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    // Testcontainers 2.x renamed its modules: postgresql -> testcontainers-postgresql.
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("io.micronaut.testresources:micronaut-test-resources-client")
}

dependencies {
    constraints {
        // Restates the Micronaut Platform BOM's LangChain4j version so it is greppable here.
        implementation("dev.langchain4j:langchain4j-core:1.18.0")
        implementation("dev.langchain4j:langchain4j-ollama:1.18.0")
    }
}

application {
    mainClass = "dev.l4jlab.chain.Application"
}

// C2: the annotation processor compiles against the same release the toolchain declares, so
// compile-time wiring and emitted bytecode cannot disagree about the language level.
tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.compilerArgs.addAll(
        listOf(
            "-parameters",
            "-Amicronaut.processing.group=dev.l4jlab.chain",
            // R-001: a fixed file name. The default is title plus version, so a version bump moved the
            // file and broke the frontend's contract scripts, which read it by path.
            "-Amicronaut.openapi.filename=openapi",
            // R-002: fills ${api.version} in @Info, so the version is not written a second time in Java.
            "-Amicronaut.openapi.expand.api.version=${project.version}",
        ),
    )
}

// T006: live model tests are a separate source set with their own task, never a flag.
val liveTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
    runtimeClasspath += output + compileClasspath
}

configurations["liveTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["liveTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

val liveTestTask = tasks.register<Test>("liveTest") {
    description = "Runs the live model tests against a real provider. Requires OLLAMA_API_KEY."
    group = "verification"
    testClassesDirs = liveTest.output.classesDirs
    classpath = liveTest.runtimeClasspath
    useJUnitPlatform()
    // Never part of `check`: selecting these is a deliberate act.
    systemProperty("micronaut.environments", "live")
    // The outcome depends on the provider and credential in the environment, which Gradle does not track, so
    // a cached result could report a pass or skip from another configuration (found in 006, T027).
    outputs.upToDateWhen { false }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    systemProperty("micronaut.environments", "test")
}
