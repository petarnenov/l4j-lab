package dev.l4jlab.chain.core;

import dev.l4jlab.chain.domain.ChainRequest;
import dev.l4jlab.chain.domain.RetrievedRecords;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.node.ComputeIndicatorsNode;
import dev.l4jlab.chain.node.PrepareRequestNode;
import dev.l4jlab.chain.node.RetrieveRecordsNode;
import dev.l4jlab.chain.support.Datasets;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three deterministic nodes must finish together in under one hundred milliseconds, so that the
 * sixty-second run budget (SC-004) belongs effectively to the model call alone.
 */
class DeterministicNodeBudgetTest {

    private static final long BUDGET_MS = 100;

    @Test
    void theThreeDeterministicNodesFinishWellInsideTheirBudget() throws Exception {
        PrepareRequestNode prepare = new PrepareRequestNode(Clock.systemUTC());
        RetrieveRecordsNode retrieve = new RetrieveRecordsNode(Datasets.committed());
        ComputeIndicatorsNode compute = new ComputeIndicatorsNode();

        // Warm up, so the measurement is of the work rather than of class loading.
        for (int i = 0; i < 50; i++) {
            compute.run(retrieve.run(prepare.run(new Selection("harbor-foods", "2025-Q3"))));
        }

        long start = System.nanoTime();
        ChainRequest request = prepare.run(new Selection("harbor-foods", "2025-Q3"));
        RetrievedRecords records = retrieve.run(request);
        compute.run(records);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertThat(elapsedMs)
                .as("three deterministic nodes, budget %d ms", BUDGET_MS)
                .isLessThan(BUDGET_MS);
    }
}
