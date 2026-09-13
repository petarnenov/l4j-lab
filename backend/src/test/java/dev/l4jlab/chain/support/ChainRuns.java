package dev.l4jlab.chain.support;

import dev.l4jlab.chain.agent.FinancialChain;
import dev.l4jlab.chain.agent.RunProgressListener;
import dev.l4jlab.chain.agent.RunTraceAssembler;
import dev.l4jlab.chain.agent.Summarizer;
import dev.l4jlab.chain.domain.IndicatorSet;
import dev.l4jlab.chain.domain.Selection;
import dev.langchain4j.agentic.agent.ChatMessagesAccess;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs the chain once, without a database, and gathers what LangChain4j recorded, the same way ChainRunService does:
 * the monitor's executions and the summarizer's last exchange through {@link RunTraceAssembler}, the indicators from
 * the run's scope, and the scope evicted afterwards.
 */
public final class ChainRuns {

    private ChainRuns() {}

    public record Run(
            @Nullable String summary,
            @Nullable RuntimeException thrown,
            RunTraceAssembler.RunTrace trace,
            @Nullable IndicatorSet indicators,
            List<String> stepsStarted) {}

    public static Run run(ApplicationContext context, Selection selection) {
        return run(
                context.getBean(FinancialChain.class),
                context.getBean(Summarizer.class),
                context.getBean(RunProgressListener.class),
                context.getBean(RunTraceAssembler.class),
                selection);
    }

    public static Run run(
            FinancialChain chain,
            Summarizer summarizer,
            RunProgressListener progress,
            RunTraceAssembler assembler,
            Selection selection) {
        UUID runId = UUID.randomUUID();
        List<String> started = new CopyOnWriteArrayList<>();
        progress.start(runId, started::add);
        String summary = null;
        RuntimeException thrown = null;
        try {
            summary = chain.run(runId, selection);
        } catch (RuntimeException e) {
            thrown = e;
        }
        RunTraceAssembler.RunTrace trace =
                assembler.assemble(runId, chain.agentMonitor(), (ChatMessagesAccess) summarizer, progress.finish(runId));
        AgenticScope scope = chain.getAgenticScope(runId);
        IndicatorSet indicators = scope != null && scope.readState("indicators") instanceof IndicatorSet set ? set : null;
        chain.evictAgenticScope(runId);
        return new Run(summary, thrown, trace, indicators, List.copyOf(started));
    }
}
