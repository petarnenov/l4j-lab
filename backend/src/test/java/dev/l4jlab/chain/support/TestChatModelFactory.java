package dev.l4jlab.chain.support;

import dev.langchain4j.model.chat.ChatModel;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Substitutes the fake for the real model in the default test environment.
 *
 * <p>This is not tidiness. {@code ChatModelFactory} is a {@code @Context} bean that aborts startup
 * when the credential is absent, which is what FR-016 asks for in production. Without this
 * substitution the whole default suite would fail on any machine with no OLLAMA_API_KEY, and
 * Principle IV requires tests that do not touch the model to run with no credential and no network.
 */
@Factory
@Requires(env = "test")
public class TestChatModelFactory {

    @Singleton
    @Replaces(ChatModel.class)
    public ChatModel chatModel() {
        return new FakeChatModel();
    }
}
