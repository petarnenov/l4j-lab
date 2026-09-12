package dev.l4jlab.chain.node;

import dev.l4jlab.chain.core.ChainFailure;
import dev.l4jlab.chain.dataset.SampleDatasetLoader;
import dev.l4jlab.chain.domain.ChainRequest;
import dev.l4jlab.chain.domain.RetrievedRecords;
import dev.l4jlab.chain.support.Datasets;
import dev.l4jlab.chain.support.FakeChatModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrieveRecordsNodeTest {

    private final SampleDatasetLoader dataset = Datasets.committed();
    private final FakeChatModel model = new FakeChatModel();

    private RetrieveRecordsNode node() {
        return new RetrieveRecordsNode(dataset);
    }

    private static ChainRequest request(String companyId, String period) {
        return new ChainRequest(companyId, period, Instant.parse("2026-09-12T10:15:30Z"));
    }

    @Test
    void namesItselfAsTheContractSpellsIt() {
        assertThat(node().name()).isEqualTo("RetrieveRecords");
    }

    @Test
    void returnsTheCurrentRecordAndItsPredecessor() throws Exception {
        RetrievedRecords records = node().run(request("northwind-lighting", "2025-Q2"));

        assertThat(records.companyId()).isEqualTo("northwind-lighting");
        assertThat(records.companyName()).contains("fictional");
        assertThat(records.period()).isEqualTo("2025-Q2");
        assertThat(records.current().period()).isEqualTo("2025-Q2");
        assertThat(records.prior()).isNotNull();
        assertThat(records.prior().period()).isEqualTo("2025-Q1");
    }

    @Test
    void returnsANullPriorForACompanysFirstPeriod() throws Exception {
        RetrievedRecords records = node().run(request("northwind-lighting", "2024-Q1"));

        assertThat(records.current()).isNotNull();
        assertThat(records.prior()).isNull();
    }

    @Test
    void failsNamingTheMissingCompanyRatherThanPassingEmptyData() {
        // FR-003: an empty result is never a success.
        assertThatThrownBy(() -> node().run(request("no-such-company", "2025-Q2")))
                .isInstanceOf(ChainFailure.class)
                .hasMessageContaining("no-such-company")
                .hasMessageContaining("Available companies");
    }

    @Test
    void failsNamingTheMissingPeriodAndListingWhatIsOnFile() {
        assertThatThrownBy(() -> node().run(request("northwind-lighting", "1999-Q1")))
                .isInstanceOf(ChainFailure.class)
                .hasMessageContaining("1999-Q1")
                .hasMessageContaining("2024-Q1");
    }

    @Test
    void failuresCarryTheNodeName() {
        assertThatThrownBy(() -> node().run(request("no-such-company", "2025-Q2")))
                .isInstanceOfSatisfying(
                        ChainFailure.class, f -> assertThat(f.nodeName()).isEqualTo("RetrieveRecords"));
    }

    @Test
    void neverCallsTheModel() throws Exception {
        node().run(request("northwind-lighting", "2025-Q2"));
        assertThat(model.callCount()).isZero();
    }
}
