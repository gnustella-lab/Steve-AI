package com.steve.ai.llm;

import com.steve.ai.action.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResponseParserTest {

    @Test
    void parsesFencedJsonWithoutMutatingTextInsideStrings() {
        String response = """
            ```json
            {
              "reasoning": "compare } { literally",
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
        assertEquals("compare } { literally", parsed.getReasoning());
        assertEquals("Keep the original text", parsed.getPlan());
        assertEquals(1, parsed.getTasks().size());
        Task task = parsed.getTasks().get(0);
        assertEquals("mine", task.getAction());
        assertEquals("iron_ore", task.getStringParameter("block"));
        assertEquals(3, task.getIntParameter("quantity", -1));
    }
}
