package dev.l4jlab.chain.node;

import dev.l4jlab.chain.core.ChainFailure;
import dev.l4jlab.chain.core.ChainNode;
import dev.l4jlab.chain.dataset.SampleDatasetLoader;
import dev.l4jlab.chain.domain.ChainRequest;
import dev.l4jlab.chain.domain.FinancialRecord;
import dev.l4jlab.chain.domain.RetrievedRecords;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Node 2 of 4. Looks the request up in the committed sample dataset and carries forward both the
 * selected period and the one before it, which revenue growth needs.
 *
 * <p>An empty result is never a success. When nothing matches, this node fails with a message
 * naming what was missing rather than passing empty data down the chain (FR-003).
 *
 * <p>No model call.
 */
@Singleton
public class RetrieveRecordsNode implements ChainNode<ChainRequest, RetrievedRecords> {

    private final SampleDatasetLoader dataset;

    public RetrieveRecordsNode(SampleDatasetLoader dataset) {
        this.dataset = dataset;
    }

    @Override
    public String name() {
        return "RetrieveRecords";
    }

    @Override
    public RetrievedRecords run(ChainRequest request) throws ChainFailure {
        if (!dataset.hasCompany(request.companyId())) {
            throw new ChainFailure(
                    name(),
                    "No company '" + request.companyId() + "' exists in the sample dataset. "
                            + "Available companies: " + String.join(", ", dataset.companyIds()) + ".");
        }

        Optional<FinancialRecord> current = dataset.find(request.companyId(), request.period());
        if (current.isEmpty()) {
            throw new ChainFailure(
                    name(),
                    "The sample dataset holds no record for company '" + request.companyId()
                            + "' in period '" + request.period() + "'. Periods on file for that "
                            + "company: " + String.join(", ", dataset.periodsOf(request.companyId())) + ".");
        }

        FinancialRecord record = current.get();
        // Null for a company's first period, which is the one case where revenue growth has no
        // defined answer. The computing node reports that as not applicable rather than inventing.
        FinancialRecord prior = dataset.findPrior(request.companyId(), request.period());

        return new RetrievedRecords(
                record.companyId(), record.companyName(), record.period(), record, prior);
    }
}
