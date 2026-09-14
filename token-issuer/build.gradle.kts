plugins {
    id("io.micronaut.application") version "5.0.2"
}

// Feature 007. Development only (FR-025), and enforced as such: every controller here is
// @Requires(env = ...)-gated, so outside development the beans do not exist (research.md R-013).
version = "0.1.0"
group = "dev.l4jlab.issuer"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

micronaut {
    version = "5.1.5"
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(false)
        annotations("dev.l4jlab.issuer.*")
    }
}

dependencies {
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")

    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("io.micronaut:micronaut-management")
    implementation("jakarta.validation:jakarta.validation-api")
    // The JWT library, version managed by the Micronaut Platform BOM. No datasource: this service
    // has no state beyond a committed key pair.
    implementation("com.nimbusds:nimbus-jose-jwt")

    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.yaml:snakeyaml")

    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("org.assertj:assertj-core")
}

application {
    mainClass = "dev.l4jlab.issuer.Application"
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.compilerArgs.addAll(
        listOf(
            "-parameters",
            "-Amicronaut.processing.group=dev.l4jlab.issuer",
        ),
    )
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // Deliberately does NOT pin micronaut.environments. R-013's gating is what EnvironmentGatingTest
    // exercises, and a globally forced `test-capture` would be added to that test's own environment
    // set rather than replaced — the gate would then pass for the wrong reason. Each test declares
    // the environments it needs through @MicronautTest instead.
}
