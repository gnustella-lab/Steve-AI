package com.steve.ai.action.actions;

import com.steve.ai.action.Task;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SayActionTest {
    @Test
    void sanitizeFlattensWhitespaceAndStripsFormattingCodes() {
        assertEquals("Vou buscar madeira.",
            SayAction.sanitize("  Vou   buscar\nmadeira.  "));
        assertEquals("oi", SayAction.sanitize("§c§loi"));
        assertEquals("oi", SayAction.sanitize("&aoi"));
        assertEquals("", SayAction.sanitize("   \n\t  "));
        assertEquals("", SayAction.sanitize(null));
    }

    @Test
    void sanitizeCapsLengthAndLeavesPunctuation() {
        String spoken = SayAction.sanitize("ok? " + "x".repeat(200));
        assertEquals(160, spoken.length());
        assertTrue(spoken.startsWith("ok? x"));
        assertFalse(spoken.contains("§"));
    }

    @Test
    void descriptionUsesSanitizedTextBeforeStart() {
        SayAction action = new SayAction(null, new Task("say", Map.of("text", "  hello\nthere  ")));
        assertEquals("Say: hello there", action.getDescription());
    }
}
