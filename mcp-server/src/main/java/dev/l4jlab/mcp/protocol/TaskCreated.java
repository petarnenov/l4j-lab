package dev.l4jlab.mcp.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A long-running operation has been handed a durable handle (FR-021).
 *
 * <p>Thrown by a tool and converted by {@link JsonRpcResponseSerializer} into a result with
 * {@code resultType: "task"}. Same reason as {@link InputRequired}: the Tasks extension is new, the
 * module predates it, and a tool method cannot otherwise produce a non-standard result shape.
 */
public final class TaskCreated extends RuntimeException {

    /** Outside the JSON-RPC reserved range; consumed by the serializer, never seen by a client. */
    public static final int CODE = 1003;

    private final transient Map<String, Object> task;

    public TaskCreated(String taskId, String status, String statusMessage, String createdAt,
                       String lastUpdatedAt, Long ttlMs, Integer pollIntervalMs) {
        super("Task created");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("taskId", taskId);
        fields.put("status", status);
        if (statusMessage != null) {
            fields.put("statusMessage", statusMessage);
        }
        fields.put("createdAt", createdAt);
        fields.put("lastUpdatedAt", lastUpdatedAt);
        fields.put("ttlMs", ttlMs);
        if (pollIntervalMs != null) {
            fields.put("pollIntervalMs", pollIntervalMs);
        }
        this.task = Map.copyOf(fields);
    }

    public Map<String, Object> task() {
        return task;
    }
}
