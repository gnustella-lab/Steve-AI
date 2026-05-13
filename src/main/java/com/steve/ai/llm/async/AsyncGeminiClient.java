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
 * Asynchronous Google Gemini API client using Java HttpClient's sendAsync().
 *
 * <p>Gemini provides powerful multi-modal AI capabilities with competitive pricing.
 * Uses Google's proprietary API format (different from OpenAI).</p>
 *
 * <p><b>API Endpoint:</b> https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent</p>
 *
 * <p><b>Supported Models:</b></p>
 * <ul>
 *   <li>gemini-2.0-flash-exp (newest, experimental)</li>
 *   <li>gemini-1.5-flash (fast, recommended)</li>
 *   <li>gemini-1.5-pro (best quality)</li>
 *   <li>gemini-pro (legacy, still supported)</li>
 * </ul>
 *
 * <p><b>Free Tier Limits:</b></p>
 * <ul>
 *   <li>15 requests per minute</li>
 *   <li>1,500 requests per day</li>
 * </ul>
 *
 * <p><b>API Format Differences:</b></p>
 * <ul>
 *   <li>Uses "contents" array with "parts" (not "messages")</li>
 *   <li>API key in query string (not Authorization header)</li>
 *   <li>Response in "candidates[].content.parts[].text"</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> Thread-safe. HttpClient is thread-safe and immutable.</p>
 *
 * @since 1.1.0
 */
