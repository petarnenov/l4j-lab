package dev.l4jlab.mcp.protocol;

/**
 * A tool execution failure — the {@code isError} half of FR-010.
 *
 * <p>Thrown by a tool, never seen on the wire as an exception. {@link McpResultFilter} converts it
 * into a {@code CallToolResult} with {@code isError: true}, because that is what the specification
 * says a business-logic failure must look like: something a model can read and act on, not a
 * protocol error telling it the request was malformed.
 *
 * <p>It exists because {@code micronaut-mcp-server-java-sdk} maps every thrown exception to an
 * {@code McpError}, and so to a JSON-RPC error. There is no path from a tool method to an
 * {@code isError} result in the module — the eighth delta in research.md R-014.
 */
public final class ToolFailure extends RuntimeException {

    /**
     * Outside the JSON-RPC reserved range on purpose. The specification reserves
     * {@code -32768..-32000} and forbids emitting undefined codes from it; application-defined codes
     * belong elsewhere. This one never reaches a client anyway — the filter consumes it.
     */
    public static final int CODE = 1001;

    public ToolFailure(String message) {
        // No cause, ever. A cause carries a stack trace, and SC-006 says none of that reaches a
        // caller. What the server needs for debugging is the audit row, not an exception chain.
        super(message);
    }
}
