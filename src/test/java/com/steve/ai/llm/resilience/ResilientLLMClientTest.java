package com.steve.ai.llm.resilience;

import com.steve.ai.llm.async.AsyncLLMClient;
import com.steve.ai.llm.async.LLMCache;
import com.steve.ai.llm.async.LLMException;
import com.steve.ai.llm.async.LLMResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResilientLLMClientTest {

    @Test
    void retriesExceptionalAsyncResponsesBeforeUsingFallback() {
        AtomicInteger attempts = new AtomicInteger();
        AsyncLLMClient delegate = new FakeClient(params -> {
            if (attempts.incrementAndGet() < 3) {
                return CompletableFuture.failedFuture(new LLMException(
                    "temporary failure",
                    LLMException.ErrorType.SERVER_ERROR,
                    "fake",
                    true
                ));
            }
            return CompletableFuture.completedFuture(response("recovered"));
        });
        ResilientLLMClient client = new ResilientLLMClient(
            delegate, new LLMCache(), new LLMFallbackHandler());

        LLMResponse result = client.sendAsync("prompt", params("system-a")).join();

        assertEquals(3, attempts.get());
        assertEquals("recovered", result.getContent());
        assertEquals("fake", result.getProviderId());
    }

    @Test
    void cacheKeyIncludesRequestParametersThatChangeTheResponse() {
        AtomicInteger calls = new AtomicInteger();
        AsyncLLMClient delegate = new FakeClient(params -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(response((String) params.get("systemPrompt")));
        });
        ResilientLLMClient client = new ResilientLLMClient(
            delegate, new LLMCache(), new LLMFallbackHandler());

        LLMResponse first = client.sendAsync("same prompt", params("system-a")).join();
        LLMResponse second = client.sendAsync("same prompt", params("system-b")).join();

        assertEquals(2, calls.get());
        assertEquals("system-a", first.getContent());
        assertEquals("system-b", second.getContent());
        assertFalse(second.isFromCache());
    }

    @Test
    void fallbackMatchesTheOriginalCommandInsteadOfWorldContext() {
        AsyncLLMClient delegate = new FakeClient(params -> CompletableFuture.failedFuture(
            new LLMException(
                "provider unavailable",
                LLMException.ErrorType.AUTH_ERROR,
                "fake",
                false
            )
        ));
        ResilientLLMClient client = new ResilientLLMClient(
            delegate, new LLMCache(), new LLMFallbackHandler());

        Map<String, Object> params = new java.util.HashMap<>(params("system-a"));
        params.put("fallbackPrompt", "build a house");
        LLMResponse result = client.sendAsync(
            "World context contains stone and iron, but the command is elsewhere", params).join();

        org.junit.jupiter.api.Assertions.assertTrue(
            result.getContent().contains("\"action\":\"build\""));
    }

    @Test
    void malformedOperationalResponsesAreNotCachedWhenValidatorRejectsThem() {
        AtomicInteger calls = new AtomicInteger();
        AsyncLLMClient delegate = new FakeClient(params -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(response("not-json"));
        });
        LLMCache cache = new LLMCache();
        ResilientLLMClient client = new ResilientLLMClient(
            delegate, cache, new LLMFallbackHandler(), value -> value.getContent().startsWith("{"));

        client.sendAsync("same", params("system")).join();
        client.sendAsync("same", params("system")).join();

        assertEquals(2, calls.get());
        assertEquals(0, cache.size());
    }

    @Test
    void requestAwareCacheValidationHonorsThePlanningHorizon() {
        AtomicInteger calls = new AtomicInteger();
        AsyncLLMClient delegate = new FakeClient(params -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(response("bounded"));
        });
        LLMCache cache = new LLMCache();
        ResilientLLMClient client = new ResilientLLMClient(delegate, cache, new LLMFallbackHandler(),
            (value, params) -> ((Number) params.getOrDefault("horizon", 64)).intValue() >= 2);
        Map<String, Object> request = new java.util.HashMap<>(params("system"));
        request.put("horizon", 1);

        client.sendAsync("same", request).join();
        client.sendAsync("same", request).join();

        assertEquals(2, calls.get());
        assertEquals(0, cache.size());
    }

    private static Map<String, Object> params(String systemPrompt) {
        return Map.of(
            "model", "fake-model",
            "systemPrompt", systemPrompt,
            "maxTokens", 100,
            "temperature", 0.2
        );
    }

    private static LLMResponse response(String content) {
        return LLMResponse.builder()
            .content(content)
            .model("fake-model")
            .providerId("fake")
            .tokensUsed(1)
            .latencyMs(1)
            .fromCache(false)
            .build();
    }

    @FunctionalInterface
    private interface RequestHandler {
        CompletableFuture<LLMResponse> send(Map<String, Object> params);
    }

    private static class FakeClient implements AsyncLLMClient {
        private final RequestHandler handler;

        private FakeClient(RequestHandler handler) {
            this.handler = handler;
        }

        @Override
        public CompletableFuture<LLMResponse> sendAsync(String prompt, Map<String, Object> params) {
            return handler.send(params);
        }

        @Override
        public String getProviderId() {
            return "fake";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }
    }
}
