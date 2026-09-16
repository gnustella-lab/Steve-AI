package com.steve.ai.llm;

import com.steve.ai.action.Task;
import com.steve.ai.di.SimpleServiceContainer;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.plugin.CoreActionsPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResponseParserTest {

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
    void parsesFencedJsonWithoutMutatingTextInsideStrings() {
        String response = """
            ```json
            {
              "plan": "Keep the original text",
              "tasks": [
                {
                  "action": "mine",
                  "parameters": {"block": "iron_ore", "quantity": 3}
                }
              ]
            }
            ```
            """;

        ResponseParser.ParsedResponse parsed = ResponseParser.parseAIResponse(response);

        assertNotNull(parsed);
        assertEquals("", parsed.getReasoning());
        assertEquals("Keep the original text", parsed.getPlan());
        assertEquals(1, parsed.getTasks().size());
        Task task = parsed.getTasks().get(0);
        assertEquals("mine", task.getAction());
        assertEquals("iron_ore", task.getStringParameter("block"));
        assertEquals(3, task.getIntParameter("quantity", -1));
    }

    @Test
    void parsesOperationalSummaryWithoutPrivateReasoning() {
        String response = """
            {"summary":"Obter ferro e fabricar uma picareta","tasks":[
              {"action":"mine","parameters":{"block":"iron_ore","quantity":3}}
            ]}
            """;

        ResponseParser.ParsedResponse parsed = ResponseParser.parseAIResponse(response);

        assertNotNull(parsed);
        assertEquals("Obter ferro e fabricar uma picareta", parsed.getSummary());
        assertEquals(parsed.getSummary(), parsed.getPlan());
        assertEquals("", parsed.getReasoning());
    }

    @Test
    void rejectsOversizedOrStructurallyUnknownResponses() {
        String oversizedSummary = "x".repeat(161);

        assertNull(ResponseParser.parseAIResponse(
            "{\"summary\":\"" + oversizedSummary + "\",\"tasks\":[]}"));
        assertNull(ResponseParser.parseAIResponse(
            "{\"summary\":\"safe\",\"javaClass\":\"java.lang.Runtime\",\"tasks\":[]}"));
        assertNull(ResponseParser.parseAIResponse(
            "{\"summary\":\"safe\",\"tasks\":[{\"action\":\"mine\",\"parameters\":{},"
                + "\"command\":\"/op Steve\"}]}"));
    }

    @Test
    void rejectsPrivateReasoningAndAnySurroundingProse() {
        assertNull(ResponseParser.parseAIResponse(
            "Here is the plan: {\"summary\":\"safe\",\"tasks\":["
                + "{\"action\":\"mine\",\"parameters\":{\"block\":\"iron_ore\",\"quantity\":1}}]}"));
        assertNull(ResponseParser.parseAIResponse(
            "{\"summary\":\"safe\",\"tasks\":["
                + "{\"action\":\"mine\",\"parameters\":{\"block\":\"iron_ore\",\"quantity\":1}}]} trailing"));
        assertNull(ResponseParser.parseAIResponse("`````"));
    }

    @Test
    void ignoresUnknownTopLevelFieldsSuchAsReasoning() {
        String response = """
            {"reasoning":"private","decision":"act","summary":"safe","goalStatus":"in_progress",
             "tasks":[{"action":"mine","parameters":{"block":"iron_ore","quantity":1}}]}
            """;

        ResponseParser.ParsedResponse parsed = ResponseParser.parseAIResponse(response);

        assertNotNull(parsed);
        assertEquals("safe", parsed.getSummary());
        assertEquals(1, parsed.getTasks().size());
    }

    @Test
    void acceptsOnlyOneCompleteFenceAroundOneJsonObject() {
        String fenced = "```json\n{\"summary\":\"safe\",\"tasks\":["
            + "{\"action\":\"mine\",\"parameters\":{\"block\":\"iron_ore\",\"quantity\":1}}]}\n```";

        ResponseParser.ParsedResponse parsed = ResponseParser.parseAIResponse(fenced);

        assertNotNull(parsed);
        assertEquals("safe", parsed.getSummary());
        assertNull(ResponseParser.parseAIResponse("```json\n{}\n```\n```json\n{}\n```"));
    }

    @Test
    void rejectsTasksThatAreUnknownOrInvalidForTheRegisteredSchema() {
        assertNull(ResponseParser.parseAIResponse(
            "{\"summary\":\"unsafe\",\"tasks\":[{\"action\":\"unknown\",\"parameters\":{}}]}"));
        assertNull(ResponseParser.parseAIResponse(
            "{\"summary\":\"unsafe\",\"tasks\":[{\"action\":\"mine\","
                + "\"parameters\":{\"block\":\"iron_ore\",\"quantity\":0}}]}"));
    }
}
