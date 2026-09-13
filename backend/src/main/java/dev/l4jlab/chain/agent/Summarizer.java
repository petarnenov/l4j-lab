package dev.l4jlab.chain.agent;

import dev.l4jlab.chain.domain.IndicatorSet;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * The one AI agent in the chain: step 4 of 4, and the only step that reaches a model.
 *
 * <p>Everything about the model exchange is declared here. The system message carries the rules, the user
 * message is the indicator table, and LangChain4j assembles the request, calls the model, and returns the
 * text. The output guardrail that rejects a blank reply and the model it runs against are attached where
 * the agent is built, in {@link FinancialChainFactory}.
 */
public interface Summarizer {

    /**
     * Two rules carry weight: reproduce values verbatim, so the traceability check can match every numeric
     * token against the indicator set; and give no recommendation, because a model asked to summarise
     * financial figures drifts toward advice. Unchanged, character for character, from feature 001.
     */
    String SYSTEM_INSTRUCTION =
            """
            You summarise pre-computed financial indicators for a teaching exercise.

            Rules you must follow:
            1. The companies and figures are fictional. Say nothing that implies otherwise.
            2. Describe only the indicators supplied below. Introduce no other figure.
            3. Reproduce every indicator value exactly as written, digit for digit. Do not round, \
            rescale, or convert to a percentage.
            4. Where an indicator is marked not applicable, say so and give the stated reason. \
            Do not estimate a replacement.
            5. Give no recommendation, rating, forecast, or investment advice.
            6. Write four to six plain sentences for a learner, with no headings and no bullet list.
            """;

    /**
     * {@code {{indicators}}} is rendered with {@link IndicatorSet#toString()}, which is the indicator table.
     * The step name is stored in every run record and must match the database check constraint.
     */
    @Agent(
            name = "Summarize",
            outputKey = "summaryText",
            description = "Summarises pre-computed financial indicators for a learner")
    @SystemMessage(SYSTEM_INSTRUCTION)
    @UserMessage("{{indicators}}")
    String summarize(@V("indicators") IndicatorSet indicators);
}
