package dev.l4jlab.chain.core;

import dev.l4jlab.chain.agent.FailureClassifier;
import dev.l4jlab.chain.agent.FinancialChain;
import dev.l4jlab.chain.agent.RunProgressListener;
import dev.l4jlab.chain.agent.RunTraceAssembler;
import dev.l4jlab.chain.agent.Summarizer;
import dev.langchain4j.agentic.agent.ChatMessagesAccess;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.l4jlab.chain.domain.IndicatorSet;
import dev.l4jlab.chain.domain.RunSummary;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.model.ModelProperties;
import dev.l4jlab.chain.persistence.ChainRunEntity;
import dev.l4jlab.chain.persistence.ChainRunRepository;
import dev.l4jlab.chain.persistence.NodeExecutionEntity;
import dev.l4jlab.chain.persistence.NodeExecutionRepository;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.micronaut.core.annotation.Nullable;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Starts runs and drives their state. The write endpoint returns as soon as a PENDING row exists,
 * and the chain executes here, off the request thread, so the learner can watch it advance rather
 * than staring at a blocked request for forty-five seconds (R-004, R-013, FR-020).
 *
 * <p>The submission below is an explicit {@code executor.submit}. An annotation would have been
 * fewer lines and would have made the hand-off invisible at the call site.
 *
 * <p>The chain itself is the {@link FinancialChain} agentic sequence (feature 005). This class starts it, collects
 * what LangChain4j recorded through {@link RunTraceAssembler}, turns a failure into the outcome a learner reads through
 * {@link FailureClassifier}, and persists the result exactly as before.
 */
@Singleton
public class ChainRunService {

    private static final Logger LOG = LoggerFactory.getLogger(ChainRunService.class);

    private final FinancialChain chain;
    private final Summarizer summarizer;
    private final RunProgressListener progress;
    private final RunTraceAssembler assembler;
    private final FailureClassifier classifier;
    private final BoundaryValidation validation;
    private final ChainRunRepository runs;
    private final NodeExecutionRepository nodeExecutions;
    private final ModelProperties modelProperties;
    private final ExecutorService executor;
    private final Clock clock;

    public ChainRunService(
            FinancialChain chain,
            Summarizer summarizer,
            RunProgressListener progress,
            RunTraceAssembler assembler,
            FailureClassifier classifier,
            BoundaryValidation validation,
            ChainRunRepository runs,
            NodeExecutionRepository nodeExecutions,
            ModelProperties modelProperties,
            @Named(TaskExecutors.BLOCKING) ExecutorService executor,
            Clock clock) {
        this.chain = chain;
        this.summarizer = summarizer;
        this.progress = progress;
        this.assembler = assembler;
        this.classifier = classifier;
        this.validation = validation;
        this.runs = runs;
        this.nodeExecutions = nodeExecutions;
        this.modelProperties = modelProperties;
        this.executor = executor;
        this.clock = clock;
    }

    /** Persists a PENDING run and hands it to the executor. Returns immediately. */
    public UUID start(Selection selection) {
        UUID runId = UUID.randomUUID();
        runs.save(
                new ChainRunEntity(
                        runId,
                        selection.companyId(),
                        selection.period(),
                        RunStatus.PENDING.name(),
                        null,
                        null,
                        null,
                        modelProperties.providerMode().name(),
                        modelProperties.getModelId(),
                        null,
                        clock.instant(),
                        null));

        executor.submit(() -> execute(runId, selection));
        return runId;
    }

    private void execute(UUID runId, Selection selection) {
        MDC.put("runId", runId.toString());
        try {
            transition(runId, RunStatus.RUNNING, null);

            progress.start(runId, node -> markCurrentNode(runId, node));
            RuntimeException thrown = null;
            try {
                chain.run(runId, selection);
            } catch (RuntimeException e) {
                thrown = e;
            }
            ChainResult result = collect(runId, thrown);

            persistNodeRecords(runId, result);
            finish(runId, result);

        } catch (RuntimeException e) {
            // Step failures are already results. Reaching here means the plumbing itself broke,
            // which the learner still needs to see as a failed run.
            LOG.error("Run failed outside the chain", e);
            finishUnexpected(runId);
        } finally {
            MDC.remove("runId");
            MDC.remove("node");
        }
    }

    /**
     * Gathers what LangChain4j recorded about a finished run, then releases the library's per-run state: the
     * registered scope and the summarizer's last exchange. Indicators come from the scope the steps wrote to.
     */
    public ChainResult collect(UUID runId, @Nullable RuntimeException thrown) {
        RunTraceAssembler.RunTrace trace = assembler.assemble(
                runId, chain.agentMonitor(), (ChatMessagesAccess) summarizer, progress.finish(runId));
        AgenticScope scope = chain.getAgenticScope(runId);
        IndicatorSet indicators = scope != null && scope.readState("indicators") instanceof IndicatorSet set ? set : null;
        chain.evictAgenticScope(runId);
        return toResult(trace, indicators, thrown);
    }

