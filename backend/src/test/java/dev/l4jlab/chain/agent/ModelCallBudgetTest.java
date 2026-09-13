package dev.l4jlab.chain.agent;

import dev.l4jlab.chain.domain.ChainRequest;
import dev.l4jlab.chain.domain.RetrievedRecords;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.node.ComputeIndicatorsNode;
import dev.l4jlab.chain.node.PrepareRequestNode;
import dev.l4jlab.chain.node.RetrieveRecordsNode;
import dev.l4jlab.chain.support.ChainRuns;
import dev.l4jlab.chain.support.Datasets;
import dev.l4jlab.chain.support.FakeChatModel;
import dev.l4jlab.chain.support.Validations;
import dev.langchain4j.model.chat.ChatModel;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feature 005, User Story 3: only the summarizing step reaches the model, whatever the orchestration does inside.
 * Counting calls on the fake model is how that claim is proved, as it was before the migration. The timing budget
 * is carried over from the former DeterministicNodeBudgetTest.
 */
class ModelCallBudgetTest {

    private static final Map<String, Object> PROPERTIES = Map.of(
            "datasources.default.enabled", "false",
            "flyway.enabled", "false",
            "l4j.model.provider", "local",
            "l4j.model.base-url", "http://localhost:11434",
            "l4j.model.model-id", "test-model");

    private static ApplicationContext context(String... environments) {
        return ApplicationContext.builder()
                .environments(environments)
                .eagerInitSingletons(false)
                .properties(PROPERTIES)
                .start();
    }

    private static void run(ApplicationContext context, String companyId, String period) {
        ChainRuns.Run run = ChainRuns.run(context, new Selection(companyId, period));
        if (run.thrown() != null) {
            throw run.thrown();
        }
    }

    @Test
    void tenSuccessfulRunsMakeExactlyTenModelCalls() {
        try (ApplicationContext context = context("test")) {
            FakeChatModel model = (FakeChatModel) context.getBean(ChatModel.class);
            int before = model.callCount();

            for (int i = 0; i < 10; i++) {
                run(context, "harbor-foods", "2025-Q3");
            }

            assertThat(model.callCount() - before).isEqualTo(10);
        }
    }

    @Test
    void aRunFailingInPrepareRequestOrRetrieveRecordsMakesNoModelCall() {
        try (ApplicationContext context = context("test")) {
            FakeChatModel model = (FakeChatModel) context.getBean(ChatModel.class);
            int before = model.callCount();

            assertThatThrownBy(() -> run(context, "Not A Valid Id", "2025-Q2")).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> run(context, "northwind-lighting", "1999-Q1")).isInstanceOf(RuntimeException.class);

            assertThat(model.callCount()).isEqualTo(before);
        }
    }

    @Test
    void aRunFailingInComputeIndicatorsMakesNoModelCall() {
        try (ApplicationContext context = context("test", "invalid-boundary")) {
            FakeChatModel model = (FakeChatModel) context.getBean(ChatModel.class);
            int before = model.callCount();

            assertThatThrownBy(() -> run(context, "northwind-lighting", "2025-Q2")).isInstanceOf(RuntimeException.class);

            assertThat(model.callCount()).isEqualTo(before);
        }
    }

    @Test
    void theThreeDeterministicNodesFinishWellInsideTheirBudget() throws Exception {
        // Carried over from DeterministicNodeBudgetTest: under one hundred milliseconds together, so the
        // sixty-second run budget (feature 001, SC-004) belongs effectively to the model call alone.
        long budgetMs = 100;
        PrepareRequestNode prepare = new PrepareRequestNode(Clock.systemUTC(), Validations.boundary());
        RetrieveRecordsNode retrieve = new RetrieveRecordsNode(Datasets.committed(), Validations.boundary());
        ComputeIndicatorsNode compute = new ComputeIndicatorsNode(Validations.boundary());
        // Warm up, so the measurement is of the work rather than of class loading.
        for (int i = 0; i < 50; i++) {
            compute.run(retrieve.run(prepare.run(new Selection("harbor-foods", "2025-Q3"))));
        }

        long start = System.nanoTime();
        ChainRequest request = prepare.run(new Selection("harbor-foods", "2025-Q3"));
        RetrievedRecords records = retrieve.run(request);
        compute.run(records);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertThat(elapsedMs).as("three deterministic nodes, budget %d ms", budgetMs).isLessThan(budgetMs);
    }
}
