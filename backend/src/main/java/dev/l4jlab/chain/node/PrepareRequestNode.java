package dev.l4jlab.chain.node;

import dev.l4jlab.chain.core.BoundaryValidation;
import dev.l4jlab.chain.core.ChainFailure;
import dev.l4jlab.chain.domain.ChainRequest;
import dev.l4jlab.chain.domain.Selection;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.util.regex.Pattern;

/**
 * Node 1 of 4. Turns the learner's selection into a structured request. Rejects an incomplete or
 * malformed selection here, before any later node runs (FR-002).
 *
 * <p>No model call. This node is pure arithmetic on strings.
 */
@Singleton
public class PrepareRequestNode {

    private static final Pattern COMPANY_ID = Pattern.compile("[a-z0-9-]{3,64}");
    private static final Pattern PERIOD = Pattern.compile("\\d{4}-Q[1-4]");

    private final Clock clock;
    private final BoundaryValidation validation;

    public PrepareRequestNode(Clock clock, BoundaryValidation validation) {
        this.clock = clock;
        this.validation = validation;
    }

    /** The step name stored with every record of this step, and used in its boundary messages. */
    public String name() {
        return "PrepareRequest";
    }

    /** Step 1 of the agentic sequence: reads {@code selection}, writes {@code request} (feature 005). */
    @Agent(name = "PrepareRequest", outputKey = "request",
            description = "Turns the learner's selection into a structured request")
    public ChainRequest run(@V("selection") Selection selection) throws ChainFailure {
        if (selection == null) {
            throw new ChainFailure(name(), "No selection was supplied. Pick a company and a period.");
        }
        String companyId = selection.companyId();
        String period = selection.period();

        if (companyId == null || companyId.isBlank()) {
            throw new ChainFailure(name(), "No company was selected. Pick one from the list.");
        }
        if (period == null || period.isBlank()) {
            throw new ChainFailure(name(), "No reporting period was selected. Pick one from the list.");
        }
        if (!COMPANY_ID.matcher(companyId).matches()) {
            throw new ChainFailure(
                    name(),
                    "The company identifier '" + companyId + "' is malformed. It must be 3 to 64 "
                            + "characters of lower-case letters, digits, and hyphens.");
        }
        if (!PERIOD.matcher(period).matches()) {
            throw new ChainFailure(
                    name(),
                    "The reporting period '" + period + "' is malformed. It must look like 2025-Q3.");
        }

        return validation.requireValid(name(), new ChainRequest(companyId, period, clock.instant()));
    }
}
