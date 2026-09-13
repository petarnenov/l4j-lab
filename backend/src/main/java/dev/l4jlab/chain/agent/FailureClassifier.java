package dev.l4jlab.chain.agent;

import dev.l4jlab.chain.core.ChainFailure;
import dev.l4jlab.chain.core.ChainTimeoutException;
import dev.l4jlab.chain.core.RunStatus;
import dev.l4jlab.chain.model.ModelProperties;
import dev.langchain4j.guardrail.OutputGuardrailException;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.regex.Pattern;

/**
 * Turns whatever stopped a run into what the learner reads: a status, the step to blame, and a reason.
 *
 * <p>The orchestration of the chain changed in feature 005; these outcomes did not. Every reason here is
 * the wording feature 001 established, and the rules for when each applies are in
 * specs/005-langchain4j-declarative-migration/contracts/trace-and-outcomes.md. The timeout, credential,
 * and "could not be reached" reasons apply only to the summarizing step, because only its model call
 * ever produced them.
 */
@Singleton
public class FailureClassifier {

    /** The step that calls the model. */
    public static final String SUMMARIZE = "Summarize";

    /**
     * Stored as failed_node when a run stops before any step records a failure. It names the class that
     * used to own this case; the class is gone, but the value is stored data and is kept unchanged.
     */
    public static final String UNKNOWN_STEP = "ChainRunner";

    public record Outcome(RunStatus status, String failedNode, String reason) {}

    /** Longest provider message shown to a learner. Past this it stops being a reason. */
    private static final int MAX_REASON_LENGTH = 200;

    private static final Pattern REJECTED_CREDENTIAL =
            Pattern.compile("\\b40[13]\\b|unauthori[sz]ed|forbidden", Pattern.CASE_INSENSITIVE);

    private final ModelProperties properties;

    public FailureClassifier(ModelProperties properties) {
        this.properties = properties;
    }

    public Outcome classify(@Nullable String failedStep, Throwable thrown) {
        if (failedStep == null) {
            return new Outcome(RunStatus.FAILED, UNKNOWN_STEP, "The run stopped unexpectedly before the chain completed.");
        }

        ChainFailure handled = find(thrown, ChainFailure.class);
        if (handled != null) {
            RunStatus status = handled instanceof ChainTimeoutException ? RunStatus.TIMED_OUT : RunStatus.FAILED;
            return new Outcome(status, failedStep, handled.getMessage());
        }

        if (!SUMMARIZE.equals(failedStep)) {
            // Anything unplanned in a deterministic step, including a broken boundary, gets the generic
            // reason. A stack trace or a validation detail must never reach the screen (FR-015).
            return new Outcome(RunStatus.FAILED, failedStep, failedStep + " failed unexpectedly. See the server log for detail.");
        }

        if (find(thrown, OutputGuardrailException.class) != null) {
            return new Outcome(
                    RunStatus.FAILED,
                    failedStep,
                    "The model returned an empty response. The indicators above were computed "
                            + "successfully; only the summary is missing.");
        }
        if (isTimeout(thrown)) {
            return new Outcome(
                    RunStatus.TIMED_OUT,
                    failedStep,
                    "The model did not answer within " + properties.getTimeoutSeconds()
                            + " seconds. The indicators above were computed successfully; only the "
                            + "summary is missing.");
        }
        if (isRejectedCredential(thrown)) {
            // FR-015: {"error":"Unauthorized"} on its own gives a learner nothing to act on.
            return new Outcome(
                    RunStatus.FAILED,
                    failedStep,
                    "The model provider rejected the credential, so no summary was produced. The "
                            + "indicators above were computed successfully. Check that OLLAMA_API_KEY is "
                            + "valid, and that L4J_PROVIDER is set to cloud when using Ollama Cloud. "
                            + "Provider said: " + safeReason(thrown));
        }
        return new Outcome(
                RunStatus.FAILED,
                failedStep,
                "The model could not be reached or rejected the request. The indicators above "
                        + "were computed successfully; only the summary is missing. Reason: "
                        + safeReason(thrown));
    }

    private static <T extends Throwable> T find(Throwable thrown, Class<T> type) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }

    private static boolean isRejectedCredential(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && REJECTED_CREDENTIAL.matcher(t.getMessage()).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String name = t.getClass().getSimpleName().toLowerCase();
            String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
            if (name.contains("timeout") || message.contains("timed out") || message.contains("timeout")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The root cause's message, made safe to show.
     *
     * <p>A provider's error message is genuinely useful: "Connection refused" and "401 Unauthorized" are
     * exactly what a learner needs to see. But an HTTP client's message can echo the request, headers
     * included, and the Authorization header carries the bearer token. So the credential is stripped
     * before anything is shown, and the result is capped, because an unbounded provider message is a stack
     * trace by another name.
     */
    private String safeReason(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return "the provider gave no reason";
        }
        String redacted = redactCredential(message);
        return redacted.length() <= MAX_REASON_LENGTH ? redacted : redacted.substring(0, MAX_REASON_LENGTH) + "...";
    }

    private String redactCredential(String message) {
        String cleaned = message;
        if (properties.hasApiKey()) {
            cleaned = cleaned.replace(properties.getApiKey(), "<redacted>");
        }
        // Also catch a token the client formatted itself, which would not match the raw value.
        return cleaned.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*", "$1<redacted>");
    }
}
