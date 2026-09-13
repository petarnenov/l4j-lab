package dev.l4jlab.chain.agent;

import dev.l4jlab.chain.core.ChainRunService;
import dev.l4jlab.chain.core.RunStatus;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.support.FakeChatModel;
import dev.l4jlab.chain.support.PostgresTest;
import dev.l4jlab.chain.web.RunController;
import dev.l4jlab.chain.web.dto.NodeView;
import dev.l4jlab.chain.web.dto.RunDetailResponse;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 005, User Story 2: failures still stop the chain and say why.
 *
 * <p>A characterization test. Its assertions are the pre-migration behavior, carried over from the former
 * ChainRunnerTest and SummarizeNodeTest, and checked here through the whole service against a real database, which
 * is where a learner reads them. Written after the service was rewired (T021), so it did not run against the old
 * runner; every expectation is the old runner's documented outcome. The rare branches that the running chain cannot
 * reach are in ChainRunServiceTest.
 */
class FinancialChainFailureTest extends PostgresTest {

    private FakeChatModel model;

    @BeforeEach
    void resetModel() {
        model = (FakeChatModel) context.getBean(ChatModel.class);
        model.failWith(null);
        model.respondWith("A fictional summary of the supplied indicators.");
    }

    @AfterEach
    void restoreModel() {
        model.failWith(null);
        model.respondWith("A fictional summary of the supplied indicators.");
    }

    private RunDetailResponse runToCompletion(String companyId, String period) {
        UUID runId = context.getBean(ChainRunService.class).start(new Selection(companyId, period));
        RunController controller = context.getBean(RunController.class);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            RunDetailResponse detail = controller.detail(runId.toString());
            if (RunStatus.valueOf(detail.status()).isTerminal()) {
                return detail;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Run never reached a terminal state");
    }

    @Test
    void stopsAtTheFirstFailureAndKeepsTheRecordsOfNodesThatCompleted() {
        int callsBefore = model.callCount();

        RunDetailResponse detail = runToCompletion("northwind-lighting", "1999-Q1");

        assertThat(detail.status()).isEqualTo(RunStatus.FAILED.name());
        assertThat(detail.failedNode()).isEqualTo("RetrieveRecords");
        assertThat(detail.failureReason()).contains("1999-Q1");
        assertThat(detail.nodes()).hasSize(2);
        assertThat(detail.nodes().getFirst().succeeded()).isTrue();
        assertThat(detail.nodes().getLast().succeeded()).isFalse();
        assertThat(detail.nodes().getLast().outputPayload()).isNull();
        assertThat(model.callCount()).isEqualTo(callsBefore);
    }

    @Test
    void failsAtTheFirstNodeBeforeAnyLaterNodeRuns() {
        RunDetailResponse detail = runToCompletion("Not A Valid Id", "2025-Q2");

        assertThat(detail.status()).isEqualTo(RunStatus.FAILED.name());
        assertThat(detail.failedNode()).isEqualTo("PrepareRequest");
        assertThat(detail.failureReason()).contains("malformed");
        assertThat(detail.nodes()).hasSize(1);
    }

    @Test
    void carriesTheIndicatorsForwardEvenWhenTheSummarizingNodeFails() {
        model.failWith(new RuntimeException("401 Unauthorized"));

        RunDetailResponse detail = runToCompletion("northwind-lighting", "2025-Q2");

        assertThat(detail.status()).isEqualTo(RunStatus.FAILED.name());
        assertThat(detail.failedNode()).isEqualTo("Summarize");
        assertThat(detail.failureReason()).contains("rejected the credential");
        assertThat(detail.indicators()).hasSize(5);
        assertThat(detail.summary()).isNull();
        assertThat(detail.nodes()).hasSize(4);
    }

    @Test
    void reachesTimedOutRatherThanFailedWhenTheModelExceedsItsLimit() {
        model.failWith(new RuntimeException("timed out", new SocketTimeoutException("Read timed out")));

        RunDetailResponse detail = runToCompletion("northwind-lighting", "2025-Q2");

        assertThat(detail.status()).isEqualTo(RunStatus.TIMED_OUT.name());
        assertThat(detail.failedNode()).isEqualTo("Summarize");
        assertThat(detail.indicators()).hasSize(5);
    }

    @Test
    void wrapsAProviderFailureIntoABoundedReasonWithNoStackTrace() {
        model.failWith(new IllegalStateException("boom ".repeat(200)));

        RunDetailResponse detail = runToCompletion("northwind-lighting", "2025-Q2");

        assertThat(detail.status()).isEqualTo(RunStatus.FAILED.name());
        assertThat(detail.failedNode()).isEqualTo("Summarize");
        assertThat(detail.failureReason())
                .startsWith("The model could not be reached or rejected the request.")
                .doesNotContain("\tat ", ".java:")
                .hasSizeLessThan(500);
    }

    @Test
    void recordsTheExactPromptEvenWhenTheCallFails() {
        model.failWith(new RuntimeException("connection refused"));

        NodeView summarize = runToCompletion("northwind-lighting", "2025-Q2").nodes().get(3);

        assertThat(summarize.succeeded()).isFalse();
        assertThat(summarize.modelRequestText()).contains("revenueGrowth = 0.0600");
        assertThat(summarize.modelResponseText()).isNull();
    }

    @Test
    void anEmptyModelResponseFailsAtSummarizeAndStoresTheBlankReply() {
        model.respondWith("   ");

        RunDetailResponse detail = runToCompletion("northwind-lighting", "2025-Q2");
        List<NodeView> nodes = detail.nodes();

        assertThat(detail.status()).isEqualTo(RunStatus.FAILED.name());
        assertThat(detail.failedNode()).isEqualTo("Summarize");
        assertThat(detail.failureReason()).contains("empty response");
        assertThat(nodes).hasSize(4);
        assertThat(nodes.get(3).modelRequestText()).contains("revenueGrowth");
        // Exactly as before the migration: the blank reply the model sent is stored. It comes from the summarizer's
        // own record of its last exchange (ChatMessagesAccess), which the library keeps even when the guardrail
        // then rejects the reply.
        assertThat(nodes.get(3).modelResponseText()).isEqualTo("   ");
    }
}
