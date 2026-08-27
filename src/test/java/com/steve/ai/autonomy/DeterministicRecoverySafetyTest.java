package com.steve.ai.autonomy;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicRecoverySafetyTest {

    @Test
    void resourceEvidenceWinsOverGenericRequiresReplanning() {
        AgentGoal goal = AgentGoal.create("Craft a pickaxe", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        Task craft = new Task("craft", Map.of("item", "iron_pickaxe", "quantity", 1));
        ActionResult failure = ActionResult.failure(ActionResult.ERROR_RESOURCE, "missing ingredients")
            .retryable(true)
            .requiresReplanning(true)
            .observation("missing_item", "oak_log")
            .observation("missing_quantity", 2)
            .build();

        RecoveryDecision decision = new RecoveryEngine().decide(goal, craft, failure,
            new FailureTracker(2), new BlockPos(0, 64, 0));

        assertEquals(RecoveryDecision.Kind.PREREQUISITE, decision.kind());
        assertTrue(decision.prerequisiteDescription().contains("oak_log"));
    }

    @Test
    void nullResourceEvidenceDoesNotCrashOrCreateNullMetadata() {
        AgentGoal goal = AgentGoal.create("Craft a pickaxe", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        ActionResult failure = ActionResult.failure(ActionResult.ERROR_RESOURCE, "missing ingredients")
            .requiresReplanning(true)
            .observation("missing_item", null)
            .observation("missing_quantity", null)
            .build();

        RecoveryDecision decision = assertDoesNotThrow(() -> new RecoveryEngine().decide(goal,
            new Task("craft", Map.of("item", "iron_pickaxe", "quantity", 1)), failure,
            new FailureTracker(2), new BlockPos(0, 64, 0)));

        assertEquals(RecoveryDecision.Kind.PREREQUISITE, decision.kind());
        assertTrue(decision.metadata().values().stream().noneMatch(value -> value == null));
    }

    @Test
    void protectedFailureCarriesDeniedTargetCoordinates() {
        AgentGoal goal = AgentGoal.create("Build here", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        Task place = new Task("place", Map.of("block", "stone", "x", 11, "y", 65, "z", -4));
        ActionResult failure = ActionResult.failure(ActionResult.ERROR_PROTECTED, "protected")
            .requiresReplanning(true)
            .observation("x", 11)
            .observation("y", 65)
            .observation("z", -4)
            .build();

        RecoveryDecision decision = new RecoveryEngine().decide(goal, place, failure,
            new FailureTracker(2), new BlockPos(0, 64, 0));

        assertEquals(RecoveryDecision.Kind.REPLAN, decision.kind());
        assertEquals(11, decision.metadata().get("x"));
        assertEquals(65, decision.metadata().get("y"));
        assertEquals(-4, decision.metadata().get("z"));
        assertTrue(decision.reason().contains("11"));
    }

    @Test
    void nullTrackerBlocksInsteadOfFreshFingerprint() {
        AgentGoal goal = AgentGoal.create("Reach the cave", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        ActionResult failure = ActionResult.failure(ActionResult.ERROR_PATHING, "blocked")
            .retryable(true).build();

        RecoveryDecision decision = new RecoveryEngine().decide(goal,
            new Task("pathfind", Map.of("x", 1, "y", 64, "z", 1)), failure, null,
            new BlockPos(0, 64, 0));

        assertEquals(RecoveryDecision.Kind.BLOCKED, decision.kind());
        assertTrue(decision.reason().toLowerCase().contains("tracker"));
    }

    @Test
    void validationFailureIsBlockedBeforeGenericReplanning() {
        AgentGoal goal = AgentGoal.create("Do something", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        ActionResult failure = ActionResult.failure(ActionResult.ERROR_VALIDATION, "invalid task")
            .requiresReplanning(true)
            .build();

        RecoveryDecision decision = new RecoveryEngine().decide(goal,
            new Task("unknown", Map.of()), failure, new FailureTracker(2), null);

        assertEquals(RecoveryDecision.Kind.BLOCKED, decision.kind());
    }
}
