package com.steve.ai.autonomy;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryEngineTest {
    @Test
    void houseMaterialsBecomeCraftPrerequisiteRatherThanMiningPlanks() {
        Task build = new Task("build", Map.of("structure", "house"));
        ActionResult dependency = ActionResult.failure(ActionResult.ERROR_RESOURCE, "Missing planks")
            .retryable(true)
            .observation("missing_item", "minecraft:oak_planks")
            .observation("required_action", "craft")
            .observation("required_item", "minecraft:oak_planks")
            .observation("required_quantity", 180).build();
        RecoveryDecision decision = new RecoveryEngine().decide(null, build, dependency,
            new FailureTracker(2), BlockPos.ZERO);
        assertEquals("Craft 180 minecraft:oak_planks", decision.prerequisiteDescription());
    }

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

    @Test
    void protectedFailureHonorsReplanBudgetAndFingerprint() {
        AgentGoal exhausted = AgentGoal.create("Build here", GoalOrigin.USER,
            GoalPriority.USER, null, 1L, GoalBudget.fromConfiguredLimits(3, 0, 12, 5, 2));
        Task place = new Task("place", Map.of("block", "stone", "x", 1, "y", 64, "z", 1));
        ActionResult failure = ActionResult.failure(ActionResult.ERROR_PROTECTED, "protected")
            .requiresReplanning(true).build();

        RecoveryDecision budgeted = new RecoveryEngine().decide(exhausted, place, failure,
            new FailureTracker(2), new BlockPos(1, 64, 1));
        assertEquals(RecoveryDecision.Kind.BLOCKED, budgeted.kind());

        AgentGoal repeating = AgentGoal.create("Build here", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        FailureTracker tracker = new FailureTracker(2);
        RecoveryEngine engine = new RecoveryEngine();
        engine.decide(repeating, place, failure, tracker, new BlockPos(1, 64, 1));
        engine.decide(repeating, place, failure, tracker, new BlockPos(1, 64, 1));
        RecoveryDecision third = engine.decide(repeating, place, failure, tracker,
            new BlockPos(1, 64, 1));
        assertEquals(RecoveryDecision.Kind.BLOCKED, third.kind());
    }

    @Test
    void missingCombatTargetRequestsFreshObservationInsteadOfSuccess() {
        AgentGoal goal = AgentGoal.create("Attack that creeper", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        Task combat = new Task("combat", Map.of("target", "creeper"));
        ActionResult missing = ActionResult.failure(ActionResult.ERROR_TARGET_NOT_FOUND,
            "No compatible combat target was found nearby")
            .retryable(true).requiresReplanning(true)
            .observation("targetsSeen", 0).build();

        RecoveryDecision decision = new RecoveryEngine().decide(goal, combat, missing,
            new FailureTracker(2), BlockPos.ZERO);

        assertEquals(RecoveryDecision.Kind.REPLAN, decision.kind());
    }

    @Test
    void cookingDependencyBecomesDeterministicSmeltPrerequisite() {
        AgentGoal goal = AgentGoal.create("Craft an iron pickaxe", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        Task craft = new Task("craft", Map.of("item", "iron_pickaxe", "quantity", 1));
        ActionResult dependency = ActionResult.failure(ActionResult.ERROR_RESOURCE,
            "Crafting dependency requires smelting")
            .retryable(true)
            .observation("required_action", "smelt")
            .observation("required_item", "minecraft:iron_ingot")
            .observation("required_quantity", 3)
            .build();

        RecoveryDecision decision = new RecoveryEngine().decide(goal, craft, dependency,
            new FailureTracker(2), BlockPos.ZERO);

        assertEquals(RecoveryDecision.Kind.PREREQUISITE, decision.kind());
        assertEquals("Smelt 3 minecraft:iron_ingot", decision.prerequisiteDescription());
        GoalConstraints constraints = GoalConstraints.fromDescription(decision.prerequisiteDescription());
        assertEquals("minecraft:iron_ingot", constraints.targetItem());
        assertEquals(3, constraints.targetQuantity());
    }
}