    /**
     * What one run produced, from the steps LangChain4j recorded, the indicators the run's scope holds, and the
     * exception the sequence threw, if any.
     * Public so the rare branches, and the credential checks in another package, can be tested without a database.
     */
    public ChainResult toResult(
            RunTraceAssembler.RunTrace trace, @Nullable IndicatorSet indicators, @Nullable RuntimeException thrown) {
        List<NodeRecord> records = new ArrayList<>(trace.records());

        if (thrown != null) {
            FailureClassifier.Outcome outcome = classifier.classify(trace.failedStep(), thrown);
            if (!FailureClassifier.SUMMARIZE.equals(trace.failedStep()) && !isHandled(thrown)) {
                // A broken boundary or an unplanned error in a deterministic step: the learner sees the generic
                // reason, and the detail belongs here. Not logged for Summarize, whose provider errors can quote
                // request headers (FR-018 of feature 001).
                LOG.error("Step {} failed unexpectedly", outcome.failedNode(), thrown);
            }
            return new ChainResult(outcome.status(), outcome.failedNode(), outcome.reason(), indicators, null, records);
        }

        RunSummary summary = records.stream()
                .map(NodeRecord::outputPayload)
                .filter(RunSummary.class::isInstance)
                .map(RunSummary.class::cast)
                .findFirst()
                .orElse(null);
        try {
            // The former runner validated this boundary too. Not reachable through the running chain (research
            // R-006); kept as the same defence.
            validation.requireValid(FailureClassifier.SUMMARIZE, summary);
        } catch (BoundaryViolation violation) {
            LOG.error("Step {} produced an invalid output: {}", FailureClassifier.SUMMARIZE, violation.getMessage());
            String reason = FailureClassifier.SUMMARIZE + " failed unexpectedly. See the server log for detail.";
            records.replaceAll(r -> FailureClassifier.SUMMARIZE.equals(r.nodeName())
                    ? new NodeRecord(r.position(), r.nodeName(), r.inputPayload(), null, false, reason,
                            r.startedAt(), r.durationMs(), r.modelRequestText(), r.modelResponseText(), null, null)
                    : r);
            return new ChainResult(RunStatus.FAILED, FailureClassifier.SUMMARIZE, reason, indicators, null, records);
        }
        return new ChainResult(RunStatus.SUCCEEDED, null, null, indicators, summary, records);
    }

    private static boolean isHandled(Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof ChainFailure) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private void markCurrentNode(UUID runId, String nodeName) {
        MDC.put("node", nodeName);
        LOG.info("Node started");
        runs.findById(runId)
                .ifPresent(run -> runs.update(withCurrentNode(run, nodeName)));
    }

    private void persistNodeRecords(UUID runId, ChainResult result) {
        for (NodeRecord record : result.nodeRecords()) {
            nodeExecutions.save(
                    new NodeExecutionEntity(
                            UUID.randomUUID(),
                            runId,
                            (short) record.position(),
                            record.nodeName(),
                            record.inputPayload(),
                            record.outputPayload(),
                            record.succeeded(),
                            record.failureReason(),
                            record.startedAt(),
                            (int) record.durationMs(),
                            record.modelRequestText(),
                            record.modelResponseText(),
                            record.inputTokens(),
                            record.outputTokens()));
        }
    }

    private void finish(UUID runId, ChainResult result) {
        runs.findById(runId)
                .ifPresent(
                        run ->
                                runs.update(
                                        new ChainRunEntity(
                                                run.id(),
                                                run.companyId(),
                                                run.period(),
                                                result.status().name(),
                                                null,
                                                result.failedNode(),
                                                result.failureReason(),
                                                run.providerMode(),
                                                run.modelId(),
                                                // Denormalised so the history list needs no payload
                                                // read. Written once, never updated.
                                                result.summary() == null ? null : result.summary().text(),
                                                run.startedAt(),
                                                clock.instant())));
        LOG.info("Run finished with status {}", result.status());
    }

    private void finishUnexpected(UUID runId) {
        runs.findById(runId)
                .ifPresent(
                        run ->
                                runs.update(
                                        new ChainRunEntity(
                                                run.id(), run.companyId(), run.period(),
                                                RunStatus.FAILED.name(), null,
                                                FailureClassifier.UNKNOWN_STEP,
                                                "The run stopped unexpectedly before the chain completed.",
                                                run.providerMode(), run.modelId(), null,
                                                run.startedAt(), clock.instant())));
    }

    private void transition(UUID runId, RunStatus status, String currentNode) {
        runs.findById(runId)
                .ifPresent(
                        run ->
                                runs.update(
                                        new ChainRunEntity(
                                                run.id(), run.companyId(), run.period(), status.name(),
                                                currentNode, run.failedNode(), run.failureReason(),
                                                run.providerMode(), run.modelId(), run.summaryText(),
                                                run.startedAt(), run.endedAt())));
    }

    private ChainRunEntity withCurrentNode(ChainRunEntity run, String nodeName) {
        return new ChainRunEntity(
                run.id(), run.companyId(), run.period(), run.status(), nodeName,
                run.failedNode(), run.failureReason(), run.providerMode(), run.modelId(),
                run.summaryText(), run.startedAt(), run.endedAt());
    }
}
