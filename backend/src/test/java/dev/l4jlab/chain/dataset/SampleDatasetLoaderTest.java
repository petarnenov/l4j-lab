package dev.l4jlab.chain.dataset;

import dev.l4jlab.chain.support.Datasets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SampleDatasetLoaderTest {

    private final SampleDatasetLoader dataset = Datasets.committed();

    @Test
    void loadsEveryCommittedRecord() {
        // R-008: six fictional companies across eight consecutive quarters.
        assertThat(dataset.companyIds()).hasSize(6);
        assertThat(dataset.size()).isEqualTo(48);
    }

    @Test
    void everyCompanyHasEightContiguousQuarters() {
        dataset.companyIds()
                .forEach(id -> assertThat(dataset.periodsOf(id)).as(id).hasSize(8).isSorted());
    }

    @Test
    void findsARecordByCompanyAndPeriod() {
        assertThat(dataset.find("northwind-lighting", "2025-Q2")).isPresent();
        assertThat(dataset.find("northwind-lighting", "1999-Q1")).isEmpty();
        assertThat(dataset.find("no-such-company", "2025-Q2")).isEmpty();
    }

    @Test
    void resolvesThePriorPeriodIncludingAcrossAYearBoundary() {
        assertThat(dataset.findPrior("northwind-lighting", "2025-Q1")).isNotNull();
        assertThat(dataset.findPrior("northwind-lighting", "2025-Q1").period()).isEqualTo("2024-Q4");
    }

    @Test
    void hasNoPriorPeriodForACompanysFirstQuarter() {
        // The only case where revenue growth legitimately has no answer.
        assertThat(dataset.findPrior("northwind-lighting", "2024-Q1")).isNull();
    }

    @Test
    void carriesAtLeastOneZeroEquityRecordSoTheNotApplicablePathIsRealData() {
        // R-008 asks for this deliberately, so FR-005 is exercised by the dataset and not only by
        // a hand-built unit test.
        assertThat(dataset.find("stonebridge-paper", "2025-Q4"))
                .isPresent()
                .get()
                .satisfies(r -> assertThat(r.equity()).isEqualByComparingTo("0"));
    }

    @Test
    void everyCompanyIsLabelledFictional() {
        dataset.companyIds()
                .forEach(id -> assertThat(dataset.companyName(id)).as(id).contains("fictional"));
    }

    @Test
    void computesTheNextAndPreviousPeriodAcrossYearBoundaries() {
        assertThat(SampleDatasetLoader.nextPeriod("2024-Q4")).isEqualTo("2025-Q1");
        assertThat(SampleDatasetLoader.nextPeriod("2025-Q1")).isEqualTo("2025-Q2");
        assertThat(SampleDatasetLoader.previousPeriod("2025-Q1")).isEqualTo("2024-Q4");
        assertThat(SampleDatasetLoader.previousPeriod("2025-Q3")).isEqualTo("2025-Q2");
    }

    @Test
    void theLoaderIsEagerSoADatasetProblemFailsTheBoot() {
        // @Context, not @Singleton: a duplicate or a gap must stop startup with a named message,
        // not surface as a confusing retrieval failure on some later run.
        assertThat(SampleDatasetLoader.class.isAnnotationPresent(io.micronaut.context.annotation.Context.class))
                .isTrue();
    }
}
