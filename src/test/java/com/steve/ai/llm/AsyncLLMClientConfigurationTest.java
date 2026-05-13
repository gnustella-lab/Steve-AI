package com.steve.ai.llm;

import com.steve.ai.llm.async.AsyncGeminiClient;
import com.steve.ai.llm.async.AsyncGroqClient;
import com.steve.ai.llm.async.AsyncLLMClient;
import com.steve.ai.llm.async.AsyncOpenAIClient;
import com.steve.ai.llm.async.LLMException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncLLMClientConfigurationTest {

    @Test
    void openAIClientFailsFastWhenApiKeyIsMissing() {
        assertMissingKeyFailure(new AsyncOpenAIClient("", "gpt-4o-mini", 1000, 0.2), "openai");
    }

    @Test
    void groqClientFailsFastWhenApiKeyIsMissing() {
        assertMissingKeyFailure(new AsyncGroqClient("   ", "llama-3.1-8b-instant", 500, 0.2), "groq");
    }

    @Test
    void geminiClientFailsFastWhenApiKeyIsMissing() {
        assertMissingKeyFailure(new AsyncGeminiClient(null, "gemini-1.5-flash", 1000, 0.2), "gemini");
    }

    private static void assertMissingKeyFailure(AsyncLLMClient client, String providerId) {
        assertEquals(providerId, client.getProviderId());
        assertFalse(client.isHealthy());

        CompletionException exception = assertThrows(CompletionException.class,
            () -> client.sendAsync("build a house", Map.of()).join());

        LLMException llmException = assertInstanceOf(LLMException.class, exception.getCause());
        assertEquals(LLMException.ErrorType.AUTH_ERROR, llmException.getErrorType());
        assertEquals(providerId, llmException.getProviderId());
        assertFalse(llmException.isRetryable());
    }
}
