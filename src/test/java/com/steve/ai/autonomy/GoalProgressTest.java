package com.steve.ai.autonomy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalProgressTest {
    @Test
    void horizonStepCompletionDoesNotCountAsGoalEvidence() {
        GoalProgress progress = new GoalProgress();
        progress.recordStepCompletion(4, 4, 10L);

        assertTrue(progress.isStepPlanComplete());
        assertFalse(progress.isComplete());

        progress.record(4, 4, 11L);
        assertTrue(progress.isUnitTargetComplete());
        assertTrue(progress.isComplete());
    }
}
