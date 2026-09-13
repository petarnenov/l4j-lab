package dev.l4jlab.chain.agent;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.scope.AgenticScope;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The two things about a running chain that LangChain4j's {@code AgentMonitor} does not expose, taken from the
 * library's own agent events (constitution, Principle V: hand instrumentation only where the library has none).
 *
 * <ul>
 *   <li><b>Which step is running now</b>, so the screen can name it (feature 001, FR-020). The monitor records
 *       invocations but notifies no one.</li>
 *   <li><b>When a failed step stopped.</b> The monitor never marks a failed invocation finished, so it has a start
 *       time and no duration.</li>
 * </ul>
 *
 * <p>Everything else a step record holds is read from the monitor, the summarizer's message history, and the run's
 * scope by {@link RunTraceAssembler}. LangChain4j catches and logs anything a listener throws, so nothing here can
 * stop a run.
 */
@Singleton
public class RunProgressListener implements AgentListener {

    /** The four step names, in order. Their position here is the position stored with each record. */
    public static final List<String> STEPS =
            List.of("PrepareRequest", "RetrieveRecords", "ComputeIndicators", FailureClassifier.SUMMARIZE);

    /** What this listener observed for one run. */
    public record Observations(Map<String, Instant> failedAt) {}

    private record Progress(Consumer<String> onStepStart, Map<String, Instant> failedAt) {}

    private final Map<UUID, Progress> runs = new ConcurrentHashMap<>();

    /** Begins observing a run. {@code onStepStart} is told each step's name before the step runs. */
    public void start(UUID runId, Consumer<String> onStepStart) {
        runs.put(runId, new Progress(onStepStart, new ConcurrentHashMap<>()));
    }

    /** Stops observing a run and returns what was observed. */
    public Observations finish(UUID runId) {
        Progress progress = runs.remove(runId);
        return new Observations(progress == null ? Map.of() : Map.copyOf(progress.failedAt()));
    }

    /**
     * Sub-agent events reach this listener only if it inherits them. Plain object steps receive inherited listeners
     * only inside a workflow agent, which is why the deterministic steps are wrapped (FinancialChainFactory).
     */
    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        Progress progress = progressFor(request.agentName(), request.agenticScope());
        if (progress != null) {
            progress.onStepStart().accept(request.agentName());
        }
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        Progress progress = progressFor(error.agentName(), error.agenticScope());
        if (progress != null) {
            // The first failure is the step that failed; the same error then passes up through its wrapper and the
            // sequence, which are not steps.
            progress.failedAt().putIfAbsent(error.agentName(), Instant.now());
        }
    }

    private Progress progressFor(String agentName, AgenticScope scope) {
        if (!STEPS.contains(agentName) || scope == null || !(scope.memoryId() instanceof UUID runId)) {
            return null;
        }
        return runs.get(runId);
    }
}
