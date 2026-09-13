package dev.l4jlab.chain.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

/**
 * Rejects a blank model reply. A blank summary is not a summary, and the learner needs to be told the model
 * returned nothing rather than shown an empty panel.
 *
 * <p>{@code fatal}, not {@code failure} or {@code retry}: the reply is rejected once, without asking the
 * model again, which is what the summarizing step always did. The failure classifier turns the resulting
 * exception into feature 001's empty-response reason.
 */
public class NonBlankSummaryGuardrail implements OutputGuardrail {

    public static final String MESSAGE = "The model returned an empty response";

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String text = responseFromLLM == null ? null : responseFromLLM.text();
        return text == null || text.isBlank() ? fatal(MESSAGE) : success();
    }
}