public class AsyncGeminiClient implements AsyncLLMClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncGeminiClient.class);
    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String PROVIDER_ID = "gemini";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    /**
     * Constructs an AsyncGeminiClient.
     *
     * @param apiKey      Google AI Studio API key (required)
     * @param model       Model to use (e.g., "gemini-1.5-flash")
     * @param maxTokens   Maximum tokens in response (e.g., 1000)
     * @param temperature Response randomness (0.0 - 2.0)
     * <p>If the API key is missing, requests fail fast with an authentication error.</p>
     */
    public AsyncGeminiClient(String apiKey, String model, int maxTokens, double temperature) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        LOGGER.info("AsyncGeminiClient initialized (model: {}, maxTokens: {}, temperature: {})",
            model, maxTokens, temperature);
    }

    @Override
    public CompletableFuture<LLMResponse> sendAsync(String prompt, Map<String, Object> params) {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(new LLMException(
                "Gemini API key is not configured",
                LLMException.ErrorType.AUTH_ERROR,
                PROVIDER_ID,
                false
            ));
        }

        long startTime = System.currentTimeMillis();

        String requestBody = buildRequestBody(prompt, params);
        String urlWithKey = GEMINI_API_BASE + model + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(urlWithKey))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(60)) // Gemini can be slower
            .build();

        LOGGER.debug("[gemini] Sending async request (prompt length: {} chars)", prompt.length());

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                long latencyMs = System.currentTimeMillis() - startTime;

                if (response.statusCode() != 200) {
                    LLMException.ErrorType errorType = determineErrorType(response.statusCode());
                    boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;

                    LOGGER.error("[gemini] API error: status={}, body={}", response.statusCode(),
                        truncate(response.body(), 200));

                    throw new LLMException(
                        "Gemini API error: HTTP " + response.statusCode(),
                        errorType,
                        PROVIDER_ID,
                        retryable
                    );
                }

                return parseResponse(response.body(), latencyMs);
            });
    }

    /**
     * Builds the JSON request body in Gemini's format.
     *
     * <p>Gemini format uses "contents" with "parts" instead of OpenAI's "messages".</p>
     *
     * @param prompt User prompt
     * @param params Additional parameters
     * @return JSON string
     */
    private String buildRequestBody(String prompt, Map<String, Object> params) {
        JsonObject body = new JsonObject();

        // Build contents array (Gemini format)
        JsonArray contents = new JsonArray();

        // Combine system prompt and user prompt into a single user message
        // (Gemini handles system instructions differently)
        String systemPrompt = (String) params.get("systemPrompt");
        String combinedPrompt = systemPrompt != null && !systemPrompt.isEmpty()
            ? systemPrompt + "\n\n" + prompt
            : prompt;

        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");

        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", combinedPrompt);
        parts.add(textPart);

        userContent.add("parts", parts);
        contents.add(userContent);

        body.add("contents", contents);

        // Generation config
        JsonObject generationConfig = new JsonObject();
        double tempToUse = (double) params.getOrDefault("temperature", this.temperature);
        int maxTokensToUse = (int) params.getOrDefault("maxTokens", this.maxTokens);

        generationConfig.addProperty("temperature", tempToUse);
        generationConfig.addProperty("maxOutputTokens", maxTokensToUse);

        body.add("generationConfig", generationConfig);

        return body.toString();
    }

    /**
     * Parses Gemini API response.
     *
     * <p>Response format: candidates[0].content.parts[0].text</p>
     *
     * @param responseBody Raw JSON response
     * @param latencyMs    Request latency
     * @return Parsed LLMResponse
     */
    private LLMResponse parseResponse(String responseBody, long latencyMs) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

            // Check for candidates array
            if (!json.has("candidates") || json.getAsJsonArray("candidates").isEmpty()) {
                // Check if there's a blocked response
                if (json.has("promptFeedback")) {
                    JsonObject feedback = json.getAsJsonObject("promptFeedback");
                    if (feedback.has("blockReason")) {
                        String reason = feedback.get("blockReason").getAsString();
                        throw new LLMException(
                            "Gemini blocked the prompt: " + reason,
                            LLMException.ErrorType.CLIENT_ERROR,
                            PROVIDER_ID,
                            false
                        );
                    }
                }

                throw new LLMException(
                    "Gemini response missing 'candidates' array",
                    LLMException.ErrorType.INVALID_RESPONSE,
                    PROVIDER_ID,
                    false
                );
            }

            JsonObject firstCandidate = json.getAsJsonArray("candidates").get(0).getAsJsonObject();

            // Check finish reason
            if (firstCandidate.has("finishReason")) {
                String finishReason = firstCandidate.get("finishReason").getAsString();
                if ("MAX_TOKENS".equals(finishReason)) {
                    LOGGER.warn("[gemini] Response truncated due to MAX_TOKENS limit");
                }
            }

            // Extract content
            if (!firstCandidate.has("content")) {
                throw new LLMException(
                    "Gemini response missing 'content' in candidate",
                    LLMException.ErrorType.INVALID_RESPONSE,
                    PROVIDER_ID,
                    false
                );
            }

            JsonObject content = firstCandidate.getAsJsonObject("content");
            JsonArray parts = content.getAsJsonArray("parts");

            if (parts == null || parts.isEmpty()) {
                throw new LLMException(
                    "Gemini response has no parts in content",
                    LLMException.ErrorType.INVALID_RESPONSE,
                    PROVIDER_ID,
                    false
                );
            }

            String text = parts.get(0).getAsJsonObject().get("text").getAsString();

            // Gemini doesn't always provide usage metrics
            int tokensUsed = 0;
            if (json.has("usageMetadata")) {
                JsonObject usage = json.getAsJsonObject("usageMetadata");
                if (usage.has("totalTokenCount")) {
                    tokensUsed = usage.get("totalTokenCount").getAsInt();
                }
            }

            LOGGER.debug("[gemini] Response received (latency: {}ms, tokens: {})", latencyMs, tokensUsed);

            return LLMResponse.builder()
                .content(text)
                .model(model)
                .providerId(PROVIDER_ID)
                .latencyMs(latencyMs)
                .tokensUsed(tokensUsed)
                .fromCache(false)
                .build();

        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[gemini] Failed to parse response: {}", truncate(responseBody, 200), e);
            throw new LLMException(
                "Failed to parse Gemini response: " + e.getMessage(),
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
            case 408, 504 -> LLMException.ErrorType.TIMEOUT;
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
