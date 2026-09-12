package dev.l4jlab.chain.integration;

import dev.l4jlab.chain.core.ChainRunService;
import dev.l4jlab.chain.core.RunStatus;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.support.FakeChatModel;
import dev.l4jlab.chain.support.PostgresTest;
import dev.l4jlab.chain.web.RunController;
import dev.l4jlab.chain.web.dto.NodeView;
import dev.l4jlab.chain.web.dto.RunDetailResponse;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quickstart Scenario 4, the failure a learner is most likely to hit first. The first three nodes
 * produced real indicators, so the run must fail at Summarize while still showing everything the
 * earlier nodes produced (FR-015, SC-006).
 */
class FailedRunTraceTest extends PostgresTest {

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
        throw new AssertionError("Run never reached a terminal state");
    }

    @Test
    void aRunThatFailsAtSummarizeStillShowsTheThreeNodesThatSucceeded() {
        FakeChatModel model = (FakeChatModel) context.getBean(ChatModel.class);
        model.failWith(new RuntimeException("401 Unauthorized"));
        try {
            UUID runId = context.getBean(ChainRunService.class)
                    .start(new Selection("northwind-lighting", "2025-Q2"));

            RunDetailResponse detail = pollUntilTerminal(runId);

            assertThat(detail.status()).isEqualTo(RunStatus.FAILED.name());
            assertThat(detail.failedNode()).isEqualTo("Summarize");
            assertThat(detail.failureReason()).contains("indicators above were computed");
            assertThat(detail.summary()).isNull();

            // The whole point: the earlier work is still visible.
            List<NodeView> nodes = detail.nodes();
            assertThat(nodes).hasSize(4);
            assertThat(nodes.subList(0, 3)).allSatisfy(n -> assertThat(n.succeeded()).isTrue());
            assertThat(nodes.get(3).succeeded()).isFalse();
            assertThat(nodes.get(3).failureReason()).isNotBlank();
            assertThat(nodes.get(3).outputPayload()).isNull();

            // And the indicators the third node computed are still on screen.
            assertThat(detail.indicators()).hasSize(5);
        } finally {
            model.failWith(null);
        }
    }

    @Test
    void aRunThatTimesOutNamesTheNodeSoTheLearnerCanSayWhatFailed() {
        FakeChatModel model = (FakeChatModel) context.getBean(ChatModel.class);
        model.failWith(new RuntimeException("request timed out", new java.net.SocketTimeoutException("Read timed out")));
        try {
            UUID runId = context.getBean(ChainRunService.class)
                    .start(new Selection("harbor-foods", "2025-Q2"));

            RunDetailResponse detail = pollUntilTerminal(runId);

            assertThat(detail.status()).isEqualTo(RunStatus.TIMED_OUT.name());
            // SC-006 needs a named node even on the timeout path, which is why the check constraint
            // was widened to accept it.
            assertThat(detail.failedNode()).isEqualTo("Summarize");
            assertThat(detail.failureReason()).isNotBlank();
            assertThat(detail.indicators()).hasSize(5);
        } finally {
            model.failWith(null);
        }
    }
}
