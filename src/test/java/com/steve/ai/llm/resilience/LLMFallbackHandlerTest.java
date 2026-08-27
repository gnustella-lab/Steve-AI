package com.steve.ai.llm.resilience;

import com.steve.ai.di.SimpleServiceContainer;
import com.steve.ai.llm.ResponseParser;
import com.steve.ai.llm.async.LLMResponse;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.plugin.CoreActionsPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMFallbackHandlerTest {

    @BeforeEach
    void registerCoreActions() {
        ActionRegistry.getInstance().clear();
        new CoreActionsPlugin().onLoad(ActionRegistry.getInstance(), new SimpleServiceContainer());
    }

    @AfterEach
    void clearRegistry() {
        ActionRegistry.getInstance().clear();
    }

    @Test
    void offlineFallbackNeverMutatesWorld() {
        LLMFallbackHandler handler = new LLMFallbackHandler();
        for (String prompt : java.util.List.of(
                "mine iron ore", "gather wood", "build a stone house", "attack zombies")) {
            LLMResponse fallback = handler.generateFallback(prompt, new RuntimeException("offline"));
            ResponseParser.ParsedResponse parsed = ResponseParser.parseAIResponse(fallback.getContent());

            assertNotNull(parsed, prompt);
            assertEquals(ResponseParser.Decision.BLOCKED, parsed.getDecision(), prompt);
            assertTrue(parsed.getTasks().isEmpty(), prompt);
            assertFalse(fallback.getContent().contains("\"action\":\"mine\""), prompt);
            assertFalse(fallback.getContent().contains("\"action\":\"build\""), prompt);
            assertFalse(fallback.getContent().contains("\"action\":\"attack\""), prompt);
        }
    }


    @Test
    void unknownIntentFallsBackToSafeBlockedDecisionWithoutLegacyReasoningFields() {
        LLMResponse fallback = new LLMFallbackHandler().generateFallback(
            "do something surprising", new RuntimeException("offline"));

        ResponseParser.ParsedResponse parsed = ResponseParser.parseAIResponse(fallback.getContent());

        assertNotNull(parsed);
        assertEquals(ResponseParser.Decision.BLOCKED, parsed.getDecision());
        assertEquals("blocked", parsed.getGoalStatus());
        assertTrue(parsed.getTasks().isEmpty());
        assertFalse(fallback.getContent().contains("reasoning"));
        assertFalse(fallback.getContent().contains("\"plan\""));
    }

    @Test
    void everyPatternFallbackUsesTheCurrentDecisionEnvelope() {
        LLMResponse fallback = new LLMFallbackHandler().generateFallback(
            "follow me", new RuntimeException("offline"));

        assertNotNull(ResponseParser.parseAIResponse(fallback.getContent()));
        assertTrue(fallback.getContent().contains("\"decision\":\"act\""));
        assertTrue(fallback.getContent().contains("\"summary\""));
        assertTrue(fallback.getContent().contains("\"goalStatus\":\"in_progress\""));
        assertFalse(fallback.getContent().contains("reasoning"));
    }
}
