package com.steve.ai.planning;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanStepTest {
    @Test
    void persistsStepStatusAttemptsAndLastResult() {
        PlanStep step = new PlanStep(new Task("mine", Map.of("block", "stone", "quantity", 3)));
        step.markActive();
        step.incrementAttempt();
        ActionResult result = ActionResult.failure(ActionResult.ERROR_PATHING, "blocked")
            .retryable(true)
            .requiresReplanning(true)
            .build();
        step.complete(result, 42L);

        PlanStep restored = PlanStep.load(step.save());

        assertEquals(step.getStepId(), restored.getStepId());
        assertEquals(PlanStep.Status.FAILED, restored.getStatus());
        assertEquals(1, restored.getAttempts());
        assertEquals(ActionResult.ERROR_PATHING, restored.getLastResult().getErrorCode());
        assertEquals("mine", restored.getTask().getAction());
    }

    @Test
    void successfulStepIsMarkedCompleted() {
        PlanStep step = new PlanStep(new Task("inspect_inventory", Map.of()));
        step.complete(ActionResult.success("done").build(), 5L);
        assertEquals(PlanStep.Status.COMPLETED, step.getStatus());
    }
}
