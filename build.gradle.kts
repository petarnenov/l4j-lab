// The root project. It carries no application logic — every module has its own build file, and this
// one exists so the repository itself can be verified, not just its parts.
//
// The `base` plugin is here for one reason: it gives the root a `check` lifecycle to attach to, which
// is how feature 009's drift check becomes part of ordinary verification rather than a command
// someone has to remember (009 FR-008).
plugins {
    base
}

// Node is the toolchain for build tooling in this repository — see frontend/scripts/. The frontend
// module already owns a task that reads frontend/.nvmrc and fails with a named reason when Node is
// absent or the wrong major version; reusing it beats writing a second copy that can disagree.
val nodePresent = ":frontend:checkNode"

// 009 FR-003a: the checker's own suite. Deterministic, no network, no credential, nothing started.
val specDriftTest by tasks.registering(Exec::class) {
    description = "Runs the spec-drift checker's own tests."
    group = "verification"
    dependsOn(nodePresent)
    // A glob, not a directory: `node --test <dir>` tries to load the directory as a module on this
    // Node version, while `node --test "<glob>"` is expanded by Node itself — which matters because
    // Exec runs no shell, so a shell glob would arrive here as a literal string.
    commandLine("node", "--test", "scripts/spec-drift/*.test.mjs")
}

// 009 FR-008: reads every document in the repository and fails when one of them has stopped being
// true. Not wired into `check` until the feature's baseline exists — see specs/009-spec-drift-check.
tasks.register<Exec>("specDrift") {
    description = "Fails when a specification document says something about the code that is no longer true."
    group = "verification"
    dependsOn(nodePresent)
    commandLine("node", "scripts/spec-drift/check.mjs")
    // Its inputs are every markdown file and every path they name. Gradle cannot track that usefully,
    // and a drift check that reports UP-TO-DATE is a drift check that has stopped working.
    outputs.upToDateWhen { false }
}

tasks.named("check") {
    dependsOn(specDriftTest)
}
