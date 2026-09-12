package dev.l4jlab.chain.support;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * Base for the tests that need a real database. Starts one PostgreSQL container per class, with
 * pgvector present because the project stack declares it, and lets Flyway apply the committed
 * migrations exactly as it does at startup.
 */
@RequiresDocker
public abstract class PostgresTest {

    protected static PostgreSQLContainer<?> postgres;
    protected static ApplicationContext context;

    @BeforeAll
    static void startDatabase() {
        postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
                .withDatabaseName("l4j")
                .withUsername("l4j")
                .withPassword("l4j");
        postgres.start();
        context = ApplicationContext.builder().properties(databaseProperties()).start();
    }

    /** The properties that point a context at this class's database. For a test that starts its own. */
    protected static Map<String, Object> databaseProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.default.enabled", true);
        properties.put("datasources.default.url", postgres.getJdbcUrl());
        properties.put("datasources.default.username", postgres.getUsername());
        properties.put("datasources.default.password", postgres.getPassword());
        properties.put("datasources.default.driver-class-name", "org.postgresql.Driver");
        properties.put("datasources.default.dialect", "POSTGRES");
        properties.put("datasources.default.schema-generate", "NONE");
        properties.put("flyway.enabled", true);
        properties.put("flyway.datasources.default.enabled", true);
        properties.put("l4j.model.provider", "local");
        properties.put("l4j.model.base-url", "http://localhost:11434");
        properties.put("l4j.model.model-id", "test-model");
        return properties;
    }

    @AfterAll
    static void stopDatabase() {
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }
}
