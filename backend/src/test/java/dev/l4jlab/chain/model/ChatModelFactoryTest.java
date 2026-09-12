package dev.l4jlab.chain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatModelFactoryTest {

    private static final String CREDENTIAL = "sk-test-do-not-leak-0123456789";

    private static ModelProperties properties(String provider, String baseUrl, String apiKey) {
        ModelProperties p = new ModelProperties();
        p.setProvider(provider);
        p.setBaseUrl(baseUrl);
        p.setModelId("gpt-oss:120b");
        p.setApiKey(apiKey);
        p.setTimeoutSeconds(45);
        return p;
    }

    @Test
    void failsAtStartupNamingTheAbsentVariableInCloudMode() {
        // FR-016: the message must name the variable, and this must happen at construction, not at
        // the first model call forty-five seconds into a run.
        assertThatThrownBy(() -> new ChatModelFactory(properties("cloud", "https://ollama.com", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OLLAMA_API_KEY")
                .hasMessageContaining("L4J_PROVIDER=local");
    }

    @Test
    void localModeNeedsNoCredential() {
        assertThatCode(() -> new ChatModelFactory(properties("local", "http://localhost:11434", "")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsABaseUrlCarryingAPathSuffix() {
        // R-001: the client builds its own paths starting with a slash, so a path segment here is
        // silently dropped and requests land somewhere unintended.
        assertThatThrownBy(() -> new ChatModelFactory(properties("cloud", "https://ollama.com/api", CREDENTIAL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bare host")
                .hasMessageContaining("not https://ollama.com/api");
    }

    @Test
    void acceptsTheBareHost() {
        assertThatCode(() -> new ChatModelFactory(properties("cloud", "https://ollama.com", CREDENTIAL)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUnknownProviderNamingTheAllowedValues() {
        assertThatThrownBy(() -> new ChatModelFactory(properties("azure", "https://ollama.com", CREDENTIAL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'local' or 'cloud'");
    }

    @Test
    void rejectsAnUnboundedTimeout() {
        ModelProperties p = properties("local", "http://localhost:11434", "");
        p.setTimeoutSeconds(0);

        assertThatThrownBy(() -> new ChatModelFactory(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not ship");
    }

    @Test
    void rejectsAnAbsentModelIdentifier() {
        ModelProperties p = properties("local", "http://localhost:11434", "");
        p.setModelId("  ");

        assertThatThrownBy(() -> new ChatModelFactory(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("L4J_MODEL_ID");
    }

    @Test
    void resolvesTheProviderModeFromConfigurationAlone() {
        assertThat(new ChatModelFactory(properties("cloud", "https://ollama.com", CREDENTIAL)).providerMode())
                .isEqualTo(ProviderMode.CLOUD);
        assertThat(new ChatModelFactory(properties("local", "http://localhost:11434", "")).providerMode())
                .isEqualTo(ProviderMode.LOCAL);
    }

    @Test
    void buildsAModelForBothModesWithoutACodeChange() {
        // Principle II: switching providers is configuration only. Same class, same call path.
        assertThat(new ChatModelFactory(properties("cloud", "https://ollama.com", CREDENTIAL)).chatModel())
                .isNotNull();
        assertThat(new ChatModelFactory(properties("local", "http://localhost:11434", "")).chatModel())
                .isNotNull();
    }

    @Test
    void theCredentialNeverAppearsInTheBuiltModelOrThePropertiesRendering() {
        ChatModelFactory factory = new ChatModelFactory(properties("cloud", "https://ollama.com", CREDENTIAL));

        // R-012: the Supplier overload means no header map is held as a field on the model.
        assertThat(factory.chatModel().toString()).doesNotContain(CREDENTIAL);
        assertThat(properties("cloud", "https://ollama.com", CREDENTIAL).toString())
                .doesNotContain(CREDENTIAL)
                .contains("<redacted>");
    }

    // ----- Contradictory provider and endpoint. Regression for a run recorded as LOCAL that went to
    // ollama.com with no credential and failed with {"error":"Unauthorized"}: L4J_PROVIDER was unset, so it
    // defaulted to local, while the base URL and the key both pointed at the hosted service. -----

    @Test
    void refusesLocalModeAgainstTheHostedServiceNamingTheSettingToChange() {
        assertThatThrownBy(() -> new ChatModelFactory(properties("local", "https://ollama.com", CREDENTIAL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("L4J_PROVIDER")
                .hasMessageContaining("cloud")
                .hasMessageContaining("https://ollama.com")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CREDENTIAL));
    }

    @Test
    void refusesLocalModeAgainstAHostedSubdomainToo() {
        assertThatThrownBy(() -> new ChatModelFactory(properties("local", "https://api.ollama.com", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("L4J_PROVIDER");
    }

    @Test
    void refusesCloudModeAgainstALocalEndpointNamingTheBaseUrl() {
        // The mirror contradiction: a bearer token sent to a local server that neither needs nor checks it.
        assertThatThrownBy(() -> new ChatModelFactory(properties("cloud", "http://localhost:11434", CREDENTIAL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("L4J_MODEL_BASE_URL")
                .hasMessageContaining("https://ollama.com");
    }

    @Test
    void refusesCloudModeAgainstTheLoopbackAddress() {
        assertThatThrownBy(() -> new ChatModelFactory(properties("cloud", "http://127.0.0.1:11434", CREDENTIAL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("L4J_MODEL_BASE_URL");
    }

    @Test
    void acceptsLocalModeAgainstASelfHostedRemoteOllama() {
        // A GPU box on the network running Ollama is still local mode: no credential, not the hosted service.
        assertThatCode(() -> new ChatModelFactory(properties("local", "http://gpu-box.lan:11434", "")))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsLocalModeWhenAKeyHappensToBeSetInTheEnvironment() {
        // Having OLLAMA_API_KEY exported in a shell profile is common and not itself a mistake in local mode.
        assertThatCode(() -> new ChatModelFactory(properties("local", "http://localhost:11434", CREDENTIAL)))
                .doesNotThrowAnyException();
    }
}
