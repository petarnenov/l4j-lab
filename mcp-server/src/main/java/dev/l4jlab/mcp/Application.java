package dev.l4jlab.mcp;

import io.micronaut.runtime.Micronaut;

/**
 * Feature 007: the MCP billing server.
 *
 * <p>The MCP surface is hosted by {@code micronaut-mcp-server-java-sdk} (FR-029). Tools are
 * declared with {@code @Tool} in {@code dev.l4jlab.mcp.tools}; the four behaviour hints FR-011
 * requires are declarations on those annotations, not code. Only the 2026-07-28 deltas listed in
 * specs/007-mcp-billing-server/research.md R-014 are written by hand, and each one names the
 * upstream issue that would retire it.
 */
public final class Application {

    private Application() {
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
