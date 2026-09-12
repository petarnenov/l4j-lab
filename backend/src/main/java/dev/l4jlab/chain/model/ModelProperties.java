package dev.l4jlab.chain.model;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.time.Duration;

/**
 * The whole configuration surface for reaching a model. Endpoint, identifier, and credential are
 * all configuration and none is hardcoded at a call site (Principle II).
 *
 * <p>{@link #toString()} is overridden to redact the credential. Principle II forbids it reaching a
 * log line, and a properties bean is exactly the kind of object that ends up in one.
 */
@ConfigurationProperties("l4j.model")
public class ModelProperties {

    private String provider = "local";
    private String baseUrl = "http://localhost:11434";
    private String modelId = "llama3.2";
    private String apiKey = "";
    private int timeoutSeconds = 45;

    public ProviderMode providerMode() {
        return switch (provider == null ? "" : provider.trim().toLowerCase()) {
            case "cloud" -> ProviderMode.CLOUD;
            case "local" -> ProviderMode.LOCAL;
            default -> throw new IllegalStateException(
                    "L4J_PROVIDER must be 'local' or 'cloud', got '" + provider + "'");
        };
    }

    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds);
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String toString() {
        return "ModelProperties{provider=" + provider
                + ", baseUrl=" + baseUrl
                + ", modelId=" + modelId
                + ", apiKey=" + (hasApiKey() ? "<redacted>" : "<absent>")
                + ", timeoutSeconds=" + timeoutSeconds + '}';
    }
}
