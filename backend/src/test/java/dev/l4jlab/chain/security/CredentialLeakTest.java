package dev.l4jlab.chain.security;

import dev.l4jlab.chain.agent.FailureClassifier;
import dev.l4jlab.chain.agent.FinancialChain;
import dev.l4jlab.chain.agent.FinancialChainFactory;
import dev.l4jlab.chain.agent.RunProgressListener;
import dev.l4jlab.chain.agent.RunTraceAssembler;
import dev.l4jlab.chain.agent.Summarizer;
import dev.l4jlab.chain.core.BoundaryValidation;
import dev.l4jlab.chain.core.ChainResult;
import dev.l4jlab.chain.core.ChainRunService;
import dev.l4jlab.chain.core.NodeRecord;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.model.ChatModelFactory;
import dev.l4jlab.chain.model.ModelProperties;
import dev.l4jlab.chain.node.ComputeIndicatorsNode;
import dev.l4jlab.chain.node.PrepareRequestNode;
import dev.l4jlab.chain.node.RetrieveRecordsNode;
import dev.l4jlab.chain.support.ChainRuns;
import dev.l4jlab.chain.support.Datasets;
import dev.l4jlab.chain.support.FakeChatModel;
import dev.l4jlab.chain.support.Validations;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-018 and SC-002. The credential must not appear in any run record, node record, serialized
 * payload, or rendered message. This is the test that would catch a well-meaning future change that
 * starts logging the properties bean or attaching the request headers to the trace.
 */
class CredentialLeakTest {

    private static final String CREDENTIAL = "sk-live-abcdef0123456789-do-not-leak";

    private static ModelProperties cloudProperties() {
        ModelProperties p = new ModelProperties();
        p.setProvider("cloud");
        p.setBaseUrl("https://ollama.com");
        p.setModelId("gpt-oss:120b");
        p.setApiKey(CREDENTIAL);
        p.setTimeoutSeconds(45);
        return p;
    }

    /**
     * The whole chain, assembled exactly as FinancialChainFactory assembles it, with the credential under test in the
     * properties every component reads. Feature 005 replaced the hand-written runner with this sequence; the
     * guarantees checked below did not change.
     */
    private ChainResult runWith(FakeChatModel model) {
        ModelProperties properties = cloudProperties();
        BoundaryValidation validation = Validations.boundary();
        FailureClassifier classifier = new FailureClassifier(properties);
        RunProgressListener progress = new RunProgressListener();
        RunTraceAssembler assembler = new RunTraceAssembler(properties, classifier);
        FinancialChainFactory factory = new FinancialChainFactory();
        Summarizer summarizer = factory.summarizer(model);
        FinancialChain chain = factory.financialChain(
                new PrepareRequestNode(Clock.fixed(Instant.parse("2026-09-12T10:15:30Z"), ZoneOffset.UTC), validation),
                new RetrieveRecordsNode(Datasets.committed(), validation),
                new ComputeIndicatorsNode(validation),
                summarizer,
                progress);
        ChainRunService service = new ChainRunService(
                chain, summarizer, progress, assembler, classifier, validation, null, null, properties, null, null);

        ChainRuns.Run run = ChainRuns.run(chain, summarizer, progress, assembler, new Selection("northwind-lighting", "2025-Q2"));
        return service.toResult(run.trace(), run.indicators(), run.thrown());
    }

    @Test
    void noNodeRecordCarriesTheCredentialOnASuccessfulRun() throws IOException {
        ChainResult result = runWith(new FakeChatModel().respondWith("A fictional summary."));

        for (NodeRecord record : result.nodeRecords()) {
            assertSerializedFormIsClean(record.inputPayload());
            assertSerializedFormIsClean(record.outputPayload());
            assertThat(String.valueOf(record.modelRequestText())).doesNotContain(CREDENTIAL);
            assertThat(String.valueOf(record.modelResponseText())).doesNotContain(CREDENTIAL);
        }
        assertThat(result.summary().text()).doesNotContain(CREDENTIAL);
    }

    @Test
    void aRejectedCredentialIsNotEchoedBackInTheFailureReason() {
        // The realistic leak: a provider's 401 message quoting the Authorization header it received.
        FakeChatModel model =
                new FakeChatModel()
                        .failWith(new RuntimeException(
                                "401 Unauthorized for header Authorization: Bearer " + CREDENTIAL));

        ChainResult result = runWith(model);

        assertThat(result.failureReason()).doesNotContain(CREDENTIAL);
        assertThat(result.failureReason()).contains("<redacted>");
    }

    @Test
    void thePropertiesBeanRedactsTheCredentialWhenRendered() {
        assertThat(cloudProperties().toString()).doesNotContain(CREDENTIAL).contains("<redacted>");
    }

    @Test
    void theBuiltModelDoesNotHoldTheCredentialWhereARendererCanReachIt() {
        // R-012: the Supplier overload, so no header map is a field on the model object.
        assertThat(new ChatModelFactory(cloudProperties()).chatModel().toString())
                .doesNotContain(CREDENTIAL);
    }

    private void assertSerializedFormIsClean(Object payload) throws IOException {
        if (payload == null) {
            return;
        }
        // Serialized exactly as the JSONB column would hold it.
        String json = new String(ObjectMapper.getDefault().writeValueAsBytes(payload));
        assertThat(json).doesNotContain(CREDENTIAL);
    }
}
