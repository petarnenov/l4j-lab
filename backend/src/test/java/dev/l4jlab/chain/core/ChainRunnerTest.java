package dev.l4jlab.chain.core;

import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.model.ModelProperties;
import dev.l4jlab.chain.node.ComputeIndicatorsNode;
import dev.l4jlab.chain.node.PrepareRequestNode;
import dev.l4jlab.chain.node.RetrieveRecordsNode;
import dev.l4jlab.chain.node.SummarizeNode;
import dev.l4jlab.chain.support.Datasets;
import dev.l4jlab.chain.support.FakeChatModel;
import io.micronaut.context.ApplicationContext;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChainRunnerTest {

    /** The spellings the node_execution check constraint accepts. Nothing else may be persisted. */
    private static final List<String> CONTRACT_NAMES =
            List.of("PrepareRequest", "RetrieveRecords", "ComputeIndicators", "Summarize");

    /**
     * The real Micronaut validator, so the boundary checks under test are the ones that run in
     * production. Built lazily and with the datasource off, so this stays a unit test: no Docker,
     * no network, no credential.
     */
    private static ApplicationContext context;

    private FakeChatModel model;
    private ChainRunner runner;
    private List<String> nodesStarted;

    @BeforeAll
    static void startValidator() {
        context = ApplicationContext.builder()
                .eagerInitSingletons(false)
                .properties(java.util.Map.of(
                        "datasources.default.enabled", "false",
                        "flyway.enabled", "false",
                        "l4j.model.provider", "local",
                        "l4j.model.base-url", "http://localhost:11434",
                        "l4j.model.model-id", "test-model"))
                .start();
    }

    @AfterAll
    static void stopValidator() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void setUp() {
        ChainRunner.ModelExchangeHolder.take();
        model = new FakeChatModel();

        ModelProperties properties = new ModelProperties();
        properties.setProvider("local");
        properties.setBaseUrl("http://localhost:11434");
        properties.setModelId("test-model");
        properties.setTimeoutSeconds(45);

        Validator validator = context.getBean(Validator.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-12T10:15:30Z"), ZoneOffset.UTC);

        runner =
                new ChainRunner(
                        new PrepareRequestNode(clock),
                        new RetrieveRecordsNode(Datasets.committed()),
                        new ComputeIndicatorsNode(),
                        new SummarizeNode(model, properties),
                        validator);

        nodesStarted = new ArrayList<>();
    }

    private ChainResult run(String companyId, String period) {
        return runner.run(new Selection(companyId, period), nodesStarted::add);
    }

    @Test
    void runsExactlyFourNodesInTheFixedOrder() {
        ChainResult result = run("northwind-lighting", "2025-Q2");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(result.nodeRecords()).hasSize(4);
        assertThat(result.nodeRecords().stream().map(NodeRecord::nodeName)).isEqualTo(CONTRACT_NAMES);
        assertThat(result.nodeRecords().stream().map(NodeRecord::position)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void everyNodeNameMatchesTheSpellingTheCheckConstraintAllows() {
        // A class renamed without its name() would break the insert rather than the compile, so it
        // is pinned here as well as in the contract.
        ChainResult result = run("northwind-lighting", "2025-Q2");

        assertThat(result.nodeRecords().stream().map(NodeRecord::nodeName))
                .allSatisfy(name -> assertThat(CONTRACT_NAMES).contains(name));
    }

    @Test
    void announcesEachNodeBeforeItRunsSoTheScreenCanNameIt() {
        run("northwind-lighting", "2025-Q2");
        // FR-020.
        assertThat(nodesStarted).isEqualTo(CONTRACT_NAMES);
    }

    @Test
    void recordsInputOutputAndDurationForEveryNode() {
        ChainResult result = run("northwind-lighting", "2025-Q2");

        assertThat(result.nodeRecords())
                .allSatisfy(
                        record -> {
                            assertThat(record.inputPayload()).isNotNull();
                            assertThat(record.outputPayload()).isNotNull();
                            assertThat(record.succeeded()).isTrue();
                            assertThat(record.failureReason()).isNull();
                            assertThat(record.startedAt()).isNotNull();
                            assertThat(record.durationMs()).isGreaterThanOrEqualTo(0);
                        });
    }

    @Test
    void attachesTheModelExchangeToTheFourthNodeAndNoOther() {
        // FR-010 and the rule that only Summarize talks to a model.
        ChainResult result = run("northwind-lighting", "2025-Q2");

        List<NodeRecord> records = result.nodeRecords();
        assertThat(records.subList(0, 3))
                .allSatisfy(
                        r -> {
                            assertThat(r.modelRequestText()).isNull();
                            assertThat(r.modelResponseText()).isNull();
                            assertThat(r.inputTokens()).isNull();
                            assertThat(r.outputTokens()).isNull();
                        });

        NodeRecord summarize = records.get(3);
        assertThat(summarize.modelRequestText()).contains("revenueGrowth");
        assertThat(summarize.modelResponseText()).isNotBlank();
        assertThat(summarize.inputTokens()).isEqualTo(120);
    }

    @Test
    void callsTheModelExactlyOncePerRun() {
        run("northwind-lighting", "2025-Q2");
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void stopsAtTheFirstFailureAndKeepsTheRecordsOfNodesThatCompleted() {
        // FR-015. The retrieval node fails, so exactly two records survive: the one that succeeded
        // and the one that failed.
        ChainResult result = run("northwind-lighting", "1999-Q1");

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.failedNode()).isEqualTo("RetrieveRecords");
        assertThat(result.failureReason()).contains("1999-Q1");
        assertThat(result.nodeRecords()).hasSize(2);
        assertThat(result.nodeRecords().getFirst().succeeded()).isTrue();
        assertThat(result.nodeRecords().getLast().succeeded()).isFalse();
        assertThat(result.nodeRecords().getLast().outputPayload()).isNull();
        assertThat(model.callCount()).isZero();
    }

    @Test
    void failsAtTheFirstNodeBeforeAnyLaterNodeRuns() {
        // FR-002.
        ChainResult result = run("Not A Valid Id", "2025-Q2");

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.failedNode()).isEqualTo("PrepareRequest");
        assertThat(result.nodeRecords()).hasSize(1);
        assertThat(nodesStarted).containsExactly("PrepareRequest");
    }

    @Test
    void carriesTheIndicatorsForwardEvenWhenTheSummarizingNodeFails() {
        // The edge case that matters most: the first three nodes produced real work, so the learner
        // must still see it.
        model.failWith(new RuntimeException("401 Unauthorized"));

        ChainResult result = run("northwind-lighting", "2025-Q2");

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.failedNode()).isEqualTo("Summarize");
        assertThat(result.indicators()).isNotNull();
        assertThat(result.indicators().indicators()).hasSize(5);
        assertThat(result.summary()).isNull();
        assertThat(result.nodeRecords()).hasSize(4);
    }

    @Test
    void reachesTimedOutRatherThanFailedWhenTheModelExceedsItsLimit() {
        model.failWith(new RuntimeException("timed out", new SocketTimeoutException("Read timed out")));

        ChainResult result = run("northwind-lighting", "2025-Q2");

        assertThat(result.status()).isEqualTo(RunStatus.TIMED_OUT);
        assertThat(result.failedNode()).isEqualTo("Summarize");
        assertThat(result.indicators()).isNotNull();
    }

    @Test
    void wrapsAProviderFailureIntoABoundedReasonWithNoStackTrace() {
        model.failWith(new IllegalStateException("boom ".repeat(200)));

        ChainResult result = run("northwind-lighting", "2025-Q2");

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.failedNode()).isEqualTo("Summarize");
        // The provider's reason is shown, because "Connection refused" is what a learner needs.
        // What must not appear is a stack frame or an unbounded dump (FR-015).
        assertThat(result.failureReason()).doesNotContain("\tat ", ".java:");
        assertThat(result.failureReason()).hasSizeLessThan(500);
    }

    @Test
    void producesIdenticalIndicatorsAcrossRepeatedRunsOfTheSameInput() {
        // SC-005, end to end through the runner rather than only inside the computing node.
        Set<String> renderings = new java.util.HashSet<>();
        for (int i = 0; i < 10; i++) {
            ChainResult result = run("harbor-foods", "2025-Q3");
            renderings.add(
                    result.indicators().indicators().stream()
                            .map(ind -> ind.name() + '=' + (ind.isApplicable() ? ind.value() : "n/a"))
                            .reduce("", (a, b) -> a + '|' + b));
        }
        assertThat(renderings).hasSize(1);
    }

    @Test
    void reportsDebtToEquityAsNotApplicableForTheZeroEquityRecord() {
        // The dataset carries one deliberately, so FR-005 is exercised by real data (R-008).
        ChainResult result = run("stonebridge-paper", "2025-Q4");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(result.indicators().indicators())
                .filteredOn(i -> i.name().equals("debtToEquity"))
                .singleElement()
                .satisfies(
                        i -> {
                            assertThat(i.isApplicable()).isFalse();
                            assertThat(i.notApplicableReason()).contains("Equity is zero");
                        });
    }
}
