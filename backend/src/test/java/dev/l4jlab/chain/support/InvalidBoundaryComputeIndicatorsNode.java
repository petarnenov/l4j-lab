package dev.l4jlab.chain.support;

import dev.l4jlab.chain.core.BoundaryValidation;
import dev.l4jlab.chain.core.ChainFailure;
import dev.l4jlab.chain.domain.IndicatorSet;
import dev.l4jlab.chain.domain.RetrievedRecords;
import dev.l4jlab.chain.node.ComputeIndicatorsNode;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * A computing step that breaks its boundary contract: its indicator set has a blank company name. Active only in the
 * {@code invalid-boundary} environment, so a test can drive a broken boundary through the real sequence and check
 * that the learner sees the generic reason rather than the validation detail (feature 005, research R-006).
 */
@Singleton
@Requires(env = "invalid-boundary")
@Replaces(ComputeIndicatorsNode.class)
public class InvalidBoundaryComputeIndicatorsNode extends ComputeIndicatorsNode {

    private final BoundaryValidation validation;

    public InvalidBoundaryComputeIndicatorsNode(BoundaryValidation validation) {
        super(validation);
        this.validation = validation;
    }

    // Method annotations are not inherited, so the step declaration is repeated on the override.
    @Override
    @Agent(name = "ComputeIndicators", outputKey = "indicators", description = "A deliberately broken computing step")
    public IndicatorSet run(@V("records") RetrievedRecords input) throws ChainFailure {
        IndicatorSet valid = super.run(input);
        return validation.requireValid(name(), new IndicatorSet("", valid.period(), valid.indicators()));
    }
}
