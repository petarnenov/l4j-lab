package dev.l4jlab.chain.agent;

import dev.l4jlab.chain.core.NodeRecord;
import dev.l4jlab.chain.domain.IndicatorSet;
import dev.l4jlab.chain.domain.RunSummary;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.support.ChainRuns;
import dev.l4jlab.chain.support.FakeChatModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 005, User Story 1. The chain now runs as a LangChain4j agentic sequence, and a successful run must
 * produce exactly what the hand-written runner produced. Carried over from the former ChainRunnerTest; the
 * failure cases live in FinancialChainFailureTest.
 *
 * <p>The beans come from a Micronaut context with the datasource off, so this stays a unit test: no Docker,
 * no network, no credential. The model is the test FakeChatModel bean.
 */
class FinancialChainTest {

    /** The spellings the node_execution check constraint accepts. Nothing else may be persisted. */
    private static final List<String> CONTRACT_NAMES =
            List.of("PrepareRequest", "RetrieveRecords", "ComputeIndicators", "Summarize");

    private static ApplicationContext context;
    private static FakeChatModel model;

    @BeforeAll
    static void start() {
        context = ApplicationContext.builder()
                .eagerInitSingletons(false)
                .properties(Map.of(
                        "datasources.default.enabled", "false",
                        "flyway.enabled", "false",
                        "l4j.model.provider", "local",
                        "l4j.model.base-url", "http://localhost:11434",
                        "l4j.model.model-id", "test-model"))
                .start();
        model = (FakeChatModel) context.getBean(ChatModel.class);
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void resetModel() {
        model.failWith(null);
        model.respondWith("A fictional summary of the supplied indicators.");
    }

    private static ChainRuns.Run run(String companyId, String period) {
        ChainRuns.Run run = ChainRuns.run(context, new Selection(companyId, period));
        if (run.thrown() != null) {
            throw run.thrown();
        }
        return run;
    }

    @Test
    void runsExactlyFourNodesInTheFixedOrder() {
        List<NodeRecord> records = run("northwind-lighting", "2025-Q2").trace().records();

        assertThat(records).hasSize(4);
        assertThat(records.stream().map(NodeRecord::nodeName)).containsExactlyElementsOf(CONTRACT_NAMES);
        assertThat(records.stream().map(NodeRecord::position)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void everyNodeNameMatchesTheSpellingTheCheckConstraintAllows() {
        assertThat(run("northwind-lighting", "2025-Q2").trace().records().stream().map(NodeRecord::nodeName))
                .allSatisfy(name -> assertThat(CONTRACT_NAMES).contains(name));
    }

    @Test
    void announcesEachNodeBeforeItRunsSoTheScreenCanNameIt() {
        // FR-020 of feature 001.
        assertThat(run("northwind-lighting", "2025-Q2").stepsStarted()).containsExactlyElementsOf(CONTRACT_NAMES);
    }

    @Test
    void recordsInputOutputAndDurationForEveryNode() {
        assertThat(run("northwind-lighting", "2025-Q2").trace().records())
                .allSatisfy(record -> {
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
        List<NodeRecord> records = run("northwind-lighting", "2025-Q2").trace().records();

        assertThat(records.subList(0, 3)).allSatisfy(r -> {
            assertThat(r.modelRequestText()).isNull();
            assertThat(r.modelResponseText()).isNull();
            assertThat(r.inputTokens()).isNull();
            assertThat(r.outputTokens()).isNull();
        });
        NodeRecord summarize = records.get(3);
        assertThat(summarize.modelRequestText()).isEqualTo(records.get(2).outputPayload().toString());
        assertThat(summarize.modelRequestText()).contains("revenueGrowth");
        assertThat(summarize.modelResponseText()).isEqualTo("A fictional summary of the supplied indicators.");
        assertThat(summarize.inputTokens()).isEqualTo(120);
        assertThat(summarize.outputTokens()).isEqualTo(80);
        assertThat(summarize.outputPayload()).isInstanceOf(RunSummary.class);
    }

    @Test
    void theModelRequestTextIsTheUserMessageTheModelReceived() {
        // Principle V as amended: the stored request text comes from the library's record of the request.
        List<NodeRecord> records = run("harbor-foods", "2025-Q3").trace().records();

        String lastUserMessage = model.lastRequest().messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(m -> ((UserMessage) m).singleText())
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(records.get(3).modelRequestText()).isEqualTo(lastUserMessage);
        // And each deterministic step received exactly what the step before it produced.
        assertThat(records.get(1).inputPayload()).isEqualTo(records.get(0).outputPayload());
        assertThat(records.get(2).inputPayload()).isEqualTo(records.get(1).outputPayload());
        assertThat(records.get(3).inputPayload()).isEqualTo(records.get(2).outputPayload());
    }

    @Test
    void returnsTheSummaryTextAndCarriesTheIndicators() {
        ChainRuns.Run run = run("northwind-lighting", "2025-Q2");

        assertThat(run.summary()).isEqualTo("A fictional summary of the supplied indicators.");
        assertThat(run.trace().records().get(2).outputPayload()).isInstanceOf(IndicatorSet.class);
        assertThat(run.trace().failedStep()).isNull();
    }

    @Test
    void producesIdenticalIndicatorsAcrossRepeatedRunsOfTheSameInput() {
        // SC-005 of feature 001.
        Set<Object> outputs = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            outputs.add(run("harbor-foods", "2025-Q3").trace().records().get(2).outputPayload());
        }
        assertThat(outputs).hasSize(1);
    }

    @Test
    void reportsDebtToEquityAsNotApplicableForTheZeroEquityRecord() {
        IndicatorSet indicators =
                (IndicatorSet) run("stonebridge-paper", "2025-Q4").trace().records().get(2).outputPayload();

        assertThat(indicators.indicators())
                .filteredOn(i -> i.name().equals("debtToEquity"))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.isApplicable()).isFalse();
                    assertThat(i.notApplicableReason()).contains("Equity is zero");
                });
    }

    @Test
    void twoConcurrentRunsEachGetOnlyTheirOwnRecords() {
        CompletableFuture<ChainRuns.Run> first = CompletableFuture.supplyAsync(() -> run("northwind-lighting", "2025-Q2"));
        CompletableFuture<ChainRuns.Run> second = CompletableFuture.supplyAsync(() -> run("meridian-textiles", "2024-Q3"));

        for (ChainRuns.Run r : List.of(first.join(), second.join())) {
            assertThat(r.trace().records()).hasSize(4);
        }
        IndicatorSet a = (IndicatorSet) first.join().trace().records().get(2).outputPayload();
        IndicatorSet b = (IndicatorSet) second.join().trace().records().get(2).outputPayload();
        assertThat(a.companyName()).startsWith("Northwind");
        assertThat(b.companyName()).startsWith("Meridian");
    }

    @Test
    void everyRunHasExactlyFourRecordsWithNoDuplicatedStep() {
        List<String> names = new ArrayList<>();
        run("cedarline-tools", "2025-Q1").trace().records().forEach(r -> names.add(r.nodeName()));

        assertThat(names).hasSize(4).doesNotHaveDuplicates();
    }
}
