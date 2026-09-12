package dev.l4jlab.chain.node;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.l4jlab.chain.core.ChainFailure;
import dev.l4jlab.chain.core.ChainNode;
import dev.l4jlab.chain.core.ChainRunner;
import dev.l4jlab.chain.core.ChainTimeoutException;
import dev.l4jlab.chain.domain.Indicator;
import dev.l4jlab.chain.domain.IndicatorSet;
import dev.l4jlab.chain.domain.RunSummary;
import dev.l4jlab.chain.model.ModelProperties;
import jakarta.inject.Singleton;

import java.util.StringJoiner;

/**
 * Node 4 of 4, and the only node that calls the model (FR-007).
 *
 * <p>If you are looking for where this project talks to a language model, it is the single
 * {@code chatModel.chat(request)} call below. Nothing else in the codebase reaches a provider.
 * Prompt assembly, the call, and result handling are all visible here, in order, which is what
 * Principle I requires and what a declarative AI service would have hidden.
 */
@Singleton
public class SummarizeNode implements ChainNode<IndicatorSet, RunSummary> {

    /**
     * R-009 and SC-003. Two instructions carry weight: reproduce values verbatim, so the
     * traceability test can match every numeric token against the indicator set; and give no
     * recommendation, because a model asked to summarise financial figures drifts toward advice.
     */
    static final String SYSTEM_INSTRUCTION =
            """
            You summarise pre-computed financial indicators for a teaching exercise.

            Rules you must follow:
            1. The companies and figures are fictional. Say nothing that implies otherwise.
            2. Describe only the indicators supplied below. Introduce no other figure.
            3. Reproduce every indicator value exactly as written, digit for digit. Do not round, \
            rescale, or convert to a percentage.
            4. Where an indicator is marked not applicable, say so and give the stated reason. \
            Do not estimate a replacement.
            5. Give no recommendation, rating, forecast, or investment advice.
            6. Write four to six plain sentences for a learner, with no headings and no bullet list.
            """;

    private final ChatModel chatModel;
    private final ModelProperties properties;

    public SummarizeNode(ChatModel chatModel, ModelProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "Summarize";
    }

    @Override
    public RunSummary run(IndicatorSet indicators) throws ChainFailure {
        String prompt = renderPrompt(indicators);

        ChatRequest request =
                ChatRequest.builder()
                        .messages(SystemMessage.from(SYSTEM_INSTRUCTION), UserMessage.from(prompt))
                        .build();

        ChatResponse response;
        try {
            // The one model call site in this project.
            response = chatModel.chat(request);
        } catch (RuntimeException e) {
            ChainRunner.ModelExchangeHolder.set(new ChainRunner.ModelExchange(prompt, null));
            if (isTimeout(e)) {
                throw new ChainTimeoutException(
                        name(),
                        "The model did not answer within " + properties.getTimeoutSeconds()
                                + " seconds. The indicators above were computed successfully; only the "
                                + "summary is missing.");
            }
            if (isRejectedCredential(e)) {
                // FR-015: {"error":"Unauthorized"} on its own gives a learner nothing to act on.
                throw new ChainFailure(
                        name(),
                        "The model provider rejected the credential, so no summary was produced. The "
                                + "indicators above were computed successfully. Check that OLLAMA_API_KEY is "
                                + "valid, and that L4J_PROVIDER is set to cloud when using Ollama Cloud. "
                                + "Provider said: " + safeReason(e),
                        e);
            }
            throw new ChainFailure(
                    name(),
                    "The model could not be reached or rejected the request. The indicators above "
                            + "were computed successfully; only the summary is missing. Reason: "
                            + safeReason(e),
                    e);
        }

        String text = response.aiMessage() == null ? null : response.aiMessage().text();
        ChainRunner.ModelExchangeHolder.set(new ChainRunner.ModelExchange(prompt, text));

        if (text == null || text.isBlank()) {
            throw new ChainFailure(
                    name(),
                    "The model returned an empty response. The indicators above were computed "
                            + "successfully; only the summary is missing.");
        }

        return new RunSummary(
                truncate(text),
                properties.getModelId(),
                properties.providerMode(),
                inputTokens(response),
                outputTokens(response));
    }

    /** Renders the indicator table the model is asked to describe. Deterministic, so the prompt is too. */
    static String renderPrompt(IndicatorSet indicators) {
        StringJoiner lines = new StringJoiner("\n");
        lines.add("Company: " + indicators.companyName());
        lines.add("Reporting period: " + indicators.period());
        lines.add("");
        lines.add("Indicators:");
        for (Indicator indicator : indicators.indicators()) {
            if (indicator.isApplicable()) {
                lines.add("- " + indicator.name() + " = " + indicator.value());
            } else {
                lines.add("- " + indicator.name() + " = not applicable (" + indicator.notApplicableReason() + ")");
            }
        }
        return lines.toString();
    }

    private static String truncate(String text) {
        if (text.length() <= RunSummary.MAX_TEXT_LENGTH) {
            return text;
        }
        int keep = RunSummary.MAX_TEXT_LENGTH - RunSummary.TRUNCATION_MARKER.length();
        return text.substring(0, keep) + RunSummary.TRUNCATION_MARKER;
    }

    private static final java.util.regex.Pattern REJECTED_CREDENTIAL =
            java.util.regex.Pattern.compile("\\b40[13]\\b|unauthori[sz]ed|forbidden", java.util.regex.Pattern.CASE_INSENSITIVE);

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

    /** Longest provider message shown to a learner. Past this it stops being a reason. */
    private static final int MAX_REASON_LENGTH = 200;

    /**
     * The root cause's message, made safe to show.
     *
     * <p>A provider's error message is genuinely useful here: "Connection refused" and "401
     * Unauthorized" are exactly what a learner needs to see in quickstart Scenario 4. But an HTTP
     * client's message can echo the request, headers included, and the Authorization header carries
     * the bearer token. So the credential is stripped before anything is shown, and the result is
     * capped, because an unbounded provider message is a stack trace by another name.
     */
    private String safeReason(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return "the provider gave no reason";
        }
        String redacted = redactCredential(message);
        return redacted.length() <= MAX_REASON_LENGTH
                ? redacted
                : redacted.substring(0, MAX_REASON_LENGTH) + "...";
    }

    private String redactCredential(String message) {
        String cleaned = message;
        if (properties.hasApiKey()) {
            cleaned = cleaned.replace(properties.getApiKey(), "<redacted>");
        }
        // Also catch a token the client formatted itself, which would not match the raw value.
        return cleaned.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*", "$1<redacted>");
    }

    private static Integer inputTokens(ChatResponse response) {
        return response.tokenUsage() == null ? null : response.tokenUsage().inputTokenCount();
    }

    private static Integer outputTokens(ChatResponse response) {
        return response.tokenUsage() == null ? null : response.tokenUsage().outputTokenCount();
    }
}
