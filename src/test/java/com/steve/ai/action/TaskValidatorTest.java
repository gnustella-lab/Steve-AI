package com.steve.ai.action;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskValidatorTest {

    @Test
    void rejectsMalformedOrDangerouslyLargeTasks() {
        assertFalse(TaskValidator.isValid(new Task("mine", Map.of("quantity", 10))));
        assertFalse(TaskValidator.isValid(new Task("mine", Map.of("block", "iron_ore", "quantity", 0))));
        assertFalse(TaskValidator.isValid(new Task("place", Map.of(
            "block", "stone", "x", "not-a-number", "y", 64, "z", 0))));
        assertFalse(TaskValidator.isValid(new Task("build", Map.of(
            "structure", "house", "dimensions", List.of(1_000_000, 20, 1_000_000)))));
        assertFalse(TaskValidator.isValid(new Task("build", Map.of(
            "structure", "house", "width", 64, "height", 64, "depth", 64))));
        assertFalse(TaskValidator.isValid(new Task("unknown", Map.of())));
        assertFalse(TaskValidator.isValid(new Task("craft", Map.of(
            "item", "crafting_table", "quantity", 1))));
    }

    @Test
    void acceptsSupportedTasksWithSafeParameters() {
        assertTrue(TaskValidator.isValid(new Task("mine", Map.of(
            "block", "iron_ore", "quantity", 10))));
        assertTrue(TaskValidator.isValid(new Task("build", Map.of(
            "structure", "house",
            "blocks", List.of("oak_planks", "cobblestone"),
            "dimensions", List.of(9, 6, 9)))));
        assertTrue(TaskValidator.isValid(new Task("follow", Map.of("player", "me"))));
    }
}
