package com.steve.ai.llm;

import com.steve.ai.di.SimpleServiceContainer;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.plugin.CoreActionsPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AutonomousResponseSchemaTest {
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
    void parsesBoundedOperationalDecisionAndGoalStatus() {
        ResponseParser.ParsedResponse parsed = ResponseParser.parseAIResponse("""
            {"decision":"act","summary":"Need a better tool","goalStatus":"in_progress",
             "tasks":[{"action":"craft","parameters":{"item":"stone_pickaxe","quantity":1}}]}
            """);

        assertNotNull(parsed);
        assertEquals(ResponseParser.Decision.ACT, parsed.getDecision());
        assertEquals("in_progress", parsed.getGoalStatus());
        assertEquals(1, parsed.getTasks().size());
    }

    @Test
    void rejectsUnknownDecisionAndHorizonOverflow() {
        assertNull(ResponseParser.parseAIResponse(
            "{\"decision\":\"think_private\",\"summary\":\"x\",\"goalStatus\":\"in_progress\",\"tasks\":[]}"));
        assertNull(ResponseParser.parseAIResponse(
            "{\"decision\":\"act\",\"summary\":\"x\",\"goalStatus\":\"in_progress\",\"tasks\":["
                + "{\"action\":\"inspect_inventory\",\"parameters\":{}},"
                + "{\"action\":\"inspect_inventory\",\"parameters\":{}}]}" , 1));
    }
}
