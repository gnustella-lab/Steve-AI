package com.steve.ai.autonomy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalConstraintsParsingTest {
    @Test
    void extractsItemQuantityFromNaturalLanguageGoal() {
        GoalConstraints constraints = GoalConstraints.fromDescription("Get me 16 iron ingots.");
        assertEquals("iron_ingot", constraints.targetItem());
        assertEquals(16, constraints.targetQuantity());
    }

    @Test
    void extractsResourceAndQuantityForGatherGoal() {
        GoalConstraints constraints = GoalConstraints.fromDescription("Gather 32 oak logs");
        assertEquals("oak_log", constraints.targetItem());
        assertEquals(32, constraints.targetQuantity());
    }
}
