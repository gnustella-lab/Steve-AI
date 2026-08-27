package com.steve.ai.llm.resilience;

import com.steve.ai.llm.async.LLMResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Fallback handler that generates pattern-based responses when LLM calls fail.
 *
 * <p>Provides graceful degradation when all LLM providers are unavailable.
 * Uses simple pattern matching to recognize common Minecraft commands and
 * generate appropriate action responses.</p>
 *
 * <p><b>When is this used?</b></p>
 * <ul>
 *   <li>Circuit breaker is OPEN (provider experiencing failures)</li>
 *   <li>All retry attempts exhausted</li>
 *   <li>Rate limiter rejects request</li>
 *   <li>Network is completely unavailable</li>
 * </ul>
 *
 * <p><b>Design Philosophy:</b></p>
 * <ul>
 *   <li>Fail closed: never invent world mutations while the planner is unavailable</li>
 *   <li>Only a non-mutating follow action is allowed as a local pattern match</li>
 *   <li>Every other intent, including mine/build/attack/gather, returns blocked</li>
 * </ul>
 *
 * <p><b>Supported Patterns:</b></p>
 * <ul>
 *   <li><b>follow:</b> Matches "follow", "come", "follow me" — no world mutation</li>
 *   <li><b>default:</b> Matches nothing — returns a blocked operational decision</li>
 * </ul>
 *
 * @since 1.1.0
 */
public class LLMFallbackHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LLMFallbackHandler.class);

    // Pattern-based fallback responses in JSON format matching ResponseParser expectations.
    // Only non-mutating follow is allowed; mine/build/attack stay fail-closed.
    private static final List<FallbackRule> FALLBACK_RULES = List.of(
        new FallbackRule(
            Pattern.compile("(?i).*(follow|come|here|with me|accompany).*"),
            "{\"decision\":\"act\",\"summary\":\"Follow the nearest player\",\"goalStatus\":\"in_progress\","
                + "\"tasks\":[{\"action\":\"follow\",\"parameters\":{\"player\":\"me\"}}]}"
        )
    );

    // Default response when no pattern matches
    private static final String DEFAULT_RESPONSE =
        "{\"decision\":\"blocked\",\"summary\":\"No safe offline action is available\","
            + "\"goalStatus\":\"blocked\",\"tasks\":[]}";

    /**
     * Generates a fallback response based on pattern matching.
     *
     * <p>Analyzes the prompt text to identify the user's intent and returns
     * a pre-configured action response. If no pattern matches, returns a
     * blocked operational decision with no tasks.</p>
     *
     * @param prompt Original prompt that failed
     * @param error  The error that triggered the fallback (for logging)
     * @return LLMResponse containing pattern-matched action or default wait action
     */
    public LLMResponse generateFallback(String prompt, Throwable error) {
        LOGGER.warn("Generating fallback response for prompt: '{}' (error: {})",
            truncatePrompt(prompt, 50),
            error != null ? error.getClass().getSimpleName() + ": " + error.getMessage() : "unknown");

        // Try to match against known patterns
        String responseContent = matchPattern(prompt);
        String matchedPattern = responseContent.equals(DEFAULT_RESPONSE) ? "default" : "pattern-match";

        LOGGER.info("Fallback response generated (matched: {})", matchedPattern);

        return LLMResponse.builder()
            .content(responseContent)
            .model("fallback-pattern-matcher")
            .providerId("fallback")
            .latencyMs(0)
            .tokensUsed(0)
            .fromCache(false)
            .build();
    }

    /**
     * Matches the prompt against known patterns.
     *
     * @param prompt The prompt to analyze
     * @return Matching response JSON or default response
     */
    private String matchPattern(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return DEFAULT_RESPONSE;
        }

        String lowerPrompt = prompt.toLowerCase();

        for (FallbackRule rule : FALLBACK_RULES) {
            if (rule.pattern.matcher(lowerPrompt).matches()) {
                LOGGER.debug("Matched pattern: {}", rule.pattern.pattern());
                return rule.response;
            }
        }

        LOGGER.debug("No pattern matched, using default response");
        return DEFAULT_RESPONSE;
    }

    /**
     * Truncates a prompt for logging purposes.
     *
     * @param prompt Prompt to truncate
     * @param maxLength Maximum length
     * @return Truncated prompt with "..." if needed
     */
    private String truncatePrompt(String prompt, int maxLength) {
        if (prompt == null) {
            return "[null]";
        }
        if (prompt.length() <= maxLength) {
            return prompt;
        }
        return prompt.substring(0, maxLength) + "...";
    }

    /**
     * Checks if a prompt would match any known pattern.
     *
     * <p>Useful for testing and debugging.</p>
     *
     * @param prompt The prompt to check
     * @return true if a pattern matches, false if would use default
     */
    public boolean wouldMatchPattern(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return false;
        }

        String lowerPrompt = prompt.toLowerCase();
        return FALLBACK_RULES.stream()
            .anyMatch(rule -> rule.pattern.matcher(lowerPrompt).matches());
    }

    /**
     * Returns the number of registered patterns.
     *
     * @return Pattern count
     */
    public int getPatternCount() {
        return FALLBACK_RULES.size();
    }

    private record FallbackRule(Pattern pattern, String response) {
    }
}
