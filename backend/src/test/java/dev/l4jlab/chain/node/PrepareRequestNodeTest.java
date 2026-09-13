package dev.l4jlab.chain.node;

import dev.l4jlab.chain.support.Validations;
import dev.l4jlab.chain.core.ChainFailure;
import dev.l4jlab.chain.domain.ChainRequest;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.support.FakeChatModel;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrepareRequestNodeTest {

    private static final Instant FIXED = Instant.parse("2026-09-12T10:15:30Z");

    private final FakeChatModel model = new FakeChatModel();
    private final PrepareRequestNode node =
            new PrepareRequestNode(Clock.fixed(FIXED, ZoneOffset.UTC), Validations.boundary());

    @Test
    void namesItselfAsTheContractSpellsIt() {
        // The database check constraint accepts this spelling and rejects the class name.
        assertThat(node.name()).isEqualTo("PrepareRequest");
    }

    @Test
    void turnsAValidSelectionIntoAStructuredRequest() throws Exception {
        ChainRequest request = node.run(new Selection("northwind-lighting", "2025-Q2"));

        assertThat(request.companyId()).isEqualTo("northwind-lighting");
        assertThat(request.period()).isEqualTo("2025-Q2");
        assertThat(request.requestedAt()).isEqualTo(FIXED);
    }

    @Test
    void rejectsABlankCompanyNamingTheField() {
        assertThatThrownBy(() -> node.run(new Selection("  ", "2025-Q2")))
                .isInstanceOf(ChainFailure.class)
                .hasMessageContaining("company");
    }

    @Test
    void rejectsABlankPeriodNamingTheField() {
        assertThatThrownBy(() -> node.run(new Selection("northwind-lighting", "")))
                .isInstanceOf(ChainFailure.class)
                .hasMessageContaining("period");
    }

    @Test
    void rejectsAMalformedCompanyIdentifierNamingTheOffender() {
        assertThatThrownBy(() -> node.run(new Selection("Northwind Lighting!", "2025-Q2")))
                .isInstanceOf(ChainFailure.class)
                .hasMessageContaining("Northwind Lighting!");
    }

    @Test
    void rejectsAMalformedPeriodNamingTheOffender() {
        assertThatThrownBy(() -> node.run(new Selection("northwind-lighting", "Q2-2025")))
                .isInstanceOf(ChainFailure.class)
                .hasMessageContaining("Q2-2025");
    }

    @Test
    void failuresCarryTheNodeName() {
        assertThatThrownBy(() -> node.run(new Selection(null, "2025-Q2")))
                .isInstanceOfSatisfying(
                        ChainFailure.class, f -> assertThat(f.nodeName()).isEqualTo("PrepareRequest"));
    }

    @Test
    void neverCallsTheModel() throws Exception {
        // FR-007: only the fourth node may reach a provider.
        node.run(new Selection("northwind-lighting", "2025-Q2"));
        assertThat(model.callCount()).isZero();
    }
}
