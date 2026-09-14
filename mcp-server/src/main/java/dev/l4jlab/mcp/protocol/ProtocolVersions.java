package dev.l4jlab.mcp.protocol;

import java.util.List;

/**
 * The one protocol revision this server speaks (FR-001, FR-004).
 *
 * <p>Deliberately not the SDK's {@code io.modelcontextprotocol.spec.ProtocolVersions}, which tops out
 * at {@code 2025-11-25} and would happily accept versions this server does not implement.
 */
public final class ProtocolVersions {

    public static final String TARGET = "2026-07-28";

    public static final List<String> SUPPORTED = List.of(TARGET);

    /** MCP error codes new in this revision (specification, Error Codes). */
    public static final int HEADER_MISMATCH = -32020;
    public static final int MISSING_REQUIRED_CLIENT_CAPABILITY = -32021;
    public static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;
    public static final int INVALID_PARAMS = -32602;
    public static final int METHOD_NOT_FOUND = -32601;

    private ProtocolVersions() {
    }
}
