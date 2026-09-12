package dev.l4jlab.chain.core;

import dev.l4jlab.chain.domain.IndicatorSet;
import dev.l4jlab.chain.node.ComputeIndicatorsNode;
import dev.l4jlab.chain.node.PrepareRequestNode;
import dev.l4jlab.chain.node.RetrieveRecordsNode;
import dev.l4jlab.chain.node.SummarizeNode;
import dev.l4jlab.chain.domain.RunSummary;
import dev.l4jlab.chain.domain.Selection;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The agent loop, in full. Four nodes, in a fixed order, each receiving only the output of the one
 * before it. There is no framework between these lines and the work: that is the point (Principle
 * I, FR-001, FR-008).
 *
 * <p>Between every step the boundary structure is validated. A node that returns something the
 * contract forbids fails the run here rather than corrupting the next node's input.
 */
@Singleton
public class ChainRunner {

    private final List<ChainNode<?, ?>> nodes;
    private final Validator validator;

    public ChainRunner(
            PrepareRequestNode prepareRequest,
            RetrieveRecordsNode retrieveRecords,
            ComputeIndicatorsNode computeIndicators,
            SummarizeNode summarize,
            Validator validator) {
        // The chain order, stated once, in the order a learner reads it.
        this.nodes = List.of(prepareRequest, retrieveRecords, computeIndicators, summarize);
        this.validator = validator;
    }

    /**
     * Runs the chain. {@code onNodeStart} is called with each node's name before it executes, which
     * is what lets the screen name the node currently running (FR-020).
     */
    @SuppressWarnings("unchecked")
    public ChainResult run(Selection selection, Consumer<String> onNodeStart) {
        List<NodeRecord> records = new ArrayList<>();
        Object carried = selection;
        IndicatorSet indicators = null;
        RunSummary summary = null;

        for (int position = 1; position <= nodes.size(); position++) {
            ChainNode<Object, Object> node = (ChainNode<Object, Object>) nodes.get(position - 1);
            onNodeStart.accept(node.name());

            Object input = carried;
            Instant startedAt = Instant.now();
            long startNanos = System.nanoTime();

            try {
                Object output = node.run(input);
                validateBoundary(node.name(), output);

                long durationMs = elapsedMs(startNanos);
                records.add(recordFor(position, node.name(), input, output, startedAt, durationMs));

                if (output instanceof IndicatorSet set) {
                    indicators = set;
                } else if (output instanceof RunSummary produced) {
                    summary = produced;
                }
                carried = output;

            } catch (ChainTimeoutException e) {
                records.add(failureRecord(position, node.name(), input, startedAt, elapsedMs(startNanos), e.getMessage()));
                return new ChainResult(
                        RunStatus.TIMED_OUT, node.name(), e.getMessage(), indicators, null, records);

            } catch (ChainFailure e) {
                records.add(failureRecord(position, node.name(), input, startedAt, elapsedMs(startNanos), e.getMessage()));
                return new ChainResult(
                        RunStatus.FAILED, node.name(), e.getMessage(), indicators, null, records);

            } catch (RuntimeException e) {
                // Anything unplanned becomes a generic, learner-facing message. A stack trace must
                // never reach the screen (FR-015).
                String reason = node.name() + " failed unexpectedly. See the server log for detail.";
                records.add(failureRecord(position, node.name(), input, startedAt, elapsedMs(startNanos), reason));
                return new ChainResult(RunStatus.FAILED, node.name(), reason, indicators, null, records);
            }
        }

        return new ChainResult(RunStatus.SUCCEEDED, null, null, indicators, summary, records);
    }

    private void validateBoundary(String nodeName, Object output) {
        if (output == null) {
            throw new IllegalStateException(nodeName + " produced no output");
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(output);
        if (!violations.isEmpty()) {
            String detail =
                    violations.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .sorted()
                            .collect(Collectors.joining("; "));
            throw new IllegalStateException(
                    nodeName + " produced an invalid " + output.getClass().getSimpleName() + ": " + detail);
        }
    }

    private NodeRecord recordFor(
            int position, String nodeName, Object input, Object output, Instant startedAt, long durationMs) {

        if (output instanceof RunSummary s) {
            // Only the summarizing node carries a model exchange (FR-010). The request and response
            // text are attached by the node itself and picked up here.
            ModelExchange exchange = ModelExchangeHolder.take();
            return new NodeRecord(
                    position, nodeName, input, output, true, null, startedAt, durationMs,
                    exchange == null ? null : exchange.requestText(),
                    exchange == null ? null : exchange.responseText(),
                    s.inputTokens(), s.outputTokens());
        }
        return new NodeRecord(
                position, nodeName, input, output, true, null, startedAt, durationMs,
                null, null, null, null);
    }

    private NodeRecord failureRecord(
            int position, String nodeName, Object input, Instant startedAt, long durationMs, String reason) {
        ModelExchange exchange = ModelExchangeHolder.take();
        return new NodeRecord(
                position, nodeName, input, null, false, reason, startedAt, durationMs,
                exchange == null ? null : exchange.requestText(),
                exchange == null ? null : exchange.responseText(),
                null, null);
    }

    private static long elapsedMs(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    /** The exact text sent to and returned from the model, for the trace (FR-010). */
    public record ModelExchange(String requestText, String responseText) {}

    /**
     * Carries the model exchange from the summarizing node to the runner without widening the
     * ChainNode interface, which every node would then have to implement. Cleared on read, and each
     * run executes on one thread, so two concurrent runs cannot see each other's exchange.
     */
    public static final class ModelExchangeHolder {
        private static final ThreadLocal<ModelExchange> CURRENT = new ThreadLocal<>();

        private ModelExchangeHolder() {}

        public static void set(ModelExchange exchange) {
            CURRENT.set(exchange);
        }

        public static ModelExchange take() {
            ModelExchange exchange = CURRENT.get();
            CURRENT.remove();
            return exchange;
        }
    }
}
