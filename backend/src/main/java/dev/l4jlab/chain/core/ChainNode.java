package dev.l4jlab.chain.core;

/**
 * One step of the chain. Four implementations exist, one per step, and the runner walks them in a
 * fixed order. Deliberately three lines: the whole point of this project is that a learner can read
 * the agent loop without stepping into a framework (Principle I).
 */
public interface ChainNode<I, O> {

    /**
     * The node's name as it is persisted into {@code node_execution.node_name}. Returns one of
     * exactly four values: PrepareRequest, RetrieveRecords, ComputeIndicators, Summarize. Never the
     * implementing class name; a check constraint in the database rejects anything else.
     */
    String name();

    O run(I input) throws ChainFailure;
}
