package dev.l4jlab.mcp.security;

import java.util.List;
import java.util.Set;

/**
 * Who is calling (FR-013), derived per request from the validated token and retained nowhere
 * between requests — which is what statelessness requires.
 *
 * <p>The MCP server does <em>not</em> decide entitlements from these fields. It passes the identity
 * onward in the exchanged token and lets the legacy API decide (FR-014). They are here for the audit
 * record, and to bind cursors and request state to the caller who minted them.
 */
public record Principal(String userId, String firmId, String role, Set<String> advisorIds) {

    public Principal(String userId, String firmId, String role, List<String> advisorIds) {
        this(userId, firmId, role, Set.copyOf(advisorIds));
    }
}
