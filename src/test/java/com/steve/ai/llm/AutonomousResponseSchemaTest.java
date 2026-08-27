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
    void enforcesDecisionTaskAndGoalStatusConsistency() {
        String task = "{\"action\":\"inspect_inventory\",\"parameters\":{}}";

        assertNull(ResponseParser.parseAIResponse(
            "{\"decision\":\"act\",\"summary\":\"x\",\"goalStatus\":\"in_progress\",\"tasks\":[]}"));
        assertNull(ResponseParser.parseAIResponse(
            "{\"decision\":\"blocked\",\"summary\":\"x\",\"goalStatus\":\"blocked\",\"tasks\":["
                + task + "]}"));
        assertNull(ResponseParser.parseAIResponse(
            "{\"decision\":\"ask_user\",\"summary\":\"x\",\"goalStatus\":\"paused\",\"tasks\":["
                + task + "]}"));
        assertNull(ResponseParser.parseAIResponse(
            "{\"decision\":\"complete\",\"summary\":\"x\",\"goalStatus\":\"in_progress\",\"tasks\":[]}"));
        assertNull(ResponseParser.parseAIResponse(
            "{\"decision\":\"act\",\"summary\":\"x\",\"goalStatus\":\"complete\",\"tasks\":["
                + task + "]}"));
    }

    @Test
    void rejectsGiantHorizonsAndNonFiniteParameterNumbers() {
        String tasks = java.util.stream.IntStream.range(0, 17)
            .mapToObj(index -> "{\"action\":\"inspect_inventory\",\"parameters\":{}}")
            .collect(java.util.stream.Collectors.joining(","));

        assertNull(ResponseParser.parseAIResponse(
            "{\"decision\":\"act\",\"summary\":\"x\",\"goalStatus\":\"in_progress\",\"tasks\":["
                + tasks + "]}"));
        assertNull(ResponseParser.parseAIResponse(
            "{\"decision\":\"act\",\"summary\":\"x\",\"goalStatus\":\"in_progress\",\"tasks\":["
                + "{\"action\":\"mine\",\"parameters\":{\"block\":\"iron_ore\",\"quantity\":1e999}}]}"));
    }
}
