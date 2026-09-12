package dev.l4jlab.chain.web;

import dev.l4jlab.chain.core.RunStatus;
import dev.l4jlab.chain.persistence.ChainRunEntity;
import dev.l4jlab.chain.persistence.ChainRunRepository;
import dev.l4jlab.chain.support.PostgresTest;
import dev.l4jlab.chain.web.dto.RunListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The history list (FR-014, US3). Seeded directly rather than by running chains, so the ordering
 * and paging are tested without depending on how long a run takes.
 */
class RunHistoryControllerTest extends PostgresTest {

    private ChainRunRepository repository() {
        return context.getBean(ChainRunRepository.class);
    }

    private RunController controller() {
        return context.getBean(RunController.class);
    }

    @BeforeEach
    void clear() {
        repository().deleteAll();
    }

    private List<UUID> seed(int count) {
        Instant base = Instant.parse("2026-09-12T10:00:00Z");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            Instant startedAt = base.plus(i, ChronoUnit.MINUTES);
            repository().save(
                    new ChainRunEntity(
                            id, "northwind-lighting", "2025-Q2", RunStatus.SUCCEEDED.name(),
                            null, null, null, "LOCAL", "test-model",
                            "Summary number " + i + ". " + "padding ".repeat(40),
                            startedAt, startedAt.plusSeconds(5)));
            ids.add(id);
        }
        return ids;
    }

    @Test
    void listsRunsNewestFirst() {
        seed(5);

        RunListResponse response = controller().list(50, null);

        assertThat(response.runs()).hasSize(5);
        assertThat(response.runs().stream().map(RunListResponse.RunListEntry::startedAt))
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void everyEntryCarriesCompanyPeriodStatusAndTimestamps() {
        seed(1);

        RunListResponse.RunListEntry entry = controller().list(50, null).runs().getFirst();

        assertThat(entry.companyId()).isEqualTo("northwind-lighting");
        assertThat(entry.companyName()).contains("fictional");
        assertThat(entry.period()).isEqualTo("2025-Q2");
        assertThat(entry.status()).isEqualTo(RunStatus.SUCCEEDED.name());
        assertThat(entry.startedAt()).isNotNull();
        assertThat(entry.endedAt()).isNotNull();
    }

    @Test
    void theSummaryPreviewIsCappedAtOneHundredAndSixtyCharacters() {
        seed(1);

        String preview = controller().list(50, null).runs().getFirst().summaryPreview();

        assertThat(preview).hasSize(RunListResponse.PREVIEW_LENGTH);
    }

    @Test
    void aRunWithNoSummaryHasANullPreviewRatherThanAnEmptyString() {
        Instant startedAt = Instant.parse("2026-09-12T10:00:00Z");
        repository().save(
                new ChainRunEntity(
                        UUID.randomUUID(), "harbor-foods", "2025-Q1", RunStatus.FAILED.name(),
                        null, "Summarize", "The model returned nothing.", "LOCAL", "test-model",
                        null, startedAt, startedAt.plusSeconds(3)));

        assertThat(controller().list(50, null).runs().getFirst().summaryPreview()).isNull();
    }

    @Test
    void defaultsToFiftyAndCapsAtTwoHundred() {
        seed(3);

        assertThat(controller().list(50, null).runs()).hasSize(3);
        assertThat(controller().list(9999, null).runs()).hasSize(3);
        assertThat(controller().list(2, null).runs()).hasSize(2);
    }

    @Test
    void pagesThroughEveryRunWithNoDuplicatesAndNoGaps() {
        seed(12);

        List<UUID> seen = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            RunListResponse response = controller().list(5, cursor);
            response.runs().forEach(r -> seen.add(r.runId()));
            cursor = response.nextCursor();
            if (cursor == null) {
                break;
            }
        }

        assertThat(seen).hasSize(12).doesNotHaveDuplicates();
    }

    @Test
    void theLastPageCarriesNoCursor() {
        seed(3);

        assertThat(controller().list(50, null).nextCursor()).isNull();
    }

    @Test
    void aCursorThisEndpointDidNotIssueIsRejectedRatherThanIgnored() {
        seed(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller().list(50, "not-a-real-cursor"))
                .isInstanceOf(InvalidSelectionException.class);
    }
}
