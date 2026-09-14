package dev.l4jlab.mcp.protocol;

/**
 * A tool decided, correctly, not to act — and that is an outcome, not a failure (feature 010, FR-008).
 *
 * <p>Declining a confirmation is the system working. {@code contracts/mcp-protocol.md} has always said
 * so: <em>"A {@code confirmed: false} response is answered with a tool result saying the change was not
 * applied — not an error."</em> The code disagreed with it and threw a {@link ToolFailure}, whose
 * comment read "Declining is an outcome, not an error" one line above producing {@code isError: true}.
 *
 * <p>Thrown and never seen on the wire, exactly as {@link InputRequired} is: the module offers no path
 * from a tool method to a result that is neither a value nor a failure, so the serializer reads the
 * code and builds one.
 *
 * <p>The distinction matters to a model. {@code isError} says "something went wrong, consider
 * retrying"; this says "what you asked for did not happen, and that is the answer". A model that reads
 * a decline as a failure will try again, which is the one thing nobody wants it to do with a fee
 * adjustment.
 */
public final class NothingApplied extends RuntimeException {

    /** Outside the JSON-RPC reserved range, and consumed by the serializer before it reaches anyone. */
    public static final int CODE = -31003;

    public NothingApplied(String message) {
        super(message);
    }
}
