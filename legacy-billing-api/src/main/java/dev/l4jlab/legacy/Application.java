package dev.l4jlab.legacy;

import io.micronaut.runtime.Micronaut;

/**
 * The legacy billing REST API: the system of record (FR-023).
 *
 * <p>It owns the domain data and it is the <em>only</em> place entitlements are enforced (FR-014).
 * The MCP server does not duplicate that logic; it translates a 403 from here into a short tool
 * error. Keeping the decision in one service is what makes the boundary testable.
 */
public final class Application {

    private Application() {
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
