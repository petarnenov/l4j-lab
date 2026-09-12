package dev.l4jlab.chain.integration;

import dev.l4jlab.chain.core.ChainRunService;
import dev.l4jlab.chain.core.RunStatus;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.support.PostgresTest;
import dev.l4jlab.chain.web.RunController;
import dev.l4jlab.chain.web.dto.NodeView;
import dev.l4jlab.chain.web.dto.RunDetailResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole chain, end to end, against a real database and the fake model. Proves the pieces fit:
 * the run starts asynchronously, advances through four nodes, persists a complete trace, and
 * surfaces on the detail endpoint the way the contract says.
 */
class ChainEndToEndTest extends PostgresTest {

    private RunDetailResponse pollUntilTerminal(UUID runId) {
        RunController controller = context.getBean(RunController.class);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));

        while (Instant.now().isBefore(deadline)) {
            RunDetailResponse detail = controller.detail(runId.toString());
            if (RunStatus.valueOf(detail.status()).isTerminal()) {
                return detail;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("Run " + runId + " never reached a terminal state");
    }

    @Test
    void aRunCompletesAndPersistsAllFourNodeBoundaries() {
        UUID runId = context.getBean(ChainRunService.class)
                .start(new Selection("northwind-lighting", "2025-Q2"));

        RunDetailResponse detail = pollUntilTerminal(runId);

        assertThat(detail.status()).isEqualTo(RunStatus.SUCCEEDED.name());
        assertThat(detail.nodes()).hasSize(4);
        assertThat(detail.nodes().stream().map(NodeView::nodeName))
                .containsExactly("PrepareRequest", "RetrieveRecords", "ComputeIndicators", "Summarize");
        assertThat(detail.endedAt()).isNotNull();
        assertThat(detail.currentNode()).isNull();
    }

    @Test
    void theSummaryAndTheIndicatorsItWasBuiltFromArriveTogether() {
        // FR-012: both on the same screen, from the same response.
        UUID runId = context.getBean(ChainRunService.class)
                .start(new Selection("harbor-foods", "2025-Q3"));

        RunDetailResponse detail = pollUntilTerminal(runId);

        assertThat(detail.summary()).isNotBlank();
        assertThat(detail.indicators()).hasSize(5);
        assertThat(detail.indicators().stream().map(i -> i.name()))
                .containsExactlyInAnyOrder(
                        "revenueGrowth", "grossMargin", "netMargin", "currentRatio", "debtToEquity");
    }

    @Test
    void everyNodeRecordCarriesItsInputOutputAndDuration() {
        UUID runId = context.getBean(ChainRunService.class)
                .start(new Selection("cedarline-tools", "2025-Q1"));

        RunDetailResponse detail = pollUntilTerminal(runId);

        assertThat(detail.nodes())
                .allSatisfy(
                        node -> {
                            assertThat(node.inputPayload()).isNotNull();
                            assertThat(node.outputPayload()).isNotNull();
                            assertThat(node.succeeded()).isTrue();
                            assertThat(node.startedAt()).isNotNull();
                            assertThat(node.durationMs()).isGreaterThanOrEqualTo(0);
                        });
    }

    @Test
    void onlyTheFourthNodeCarriesAModelExchange() {
        // FR-007 and FR-010, checked on the persisted trace rather than only in memory.
        UUID runId = context.getBean(ChainRunService.class)
                .start(new Selection("altura-ceramics", "2025-Q2"));

        List<NodeView> nodes = pollUntilTerminal(runId).nodes();

        assertThat(nodes.subList(0, 3))
                .allSatisfy(
                        node -> {
                            assertThat(node.modelRequestText()).isNull();
                            assertThat(node.modelResponseText()).isNull();
                            assertThat(node.inputTokens()).isNull();
                            assertThat(node.outputTokens()).isNull();
                        });

        NodeView summarize = nodes.get(3);
        assertThat(summarize.modelRequestText()).contains("revenueGrowth");
        assertThat(summarize.modelResponseText()).isNotBlank();
    }

    @Test
    void theZeroEquityCompanySucceedsWithDebtToEquityMarkedNotApplicable() {
        // Quickstart Scenario 5, against real committed data.
        UUID runId = context.getBean(ChainRunService.class)
                .start(new Selection("stonebridge-paper", "2025-Q4"));

        RunDetailResponse detail = pollUntilTerminal(runId);

        assertThat(detail.status()).isEqualTo(RunStatus.SUCCEEDED.name());
        assertThat(detail.indicators())
                .filteredOn(i -> i.name().equals("debtToEquity"))
                .singleElement()
                .satisfies(
                        i -> {
                            assertThat(i.value()).isNull();
                            assertThat(i.notApplicableReason()).contains("Equity is zero");
                        });
    }

    @Test
    void twoConcurrentRunsCompleteIndependentlyWithoutOverwritingEachOther() {
        // The edge case: neither run may see or clobber the other's records.
        ChainRunService service = context.getBean(ChainRunService.class);

        CompletableFuture<UUID> first =
                CompletableFuture.supplyAsync(() -> service.start(new Selection("northwind-lighting", "2025-Q2")));
        CompletableFuture<UUID> second =
                CompletableFuture.supplyAsync(() -> service.start(new Selection("meridian-textiles", "2024-Q3")));

        RunDetailResponse a = pollUntilTerminal(first.join());
        RunDetailResponse b = pollUntilTerminal(second.join());

        assertThat(a.runId()).isNotEqualTo(b.runId());
        assertThat(a.companyId()).isEqualTo("northwind-lighting");
        assertThat(b.companyId()).isEqualTo("meridian-textiles");
        assertThat(a.nodes()).hasSize(4);
        assertThat(b.nodes()).hasSize(4);
    }

    @Test
    void repeatingTheSameRunProducesIdenticalIndicators() {
        // SC-005, through persistence, which is where a rounding change would actually show up.
        ChainRunService service = context.getBean(ChainRunService.class);

        RunDetailResponse first = pollUntilTerminal(service.start(new Selection("harbor-foods", "2025-Q2")));
        RunDetailResponse second = pollUntilTerminal(service.start(new Selection("harbor-foods", "2025-Q2")));

        assertThat(first.indicators()).isEqualTo(second.indicators());
    }

    @Test
    void indicatorValuesKeepScaleFourThroughTheJsonbRoundTrip() {
        // Regression. Serialized as a JSON number, 0.3400 comes back from the JSONB column as 0.34:
        // numerically equal, not byte-identical, which is what SC-005 actually compares. Caught by
        // running the app, not by any unit test, because every test built the value in memory.
        UUID runId = context.getBean(ChainRunService.class)
                .start(new Selection("stonebridge-paper", "2025-Q4"));

        RunDetailResponse detail = pollUntilTerminal(runId);

        assertThat(detail.indicators())
                .filteredOn(i -> i.value() != null)
                .allSatisfy(i -> assertThat(i.value()).as(i.name()).matches("^-?\\d+\\.\\d{4}$"));

        // And the same bytes in the stored node payload a learner inspects in the detail view.
        String payload = String.valueOf(detail.nodes().get(2).outputPayload());
        java.util.regex.Matcher values =
                java.util.regex.Pattern.compile("value=(-?\\d+(?:\\.\\d+)?)").matcher(payload);
        int found = 0;
        while (values.find()) {
            found++;
            assertThat(values.group(1)).as("stored value").matches("^-?\\d+\\.\\d{4}$");
        }
        assertThat(found).as("applicable indicators in the stored payload").isEqualTo(4);
    }

    @Test
    void anUnknownRunIdentifierIsNotFound() {
        RunController controller = context.getBean(RunController.class);
        assertThat(
                        org.assertj.core.api.Assertions.catchThrowable(
                                () -> controller.detail(UUID.randomUUID().toString())))
                .isInstanceOf(dev.l4jlab.chain.web.RunNotFoundException.class);
    }
}
