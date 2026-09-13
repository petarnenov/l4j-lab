package dev.l4jlab.chain.agent;

import dev.l4jlab.chain.core.NodeRecord;
import dev.l4jlab.chain.domain.RunSummary;
import dev.l4jlab.chain.model.ModelProperties;
import dev.langchain4j.agentic.agent.ChatMessagesAccess;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.MonitoredExecution;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns what LangChain4j recorded about one run into the step records the run stores (constitution Principle V;
 * feature 005, contracts/trace-and-outcomes.md).
 *
 * <p>Sources, all from the library unless marked:
 * <ul>
 *   <li>{@link AgentMonitor}: each step's start and finish time, inputs, output, token usage, and the run's first
 *       error.</li>
 *   <li>The summarizer's {@link ChatMessagesAccess}: the exact request sent to the model and the response it
 *       returned.</li>
 *   <li>{@link RunProgressListener}: when a failed step stopped, which the monitor does not record.</li>
 *   <li>Filled here, because no library source carries them: the request text of a model call that failed before a
 *       response arrived (rendered from the step's input exactly as its template renders it); the summarizing step's
 *       stored {@link RunSummary}; and each step's single input, unwrapped from the invocation's argument map.</li>
 * </ul>
 */
@Singleton
public class RunTraceAssembler {

    /** The scope key each step reads its input from (data-model.md). */
    private static final Map<String, String> INPUT_KEYS = Map.of(
            "PrepareRequest", "selection",
            "RetrieveRecords", "request",
            "ComputeIndicators", "records",
            FailureClassifier.SUMMARIZE, "indicators");

    /** What one run recorded, in step order, and the step that failed first, if any. */
    public record RunTrace(List<NodeRecord> records, @Nullable String failedStep) {}

    private final ModelProperties properties;
    private final FailureClassifier classifier;

    public RunTraceAssembler(ModelProperties properties, FailureClassifier classifier) {
        this.properties = properties;
        this.classifier = classifier;
    }

    /**
     * Reads the run's executions from the monitor and the summarizer's last exchange, then releases the exchange so the
     * summarizer does not hold it after the run is stored.
     */
    public RunTrace assemble(
            UUID runId,
            AgentMonitor monitor,
            ChatMessagesAccess summarizerMessages,
            RunProgressListener.Observations observations) {
        ChatRequest lastRequest = summarizerMessages.lastChatRequest(runId);
        ChatResponse lastResponse = summarizerMessages.lastChatResponse(runId);
        summarizerMessages.removeLastResponseEvent(runId);

        List<NodeRecord> records = new ArrayList<>();
        String failedStep = null;
        for (MonitoredExecution execution : monitor.allExecutionsFor(runId)) {
            if (failedStep == null && execution.hasError() && RunProgressListener.STEPS.contains(execution.error().agentName())) {
                failedStep = execution.error().agentName();
            }
            List<AgentInvocation> steps = new ArrayList<>();
            collectSteps(execution.topLevelInvocations(), steps);
            for (AgentInvocation step : steps) {
                records.add(record(step, execution, observations, lastRequest, lastResponse));
            }
        }
        return new RunTrace(List.copyOf(records), failedStep);
    }

    /** Depth first, in invocation order: the sequence, each step's one-step wrapper, and the steps themselves. */
    private static void collectSteps(AgentInvocation invocation, List<AgentInvocation> steps) {
        if (RunProgressListener.STEPS.contains(invocation.agent().name())) {
            steps.add(invocation);
            return;
        }
        for (AgentInvocation nested : List.copyOf(invocation.nestedInvocations())) {
            collectSteps(nested, steps);
        }
    }

    private NodeRecord record(
            AgentInvocation invocation,
            MonitoredExecution execution,
            RunProgressListener.Observations observations,
            @Nullable ChatRequest lastRequest,
            @Nullable ChatResponse lastResponse) {
        String step = invocation.agent().name();
        int position = RunProgressListener.STEPS.indexOf(step) + 1;
        Object input = invocation.inputs().get(INPUT_KEYS.get(step));
        Instant startedAt = invocation.startTime().atZone(ZoneId.systemDefault()).toInstant();
        boolean summarize = FailureClassifier.SUMMARIZE.equals(step);
        String requestText = summarize ? requestText(lastRequest, input) : null;
        String responseText = summarize && lastResponse != null && lastResponse.aiMessage() != null
                ? lastResponse.aiMessage().text()
                : null;

        if (invocation.done()) {
            long durationMs = invocation.duration().toMillis();
            if (!summarize) {
                return new NodeRecord(position, step, input, invocation.output(), true, null, startedAt, durationMs,
                        null, null, null, null);
            }
            RunSummary summary = RunSummary.from(
                    responseText == null ? String.valueOf(invocation.output()) : responseText,
                    invocation.tokenUsage().orElse(null),
                    properties);
            return new NodeRecord(position, step, input, summary, true, null, startedAt, durationMs,
                    requestText, responseText == null ? String.valueOf(invocation.output()) : responseText,
                    summary.inputTokens(), summary.outputTokens());
        }

        // Not finished: this is the step that failed. The monitor keeps its start and the run's first error.
        Throwable error = execution.hasError() ? execution.error().error() : null;
        String reason = classifier.classify(step, error == null ? new IllegalStateException("unrecorded failure") : error).reason();
        Instant failedAt = observations.failedAt().getOrDefault(step, startedAt);
        long durationMs = Math.max(0L, Duration.between(startedAt, failedAt).toMillis());
        return new NodeRecord(position, step, input, null, false, reason, startedAt, durationMs,
                requestText, responseText, null, null);
    }

    /**
     * The user message the model received, from the summarizer's own record of the request. When the call failed
     * before a response arrived there is no such record, and the text is the step's input rendered exactly as the
     * {@code {{indicators}}} template renders it.
     */
    private static String requestText(@Nullable ChatRequest request, Object input) {
        if (request != null) {
            String text = null;
            for (ChatMessage message : request.messages()) {
                if (message instanceof UserMessage user) {
                    text = user.singleText();
                }
            }
            if (text != null) {
                return text;
            }
        }
        return String.valueOf(input);
    }
}
