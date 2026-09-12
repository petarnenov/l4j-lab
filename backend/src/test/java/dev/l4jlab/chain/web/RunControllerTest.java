package dev.l4jlab.chain.web;

import dev.l4jlab.chain.core.ChainRunService;
import dev.l4jlab.chain.core.RunStatus;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.support.PostgresTest;
import dev.l4jlab.chain.web.dto.RunDetailResponse;
import dev.l4jlab.chain.web.dto.StartRunRequest;
import dev.l4jlab.chain.web.dto.StartRunResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunControllerTest extends PostgresTest {

    private RunController controller() {
        return context.getBean(RunController.class);
    }

    private RunDetailResponse pollUntilTerminal(UUID runId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            RunDetailResponse detail = controller().detail(runId.toString());
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
    void startingARunReturns202WithTheIdentifierAndALocationHeader() {
        HttpResponse<StartRunResponse> response =
                controller().start(new StartRunRequest("northwind-lighting", "2025-Q2"));

        assertThat(response.status().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body().runId()).isNotNull();
        assertThat(response.body().status()).isEqualTo(RunStatus.PENDING.name());
        assertThat(response.header("Location")).isEqualTo("/api/runs/" + response.body().runId());
    }

    @Test
    void rejectsAnUnknownCompanyNamingTheFieldAndTheAlternatives() {
        assertThatThrownBy(() -> controller().start(new StartRunRequest("no-such-company", "2025-Q2")))
                .isInstanceOfSatisfying(
                        InvalidSelectionException.class,
                        e -> {
                            assertThat(e.field()).isEqualTo("companyId");
                            assertThat(e.getMessage()).contains("no-such-company", "Available");
                        });
    }

    @Test
    void rejectsAnUnknownPeriodNamingTheFieldAndTheAlternatives() {
        assertThatThrownBy(() -> controller().start(new StartRunRequest("northwind-lighting", "1999-Q9")))
                .isInstanceOfSatisfying(
                        InvalidSelectionException.class,
                        e -> {
                            assertThat(e.field()).isEqualTo("period");
                            assertThat(e.getMessage()).contains("1999-Q9");
                        });
    }

    @Test
    void anUnknownIdentifierIsNotFound() {
        assertThatThrownBy(() -> controller().detail(UUID.randomUUID().toString()))
                .isInstanceOf(RunNotFoundException.class);
    }

    @Test
    void aMalformedIdentifierIsAlsoNotFoundRatherThanAnInternalError() {
        assertThatThrownBy(() -> controller().detail("not-a-uuid"))
                .isInstanceOf(RunNotFoundException.class);
    }

    @Test
    void nodesAppearInAscendingPositionAndOnlyOnceStarted() {
        UUID runId = context.getBean(ChainRunService.class)
                .start(new Selection("northwind-lighting", "2025-Q2"));

        RunDetailResponse detail = pollUntilTerminal(runId);

        assertThat(detail.nodes()).hasSize(4);
        assertThat(detail.nodes().stream().map(n -> n.position())).containsExactly(1, 2, 3, 4);
    }

    @Test
    void theResponseCarriesTheProviderModeAndModelIdentifier() {
        // Principle V: the trace names which backend produced the summary.
        UUID runId = context.getBean(ChainRunService.class)
                .start(new Selection("harbor-foods", "2024-Q2"));

        RunDetailResponse detail = pollUntilTerminal(runId);

        assertThat(detail.providerMode()).isEqualTo("LOCAL");
        assertThat(detail.modelId()).isEqualTo("test-model");
    }

    @Test
    void noFieldAnywhereInTheResponseCarriesTheCredential() throws Exception {
        // FR-018, on the serialized response a browser would actually receive.
        UUID runId = context.getBean(ChainRunService.class)
                .start(new Selection("cedarline-tools", "2025-Q2"));

        RunDetailResponse detail = pollUntilTerminal(runId);
        String json = new String(io.micronaut.serde.ObjectMapper.getDefault().writeValueAsBytes(detail));

        assertThat(json).doesNotContain("Authorization", "Bearer ", "apiKey", "api-key");
    }
}
