package dev.l4jlab.mcp.protocol;

import java.util.Map;

/**
 * The server needs the user to confirm something before it will act (FR-020, research.md R-002).
 *
 * <p>Thrown by a tool and never seen on the wire as an exception: {@link JsonRpcResponseSerializer}
 * turns it into a result with {@code resultType: "input_required"}, the {@code inputRequests} the
 * client must fulfil, and the opaque {@code requestState} it must echo back.
 *
 * <p>It exists for the same reason {@link ToolFailure} does. The module maps every thrown exception
 * to a JSON-RPC error, and offers no path from a tool method to an interim result — Multi Round-Trip
 * Requests are new in this revision and it predates them.
 */
public final class InputRequired extends RuntimeException {

    /**
     * Outside the JSON-RPC reserved range, which the specification forbids emitting undefined codes
     * from. It never reaches a client: the serializer consumes it.
     */
    public static final int CODE = 1002;

    private final transient Map<String, Object> inputRequests;
    private final transient String requestState;

    public InputRequired(Map<String, Object> inputRequests, String requestState) {
        super("Input required");
        this.inputRequests = Map.copyOf(inputRequests);
        this.requestState = requestState;
    }

    public Map<String, Object> inputRequests() {
        return inputRequests;
    }

    public String requestState() {
        return requestState;
    }

    /**
     * Builds the one elicitation this server ever sends: a confirmation of an exact change.
     *
     * <p>The message spells out the change in full — account, direction, size, resulting fee, and
     * effective date — because a confirmation the user cannot check is not a confirmation. A user
     * approving "the adjustment" has approved nothing.
     */
    public static Map<String, Object> confirmation(String message) {
        return Map.of("confirm_adjustment", Map.of(
            "method", "elicitation/create",
            "params", Map.of(
                "mode", "form",
                "message", message,
                "requestedSchema", Map.of(
                    "type", "object",
                    "properties", Map.of("confirmed", Map.of(
                        "type", "boolean",
                        "description", "True to apply this exact change.")),
                    "required", java.util.List.of("confirmed")))));
    }
}
