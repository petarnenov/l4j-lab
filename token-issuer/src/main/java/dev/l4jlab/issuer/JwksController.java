package dev.l4jlab.issuer;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;

import java.util.Map;

/**
 * Publishes the verification key, so the MCP server and the legacy API verify rather than share a
 * secret. Both fetch it at startup; neither is given the private key.
 */
@DevOnly
@Controller("/.well-known")
public class JwksController {

    private final KeyProvider keys;

    public JwksController(KeyProvider keys) {
        this.keys = keys;
    }

    @Get("/jwks.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> jwks() {
        return keys.jwkSet().toJSONObject();
    }
}
