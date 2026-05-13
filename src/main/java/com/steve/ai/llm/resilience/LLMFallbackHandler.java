package com.steve.ai.llm.resilience;

import com.steve.ai.llm.async.LLMResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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
 *   <li>Something is better than nothing - basic functionality continues</li>
 *   <li>Conservative defaults - prefer safe actions (wait) over risky ones</li>
 *   <li>Transparency - responses indicate they're from fallback system</li>
 * </ul>
 *
 * <p><b>Supported Patterns:</b></p>
 * <ul>
 *   <li><b>mine:</b> Matches "mine", "dig", "collect ore"</li>
 *   <li><b>build:</b> Matches "build", "construct", "create house"</li>
 *   <li><b>attack:</b> Matches "attack", "fight", "kill"</li>
 *   <li><b>follow:</b> Matches "follow", "come", "follow me"</li>
 *   <li><b>move:</b> Matches "go to", "move to", "walk"</li>
 *   <li><b>default:</b> Matches nothing - returns "wait" action</li>
 * </ul>
 *
 * @since 1.1.0
 */
public class LLMFallbackHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LLMFallbackHandler.class);

    // Pattern-based fallback responses in JSON format matching ResponseParser expectations
    private static final Map<Pattern, String> PATTERN_RESPONSES = Map.of(
        // Mining patterns
        Pattern.compile("(?i).*(mine|dig|collect|gather|ore|diamond|iron|coal|stone).*"),
        "{\"thoughts\":\"[Fallback] Mining action detected\",\"tasks\":[{\"action\":\"mine\",\"target\":\"iron_ore\",\"quantity\":10}]}",

        // Building patterns
        Pattern.compile("(?i).*(build|construct|create|make).*(house|home|shelter|structure|base).*"),
        "{\"thoughts\":\"[Fallback] Building action detected\",\"tasks\":[{\"action\":\"build\",\"structure\":\"house\",\"size\":\"small\"}]}",

        // Combat patterns
        Pattern.compile("(?i).*(attack|fight|kill|destroy|hostile|monster|zombie|skeleton|creeper).*"),
        "{\"thoughts\":\"[Fallback] Combat action detected\",\"tasks\":[{\"action\":\"attack\",\"target\":\"nearest_hostile\"}]}",

        // Follow patterns
        Pattern.compile("(?i).*(follow|come|here|with me|accompany).*"),
        "{\"thoughts\":\"[Fallback] Follow action detected\",\"tasks\":[{\"action\":\"follow\",\"target\":\"player\"}]}",

        // Movement patterns
        Pattern.compile("(?i).*(go to|move to|walk to|travel|path|navigate).*"),
        "{\"thoughts\":\"[Fallback] Movement action detected\",\"tasks\":[{\"action\":\"pathfind\",\"target\":\"player\"}]}",

        // Placement patterns
        Pattern.compile("(?i).*(place|put|set).*(block|torch|door).*"),
        "{\"thoughts\":\"[Fallback] Placement action detected\",\"tasks\":[{\"action\":\"place_block\",\"block\":\"torch\",\"position\":\"here\"}]}",

        // Stop patterns
        Pattern.compile("(?i).*(stop|halt|cancel|wait|pause|stay).*"),
        "{\"thoughts\":\"[Fallback] Stop action detected\",\"tasks\":[{\"action\":\"wait\",\"duration\":5}]}"
    );

    // Default response when no pattern matches
    private static final String DEFAULT_RESPONSE =
        "{\"thoughts\":\"[Fallback] No pattern matched, waiting\",\"tasks\":[{\"action\":\"wait\",\"duration\":5}]}";

    /**
     * Generates a fallback response based on pattern matching.
     *
     * <p>Analyzes the prompt text to identify the user's intent and returns
     * a pre-configured action response. If no pattern matches, returns a
     * safe "wait" action.</p>
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

        for (Map.Entry<Pattern, String> entry : PATTERN_RESPONSES.entrySet()) {
            if (entry.getKey().matcher(lowerPrompt).matches()) {
                LOGGER.debug("Matched pattern: {}", entry.getKey().pattern());
                return entry.getValue();
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
        return PATTERN_RESPONSES.keySet().stream()
            .anyMatch(pattern -> pattern.matcher(lowerPrompt).matches());
    }

    /**
     * Returns the number of registered patterns.
     *
     * @return Pattern count
     */
    public int getPatternCount() {
        return PATTERN_RESPONSES.size();
    }
}
