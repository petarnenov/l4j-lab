package dev.l4jlab.chain.agent;

import dev.l4jlab.chain.node.ComputeIndicatorsNode;
import dev.l4jlab.chain.node.PrepareRequestNode;
import dev.l4jlab.chain.node.RetrieveRecordsNode;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * Builds the declared agents and exposes them as Micronaut beans. Each is created here and nowhere else
 * (constitution, Agent framework: one creation path per AI Service).
 *
 * <p>Why the builder rather than {@code @SequenceAgent(subAgents = {...})}: in the annotation form LangChain4j
 * instantiates every step class itself and takes models and listeners from static methods. The steps here are
 * Micronaut beans with injected dependencies (the dataset, the clock, the validator, the configured model), and
 * a static method could only reach them through a service locator. The builder takes the bean instances as they
 * are. The contract stays declared on {@link FinancialChain}, {@link Summarizer}, and each step's {@code @Agent}
 * method. Justified exception 1 in specs/005-langchain4j-declarative-migration/plan.md.
 */
@Factory
public class FinancialChainFactory {

    @Singleton
    public Summarizer summarizer(ChatModel chatModel) {
        return AgenticServices.agentBuilder(Summarizer.class)
                .chatModel(chatModel)
                .outputGuardrails(new NonBlankSummaryGuardrail())
                .build();
    }

    @Singleton
    public FinancialChain financialChain(
            PrepareRequestNode prepareRequest,
            RetrieveRecordsNode retrieveRecords,
            ComputeIndicatorsNode computeIndicators,
            Summarizer summarizer,
            RunProgressListener runProgressListener) {
        return AgenticServices.sequenceBuilder(FinancialChain.class)
                // The order here is the chain's order and the position stored with each step record.
                .subAgents(
                        step("PrepareRequest", "request", prepareRequest),
                        step("RetrieveRecords", "records", retrieveRecords),
                        step("ComputeIndicators", "indicators", computeIndicators),
                        summarizer)
                // FinancialChain extends MonitoredAgent, so LangChain4j adds an AgentMonitor alongside this listener.
                .listener(runProgressListener)
                .outputKey("summaryText")
                .build();
    }

    /**
     * A one-step sequence around a deterministic step, so that step's events reach the run trace listener.
     *
     * <p>Why it exists (research R-005, found in T020): in langchain4j-agentic 1.18.0-beta28 a plain object step
     * placed directly in a sequence never receives the sequence's inherited listener, because its
     * {@code setParent} does not register one, while a workflow agent does register it for its own sub-agents.
     * Wrapping each deterministic step in a sequence of one uses only the public builder and gets the step's
     * events delivered. The wrapper is named {@code step:<name>} so the listener ignores its events and records
     * the inner step's, which carry exactly the step's own input. Remove the wrappers when the library registers
     * inherited listeners for plain object steps.
     */
    private static Object step(String name, String outputKey, Object bean) {
        return AgenticServices.sequenceBuilder().name("step:" + name).outputKey(outputKey).subAgents(bean).build();
    }
}
