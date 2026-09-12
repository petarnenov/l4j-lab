package dev.l4jlab.chain.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for a real provider in every default test. Scriptable, and it counts its calls, which
 * is how the deterministic node tests prove that only the fourth node reaches a model (FR-007).
 */
public class FakeChatModel implements ChatModel {

    private final AtomicInteger callCount = new AtomicInteger();
    private final List<ChatRequest> receivedRequests = new ArrayList<>();

    private String nextResponse = "A fictional summary of the supplied indicators.";
    private Duration delay = Duration.ZERO;
    private RuntimeException failWith;
    private TokenUsage tokenUsage = new TokenUsage(120, 80);

    public FakeChatModel respondWith(String text) {
        this.nextResponse = text;
        return this;
    }

    /** Makes the call take this long, so a timeout can be provoked without waiting for a real one. */
    public FakeChatModel withDelay(Duration delay) {
        this.delay = delay;
        return this;
    }

    public FakeChatModel failWith(RuntimeException e) {
        this.failWith = e;
        return this;
    }

    public FakeChatModel withoutTokenUsage() {
        this.tokenUsage = null;
        return this;
    }

    public int callCount() {
        return callCount.get();
    }

    public List<ChatRequest> receivedRequests() {
        return List.copyOf(receivedRequests);
    }

    public ChatRequest lastRequest() {
        return receivedRequests.isEmpty() ? null : receivedRequests.getLast();
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        callCount.incrementAndGet();
        receivedRequests.add(chatRequest);

        if (!delay.isZero()) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (failWith != null) {
            throw failWith;
        }
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(nextResponse))
                .tokenUsage(tokenUsage)
                .build();
    }
}
