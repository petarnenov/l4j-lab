package dev.l4jlab.chain.security;

import dev.l4jlab.chain.core.ChainResult;
import dev.l4jlab.chain.core.ChainRunner;
import dev.l4jlab.chain.core.NodeRecord;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.model.ChatModelFactory;
import dev.l4jlab.chain.model.ModelProperties;
import dev.l4jlab.chain.node.ComputeIndicatorsNode;
import dev.l4jlab.chain.node.PrepareRequestNode;
import dev.l4jlab.chain.node.RetrieveRecordsNode;
import dev.l4jlab.chain.node.SummarizeNode;
import dev.l4jlab.chain.support.Datasets;
import dev.l4jlab.chain.support.FakeChatModel;
import io.micronaut.context.ApplicationContext;
import io.micronaut.serde.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-018 and SC-002. The credential must not appear in any run record, node record, serialized
 * payload, or rendered message. This is the test that would catch a well-meaning future change that
 * starts logging the properties bean or attaching the request headers to the trace.
 */
class CredentialLeakTest {

    private static final String CREDENTIAL = "sk-live-abcdef0123456789-do-not-leak";

    private static ApplicationContext context;

    @BeforeAll
    static void start() {
        context = ApplicationContext.builder()
                .eagerInitSingletons(false)
                .properties(Map.of(
                        "datasources.default.enabled", "false",
                        "flyway.enabled", "false",
                        "l4j.model.provider", "cloud",
                        "l4j.model.base-url", "https://ollama.com",
                        "l4j.model.model-id", "gpt-oss:120b",
                        "l4j.model.api-key", "context-only-not-the-credential-under-test"))
                .start();
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
    }

    private static ModelProperties cloudProperties() {
        ModelProperties p = new ModelProperties();
        p.setProvider("cloud");
        p.setBaseUrl("https://ollama.com");
        p.setModelId("gpt-oss:120b");
        p.setApiKey(CREDENTIAL);
        p.setTimeoutSeconds(45);
        return p;
    }

    private ChainResult runWith(FakeChatModel model) {
        ChainRunner runner =
                new ChainRunner(
                        new PrepareRequestNode(Clock.fixed(Instant.parse("2026-09-12T10:15:30Z"), ZoneOffset.UTC)),
                        new RetrieveRecordsNode(Datasets.committed()),
                        new ComputeIndicatorsNode(),
                        new SummarizeNode(model, cloudProperties()),
                        context.getBean(Validator.class));
        return runner.run(new Selection("northwind-lighting", "2025-Q2"), name -> {});
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
