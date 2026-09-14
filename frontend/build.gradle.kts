// R-004: npm is the frontend's build tool, and this file does not change that. It exists so the root
// `./gradlew check` also verifies the frontend. Every task below runs one npm command that you could
// type yourself in this directory; Gradle adds only the order and the prerequisite checks.
plugins {
    base
}

// checkVersion reads the backend's version, so the backend's build file must be read first.
evaluationDependsOn(":backend")

// Windows ships npm as a .cmd shim, which Exec cannot start by its bare name.
val npm = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"

// R-005: frontend/.nvmrc is the single declaration of the Node major version. CI reads the same file.
val checkNode = tasks.register("checkNode") {
    description = "Fails with a named reason when Node.js is missing or is not the version in .nvmrc."
    group = "verification"
    val nvmrc = layout.projectDirectory.file(".nvmrc").asFile
    inputs.file(nvmrc)
    doLast {
        val required = nvmrc.readText().trim().removePrefix("v").substringBefore('.')
        val found =
            try {
                val process = ProcessBuilder("node", "--version").redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText().trim()
                if (process.waitFor() != 0) null else output
            } catch (e: java.io.IOException) {
                null
            }
        if (found == null) {
            throw GradleException(
                "Node.js was not found on the PATH. The frontend needs Node.js $required, as declared in " +
                    "frontend/.nvmrc. Install it (for example `nvm install` in frontend/) and run again.",
            )
        }
        val foundMajor = found.removePrefix("v").substringBefore('.')
        if (foundMajor != required) {
            throw GradleException(
                "Node.js $found is on the PATH, but frontend/.nvmrc requires major version $required. " +
                    "Switch versions (for example `nvm use` in frontend/) and run again.",
            )
        }
    }
}

// Installs exactly what package-lock.json records. Up to date while the lock file is unchanged, so a
// second `./gradlew check` does not reinstall.
val npmCi = tasks.register<Exec>("npmCi") {
    description = "Runs `npm ci`."
    dependsOn(checkNode)
    inputs.file("package-lock.json")
    outputs.file("node_modules/.package-lock.json")
    commandLine(npm, "ci")
}

val test = tasks.register<Exec>("test") {
    description = "Runs `npm test`, the Vitest suite."
    group = "verification"
    dependsOn(npmCi)
    commandLine(npm, "test")
}

// FR-003a and SC-008: the second suite, against a running MCP stack. Deliberately NOT wired into
// `check` — for the same reason :mcp-server:topologyTest is not. It needs six containers it will not
// start behind your back, and it says so rather than failing with a connection error.
//
// A distinct task, never a flag someone has to remember to pass: the form the constitution requires
// for a separately selectable suite.
val mcpConsoleTest = tasks.register<Exec>("mcpConsoleTest") {
    description = "Runs the MCP console's live suite against a running stack. Requires `make mcp-up`."
    group = "verification"
    dependsOn(npmCi)
    commandLine(npm, "run", "test:mcp")
    // The outcome depends on a running stack, which Gradle does not track.
    outputs.upToDateWhen { false }
}

// FR-001a: the console must not be carried in the packaged build. This runs a real production build
// and reads its output, because an untested absence is an assumption.
val checkDevOnly = tasks.register<Exec>("checkDevOnly") {
    description = "Runs `npm run check:dev-only`: the MCP console must be absent from `vite build`."
    group = "verification"
    dependsOn(npmCi)
    commandLine(npm, "run", "check:dev-only")
}

val checkApi = tasks.register<Exec>("checkApi") {
    description = "Runs `npm run check:api`: the committed API types must match the backend's description."
    group = "verification"
    // FR-006: the description is written by the backend compile, so it exists before the check reads it.
    dependsOn(npmCi, ":backend:classes")
    commandLine(npm, "run", "check:api")
}

// R-002: the version is declared once, in backend/build.gradle.kts. npm insists on its own copy in
// package.json, so this task holds that copy to the declaration instead of letting the two drift.
val checkVersion = tasks.register("checkVersion") {
    description = "Fails when package.json's version differs from the application version."
    group = "verification"
    val manifest = layout.projectDirectory.file("package.json").asFile
    val expected = project(":backend").version.toString()
    inputs.file(manifest)
    inputs.property("expected", expected)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val json = groovy.json.JsonSlurper().parse(manifest) as Map<String, Any?>
        val found = json["version"]?.toString()
        if (found != expected) {
            throw GradleException(
                "frontend/package.json has version $found, but the application version in " +
                    "backend/build.gradle.kts is $expected. Set package.json's version to $expected.",
            )
        }
    }
}

tasks.named("check") {
    dependsOn(test, checkApi, checkVersion, checkDevOnly)
}
