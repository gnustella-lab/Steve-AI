package com.steve.ai.autonomy;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGoalTest {
    @Test
    void preservesLifecycleProvenanceBudgetAndMetadataAcrossNbt() {
        UUID parent = UUID.randomUUID();
        AgentGoal goal = AgentGoal.create(
            "Get 16 iron ingots",
            GoalOrigin.USER,
            GoalPriority.USER,
            parent,
            123L);
        goal.putMetadata("requestedBy", "Livia");
        goal.setConstraints(GoalConstraints.forItem("iron_ingot", 16));
        goal.getBudget().recordLlmCall();
        goal.incrementAttempt();
        goal.incrementReplan();
        goal.activate(130L);
        goal.pause(140L);

        CompoundTag tag = goal.save();
        AgentGoal restored = AgentGoal.load(tag);

        assertEquals(goal.getId(), restored.getId());
        assertEquals("Get 16 iron ingots", restored.getDescription());
        assertEquals(GoalOrigin.USER, restored.getOrigin());
        assertEquals(GoalPriority.USER, restored.getPriority());
        assertEquals(GoalStatus.PAUSED, restored.getStatus());
        assertEquals(parent, restored.getParentGoalId());
        assertEquals("Livia", restored.getMetadata().get("requestedBy"));
        assertEquals("iron_ingot", restored.getConstraints().targetItem());
        assertEquals(16, restored.getConstraints().targetQuantity());
        assertEquals(1, restored.getBudget().getLlmCalls());
        assertEquals(1, restored.getAttemptCount());
        assertEquals(1, restored.getReplanCount());
    }

    @Test
    void terminalGoalsCannotBeResumed() {
        AgentGoal goal = AgentGoal.create("test", GoalOrigin.USER, GoalPriority.USER, null, 1L);
        goal.complete(2L);

        assertTrue(goal.isTerminal());
        assertFalse(goal.activate(3L));
        assertEquals(GoalStatus.COMPLETED, goal.getStatus());
    }
}
