package dev.l4jlab.chain;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

/**
 * Entry point.
 *
 * <p>Where to look: {@code node/} holds exactly four files, one per step of the chain.
 * {@code core/ChainRunner} is the loop that walks them. The model is called in exactly one place,
 * {@code node/SummarizeNode}, and reached through exactly one place, {@code model/ChatModelFactory}.
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
