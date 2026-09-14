package dev.l4jlab.legacy.security;

import com.nimbusds.jose.jwk.JWKSet;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.Map;

/** Fetches the issuer's published keys once, and caches them. */
@Singleton
@Requires(missingBeans = JwksSource.class)
public class HttpJwksSource implements JwksSource {

    private final URI jwksUri;
    private volatile JWKSet cached;

    public HttpJwksSource(@Value("${legacy.issuer-url:`http://token-issuer:8080`}") String issuerUrl) {
        this.jwksUri = URI.create(issuerUrl + "/.well-known/jwks.json");
    }

    @Override
    public JWKSet keys() {
        JWKSet keys = cached;
        if (keys != null) {
            return keys;
        }
        synchronized (this) {
            if (cached == null) {
                // Created per fetch rather than injected: the issuer URL is configuration, and a
                // @Client cannot bind to a value known only at runtime.
                try (HttpClient client = HttpClient.create(jwksUri.toURL())) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = client.toBlocking()
                        .retrieve(HttpRequest.GET(jwksUri.getPath()), Map.class);
                    cached = JWKSet.parse(body);
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot read the issuer's keys from " + jwksUri, e);
                }
            }
            return cached;
        }
    }
}
