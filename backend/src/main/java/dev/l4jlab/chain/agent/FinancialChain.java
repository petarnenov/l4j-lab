package dev.l4jlab.chain.agent;

import dev.l4jlab.chain.domain.Selection;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;

import java.util.UUID;

/**
 * The chain, as an agentic sequence. Four steps run in order, each reading what the one before it wrote to
 * the shared scope:
 *
 * <pre>
 *   selection ─► PrepareRequest ─► request ─► RetrieveRecords ─► records
 *             ─► ComputeIndicators ─► indicators ─► Summarize ─► summaryText
 * </pre>
 *
 * <p>The first three steps are plain Micronaut beans with an {@code @Agent} method and never see a model. The
 * fourth is the {@link Summarizer}. The sequence is assembled in {@link FinancialChainFactory}.
 *
 * <p>Everything about observing a run comes from LangChain4j (constitution v3.0.0, Principle I and V):
 * <ul>
 *   <li>{@link MonitoredAgent}: the library attaches an {@code AgentMonitor}, which records every step's start and
 *       finish time, inputs, output, token usage, and the first error, per run.</li>
 *   <li>{@link MemoryId}: the run id is the scope's memory id, which is how the monitor and the summarizer's message
 *       history tell concurrent runs apart.</li>
 *   <li>{@link AgenticScopeAccess}: reads what the steps wrote to a run's scope, and evicts the scope when the run is
 *       stored.</li>
 * </ul>
 */
public interface FinancialChain extends MonitoredAgent, AgenticScopeAccess {

    /** Runs the four steps and returns the summary text. Throws when a step fails, and the first failure stops it. */
    String run(@MemoryId UUID runId, @V("selection") Selection selection);
}
