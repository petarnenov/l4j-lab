package dev.l4jlab.chain.core;

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

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Starts runs and drives their state. The write endpoint returns as soon as a PENDING row exists,
 * and the chain executes here, off the request thread, so the learner can watch it advance rather
 * than staring at a blocked request for forty-five seconds (R-004, R-013, FR-020).
 *
 * <p>The submission below is an explicit {@code executor.submit}. An annotation would have been
 * fewer lines and would have made the hand-off invisible at the call site.
 */
@Singleton
public class ChainRunService {

    private static final Logger LOG = LoggerFactory.getLogger(ChainRunService.class);

    private final ChainRunner runner;
    private final ChainRunRepository runs;
    private final NodeExecutionRepository nodeExecutions;
    private final ModelProperties modelProperties;
    private final ExecutorService executor;
    private final Clock clock;

    public ChainRunService(
            ChainRunner runner,
            ChainRunRepository runs,
            NodeExecutionRepository nodeExecutions,
            ModelProperties modelProperties,
            @Named(TaskExecutors.BLOCKING) ExecutorService executor,
            Clock clock) {
        this.runner = runner;
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

            ChainResult result = runner.run(selection, node -> markCurrentNode(runId, node));

            persistNodeRecords(runId, result);
            finish(runId, result);

        } catch (RuntimeException e) {
            // The runner already converts node failures into results. Reaching here means the
            // plumbing itself broke, which the learner still needs to see as a failed run.
            LOG.error("Run failed outside the chain", e);
            finishUnexpected(runId);
        } finally {
            MDC.remove("runId");
            MDC.remove("node");
        }
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
                                                "ChainRunner",
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
