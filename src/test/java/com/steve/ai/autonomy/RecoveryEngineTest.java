package com.steve.ai.autonomy;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryEngineTest {
    @Test
    void deterministicPolicyCreatesPrerequisiteForMissingResource() {
        AgentGoal goal = AgentGoal.create("Craft an iron pickaxe", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        Task craft = new Task("craft", Map.of("item", "iron_pickaxe", "quantity", 1));
        ActionResult failure = ActionResult.failure(ActionResult.ERROR_RESOURCE, "Missing ingredients")
            .retryable(true)
            .observation("missing_item", "oak_log")
            .observation("missing_quantity", 2)
            .build();

        RecoveryDecision decision = new RecoveryEngine().decide(goal, craft, failure,
            new FailureTracker(2), new BlockPos(10, 64, 10));

        assertEquals(RecoveryDecision.Kind.PREREQUISITE, decision.kind());
        assertTrue(decision.prerequisiteDescription().contains("oak_log"));
    }

    @Test
    void repeatedPathingFingerprintIsBlockedInsteadOfLooping() {
        AgentGoal goal = AgentGoal.create("Reach the cave", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        Task path = new Task("pathfind", Map.of("x", 10, "y", 64, "z", 10));
        ActionResult failure = ActionResult.failure(ActionResult.ERROR_PATHING, "blocked")
            .retryable(true).build();
        FailureTracker tracker = new FailureTracker(2);
        RecoveryEngine engine = new RecoveryEngine();

        engine.decide(goal, path, failure, tracker, new BlockPos(0, 64, 0));
        RecoveryDecision second = engine.decide(goal, path, failure, tracker, new BlockPos(0, 64, 0));
        RecoveryDecision third = engine.decide(goal, path, failure, tracker, new BlockPos(0, 64, 0));

        assertEquals(RecoveryDecision.Kind.RETRY, second.kind());
        assertEquals(RecoveryDecision.Kind.BLOCKED, third.kind());
    }

    @Test
    void protectedFailureRequiresAReplanAndNeverABypass() {
        AgentGoal goal = AgentGoal.create("Build here", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        Task place = new Task("place", Map.of("block", "stone", "x", 1, "y", 64, "z", 1));
        ActionResult failure = ActionResult.failure(ActionResult.ERROR_PROTECTED, "protected")
            .requiresReplanning(true).build();

        RecoveryDecision decision = new RecoveryEngine().decide(goal, place, failure,
            new FailureTracker(2), new BlockPos(1, 64, 1));

        assertEquals(RecoveryDecision.Kind.REPLAN, decision.kind());
        assertTrue(decision.reason().contains("protected"));
    }
}
