package dev.l4jlab.chain.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * The provider seam. This is the one place in the project that knows a local Ollama from the hosted
 * Ollama Cloud service, and it decides from configuration alone: no node ever sees the provider
 * (Principle II).
 *
 * <p>{@code @Context} makes Micronaut instantiate this eagerly during context initialisation, so a
 * missing credential fails the boot with a named message rather than surfacing forty-five seconds
 * into the first run (FR-016).
 */
@Factory
@Context
public class ChatModelFactory {

    private final ModelProperties properties;
    private final ProviderMode providerMode;

    public ChatModelFactory(ModelProperties properties) {
        this.properties = properties;
        this.providerMode = properties.providerMode();
        validate();
    }

    private void validate() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "L4J_MODEL_BASE_URL is not set. Set it to https://ollama.com for cloud mode or "
                            + "http://localhost:11434 for local mode.");
        }
        // R-001: LangChain4j builds its own paths starting with a slash, so a base URL carrying a
        // path segment is silently dropped and requests land somewhere unintended. Catch it here,
        // where the message can say so, rather than debugging a 404 later.
        String withoutScheme = baseUrl.replaceFirst("^https?://", "");
        if (withoutScheme.contains("/") && !withoutScheme.endsWith("/")) {
            throw new IllegalStateException(
                    "L4J_MODEL_BASE_URL must be the bare host with no path, got '" + baseUrl
                            + "'. A path segment there is silently dropped by the client. Use "
                            + "https://ollama.com, not https://ollama.com/api.");
        }
        if (properties.getModelId() == null || properties.getModelId().isBlank()) {
            throw new IllegalStateException(
                    "L4J_MODEL_ID is not set. Name the model to use, for example gpt-oss:120b.");
        }
        if (properties.getTimeoutSeconds() <= 0) {
            throw new IllegalStateException(
                    "L4J_MODEL_TIMEOUT_SECONDS must be positive, got " + properties.getTimeoutSeconds()
                            + ". An unbounded model call must not ship.");
        }
        if (providerMode == ProviderMode.CLOUD && !properties.hasApiKey()) {
            throw new IllegalStateException(
                    "OLLAMA_API_KEY is not set, and L4J_PROVIDER is 'cloud'. Set the credential, or "
                            + "set L4J_PROVIDER=local to use a locally served Ollama.");
        }
        rejectContradictoryProvider(baseUrl);
    }

    /**
     * A provider mode that contradicts the endpoint is malformed configuration, and Principle II requires it
     * to fail here, at startup, naming the setting, never as a silent fallback.
     *
     * <p>This exists because it happened: L4J_PROVIDER was left unset, so it defaulted to local, while the
     * base URL and the key both pointed at Ollama Cloud. The application started cleanly, sent no credential,
     * failed every run with {"error":"Unauthorized"}, and recorded those runs as LOCAL although they had gone
     * to the hosted service.
     */
    private void rejectContradictoryProvider(String baseUrl) {
        String host = hostOf(baseUrl);
        boolean hosted = host.equals("ollama.com") || host.endsWith(".ollama.com");
        boolean loopback = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");

        if (providerMode == ProviderMode.LOCAL && hosted) {
            throw new IllegalStateException(
                    "L4J_MODEL_BASE_URL points at the hosted Ollama Cloud service (" + baseUrl + "), but "
                            + "L4J_PROVIDER is 'local', which sends no credential and would fail every run "
                            + "with Unauthorized. Set L4J_PROVIDER=cloud to use Ollama Cloud, or point "
                            + "L4J_MODEL_BASE_URL at a locally served Ollama such as http://localhost:11434.");
        }
        if (providerMode == ProviderMode.CLOUD && loopback) {
            throw new IllegalStateException(
                    "L4J_PROVIDER is 'cloud', but L4J_MODEL_BASE_URL points at a local server (" + baseUrl
                            + "). Set L4J_MODEL_BASE_URL=https://ollama.com to use Ollama Cloud, or set "
                            + "L4J_PROVIDER=local.");
        }
    }

    private static String hostOf(String baseUrl) {
        try {
            String host = URI.create(baseUrl.trim()).getHost();
            if (host == null) {
                return "";
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "L4J_MODEL_BASE_URL is not a valid URL: '" + baseUrl + "'. Use https://ollama.com or "
                            + "http://localhost:11434.");
        }
    }

    @Singleton
    @Bean
    public ChatModel chatModel() {
        OllamaChatModel.OllamaChatModelBuilder builder =
                OllamaChatModel.builder()
                        .baseUrl(properties.getBaseUrl())
                        .modelName(properties.getModelId())
                        .timeout(properties.timeout())
                        // Never true in cloud mode: the client logs request headers, which carry
                        // the bearer token (Principle II).
                        .logRequests(false)
                        .logResponses(false);

        if (providerMode == ProviderMode.CLOUD) {
            // R-012: the Supplier overload, not the Map one. A map built once is a field on the
            // model object, reachable by anything that serializes or dumps it, including a debugger
            // projected on a screen during a lesson.
            builder.customHeaders(() -> Map.of("Authorization", "Bearer " + properties.getApiKey()));
        }

        return builder.build();
    }

    public ProviderMode providerMode() {
        return providerMode;
    }

    public String modelId() {
        return properties.getModelId();
    }
}
