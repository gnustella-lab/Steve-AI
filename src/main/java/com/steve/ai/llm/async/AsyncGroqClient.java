package com.steve.ai.llm.async;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous Groq API client using Java HttpClient's sendAsync().
 *
 * <p>Groq provides BLAZING FAST inference on open-source LLMs (LLaMA, Mixtral).
 * Uses OpenAI-compatible API format for easy integration.</p>
 *
 * <p><b>API Endpoint:</b> https://api.groq.com/openai/v1/chat/completions</p>
 *
 * <p><b>Performance:</b> 0.5-2 seconds typical latency (much faster than OpenAI/Gemini)</p>
 *
 * <p><b>Supported Models:</b></p>
 * <ul>
 *   <li>llama-3.1-70b-versatile (best quality)</li>
 *   <li>llama-3.1-8b-instant (fastest, recommended)</li>
 *   <li>mixtral-8x7b-32768 (good balance)</li>
 *   <li>gemma-7b-it (Google's open model)</li>
 * </ul>
 *
 * <p><b>Free Tier Limits:</b></p>
 * <ul>
 *   <li>30 requests per minute</li>
 *   <li>14,400 requests per day</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> Thread-safe. HttpClient is thread-safe and immutable.</p>
 *
 * @since 1.1.0
 */
public class AsyncGroqClient implements AsyncLLMClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncGroqClient.class);
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String PROVIDER_ID = "groq";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    /**
     * Constructs an AsyncGroqClient.
     *
     * @param apiKey      Groq API key (required)
     * @param model       Model to use (e.g., "llama-3.1-8b-instant")
     * @param maxTokens   Maximum tokens in response (e.g., 500)
     * @param temperature Response randomness (0.0 - 2.0)
     * <p>If the API key is missing, requests fail fast with an authentication error.</p>
     */
    public AsyncGroqClient(String apiKey, String model, int maxTokens, double temperature) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;

        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2) // Groq benefits from HTTP/2
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        LOGGER.info("AsyncGroqClient initialized (model: {}, maxTokens: {}, temperature: {})",
            model, maxTokens, temperature);
    }

    @Override
    public CompletableFuture<LLMResponse> sendAsync(String prompt, Map<String, Object> params) {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(new LLMException(
                "Groq API key is not configured",
                LLMException.ErrorType.AUTH_ERROR,
                PROVIDER_ID,
                false
            ));
        }

        long startTime = System.currentTimeMillis();

        String requestBody = buildRequestBody(prompt, params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(GROQ_API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(30))
            .build();

        LOGGER.debug("[groq] Sending async request (prompt length: {} chars)", prompt.length());

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                long latencyMs = System.currentTimeMillis() - startTime;

                if (response.statusCode() != 200) {
                    LLMException.ErrorType errorType = determineErrorType(response.statusCode());
                    boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;

                    LOGGER.error("[groq] API error: status={}, body={}", response.statusCode(),
                        truncate(response.body(), 200));

                    throw new LLMException(
                        "Groq API error: HTTP " + response.statusCode(),
                        errorType,
                        PROVIDER_ID,
                        retryable
                    );
                }

                return parseResponse(response.body(), latencyMs);
            });
    }

    /**
     * Builds the JSON request body (OpenAI-compatible format).
     *
     * @param prompt User prompt
     * @param params Additional parameters
     * @return JSON string
     */
    private String buildRequestBody(String prompt, Map<String, Object> params) {
        JsonObject body = new JsonObject();

        String modelToUse = (String) params.getOrDefault("model", this.model);
        int maxTokensToUse = (int) params.getOrDefault("maxTokens", this.maxTokens);
        double tempToUse = (double) params.getOrDefault("temperature", this.temperature);

        body.addProperty("model", modelToUse);
        body.addProperty("max_tokens", maxTokensToUse);
        body.addProperty("temperature", tempToUse);

        JsonArray messages = new JsonArray();

        // System message
        String systemPrompt = (String) params.get("systemPrompt");
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messages.add(systemMessage);
        }

        // User message
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        body.add("messages", messages);

        return body.toString();
    }

    /**
     * Parses Groq API response (OpenAI-compatible format).
     *
     * @param responseBody Raw JSON response
     * @param latencyMs    Request latency
     * @return Parsed LLMResponse
     */
    private LLMResponse parseResponse(String responseBody, long latencyMs) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

            if (!json.has("choices") || json.getAsJsonArray("choices").isEmpty()) {
                throw new LLMException(
                    "Groq response missing 'choices' array",
                    LLMException.ErrorType.INVALID_RESPONSE,
                    PROVIDER_ID,
                    false
                );
            }

            JsonObject firstChoice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            String content = message.get("content").getAsString();

            int tokensUsed = 0;
            if (json.has("usage")) {
                JsonObject usage = json.getAsJsonObject("usage");
                tokensUsed = usage.get("total_tokens").getAsInt();
            }

            LOGGER.debug("[groq] Response received (latency: {}ms, tokens: {})", latencyMs, tokensUsed);

            return LLMResponse.builder()
                .content(content)
                .model(model)
                .providerId(PROVIDER_ID)
                .latencyMs(latencyMs)
                .tokensUsed(tokensUsed)
                .fromCache(false)
                .build();

        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[groq] Failed to parse response: {}", truncate(responseBody, 200), e);
            throw new LLMException(
                "Failed to parse Groq response: " + e.getMessage(),
                LLMException.ErrorType.INVALID_RESPONSE,
                PROVIDER_ID,
                false,
                e
            );
        }
    }

    private LLMException.ErrorType determineErrorType(int statusCode) {
        return switch (statusCode) {
            case 429 -> LLMException.ErrorType.RATE_LIMIT;
            case 401, 403 -> LLMException.ErrorType.AUTH_ERROR;
            case 400 -> LLMException.ErrorType.CLIENT_ERROR;
            case 408 -> LLMException.ErrorType.TIMEOUT;
            default -> {
                if (statusCode >= 500) {
                    yield LLMException.ErrorType.SERVER_ERROR;
                }
                yield LLMException.ErrorType.CLIENT_ERROR;
            }
        };
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "[null]";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isHealthy() {
        return isConfigured();
    }

    /**
     * Returns whether this client has the credentials required to make API calls.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
