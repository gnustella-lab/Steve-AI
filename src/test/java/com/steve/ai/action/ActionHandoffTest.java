package com.steve.ai.action;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionHandoffTest {

    @Test
    void startHandoffIsImmutableAndCarriesTheAcceptedTask() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("item", "iron_ingot");
        Task task = new Task("craft", parameters);

        ActionExecutor.ActionStart handoff = new ActionExecutor.ActionStart(task, "Craft iron");

        parameters.put("item", "diamond");
        assertEquals(task, handoff.task());
        assertEquals("iron_ingot", handoff.task().getStringParameter("item"));
        assertEquals("Craft iron", handoff.description());
        assertThrows(NullPointerException.class,
            () -> new ActionExecutor.ActionStart(null, "invalid"));
    }

    @Test
    void completionHandoffRejectsMissingEvidenceAndBoundsDescription() {
        ActionResult result = ActionResult.success("done")
            .observation("affected_item", "minecraft:iron_ingot")
            .observation("affected_quantity", 1)
            .build();
        ActionExecutor.ActionCompletion completion = new ActionExecutor.ActionCompletion(
            new Task("smelt", Map.of("item", "iron_ingot", "quantity", 1)), result,
            "x".repeat(2_000));

        assertEquals(512, completion.description().length());
        assertEquals(1, completion.result().getObservation("affected_quantity"));
        assertThrows(NullPointerException.class,
            () -> new ActionExecutor.ActionCompletion(null, result, "invalid"));
    }
}
