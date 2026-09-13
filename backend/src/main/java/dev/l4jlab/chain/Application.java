package dev.l4jlab.chain;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

/**
 * Entry point.
 *
 * <p>Where to look: {@code agent/FinancialChain} is the chain, a LangChain4j agentic sequence of four steps.
 * {@code node/} holds the three deterministic steps. {@code agent/Summarizer} is the one AI agent, with its
 * instructions declared, and the only step that reaches a model, through {@code model/ChatModelFactory}.
 * {@code agent/FinancialChainFactory} assembles them, and {@code agent/RunTraceAssembler} turns what LangChain4j recorded into each step's stored record.
 */
@OpenAPIDefinition(
        info =
                @Info(
                        title = "financial-agent-chain",
                        // Declared once, in backend/build.gradle.kts, and expanded at compile time.
                        version = "${api.version}",
                        description =
                                "A four-node teaching chain over fictional financial data. The Java "
                                        + "types here are the single source for these shapes; the "
                                        + "frontend generates its types from this description."))
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
