package com.steve.ai.llm.resilience;

import com.steve.ai.action.Task;
import com.steve.ai.di.SimpleServiceContainer;
import com.steve.ai.llm.ResponseParser;
import com.steve.ai.llm.async.LLMResponse;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.plugin.CoreActionsPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void miningFallbackUsesTheExecutableTaskSchema() {
        LLMResponse fallback = new LLMFallbackHandler().generateFallback(
            "mine iron ore", new RuntimeException("offline"));

        ResponseParser.ParsedResponse parsed = ResponseParser.parseAIResponse(fallback.getContent());

        assertNotNull(parsed);
        assertEquals("Mine nearby iron ore", parsed.getPlan());
        assertEquals(1, parsed.getTasks().size());
        Task task = parsed.getTasks().get(0);
        assertEquals("mine", task.getAction());
        assertEquals("iron_ore", task.getStringParameter("block"));
        assertEquals(10, task.getIntParameter("quantity", -1));
    }

    @Test
    void buildingIntentWinsOverResourceWordsInsideTheBuildingRequest() {
        LLMResponse fallback = new LLMFallbackHandler().generateFallback(
            "build a stone house", new RuntimeException("offline"));

        ResponseParser.ParsedResponse parsed = ResponseParser.parseAIResponse(fallback.getContent());

        assertNotNull(parsed);
        assertEquals(1, parsed.getTasks().size());
        assertEquals("build", parsed.getTasks().get(0).getAction());
    }
}
