package com.steve.ai.autonomy;

import com.steve.ai.action.ActionResult;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoalEvaluatorTest {
    @Test
    void localCompoundRequiresBothVerifiedStepsNotJustBuildSuccess() {
        AgentGoal goal = AgentGoal.create("build house and craft stick", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        goal.putMetadata("localCompound", true);
        goal.putMetadata("localCompletedSteps", 1);
        var evaluator = new GoalEvaluator();
        var build = ActionResult.success("built").observation("actionType", "build").build();
        assertEquals(GoalEvaluator.Status.IN_PROGRESS,
            evaluator.evaluate(goal, Map.of(), null, build, true).status());
        goal.putMetadata("localCompletedSteps", 2);
        assertEquals(GoalEvaluator.Status.COMPLETE,
            evaluator.evaluate(goal, Map.of(), null, null, true).status());
    }

    @Test
    void deterministicItemGoalStaysInProgressUntilQuantityExists() {
        AgentGoal goal = AgentGoal.create("Get 16 iron ingots", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        goal.setConstraints(GoalConstraints.forItem("iron_ingot", 16));
        GoalEvaluator evaluator = new GoalEvaluator();

        assertEquals(GoalEvaluator.Status.IN_PROGRESS,
            evaluator.evaluate(goal, Map.of("iron_ingot", 8), null, null, true).status());
        assertEquals(GoalEvaluator.Status.COMPLETE,
            evaluator.evaluate(goal, Map.of("iron_ingot", 16), null,
                ActionResult.success("done").build(), true).status());
    }

    @Test
    void positionAndDeliveryConditionsNeedTheirObservedProof() {
        AgentGoal positionGoal = AgentGoal.create("Go to the base", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        positionGoal.setConstraints(GoalConstraints.empty().withTargetPosition(new BlockPos(10, 64, 10), 2));
        GoalEvaluator evaluator = new GoalEvaluator();

        assertEquals(GoalEvaluator.Status.COMPLETE,
            evaluator.evaluate(positionGoal, Map.of(), new BlockPos(11, 64, 10), null, true).status());

        AgentGoal delivery = AgentGoal.create("Give Alex 4 bread", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        delivery.setConstraints(GoalConstraints.forItem("bread", 4).withDelivery(null));
        assertEquals(GoalEvaluator.Status.IN_PROGRESS,
            evaluator.evaluate(delivery, Map.of("bread", 4), null, null, true).status());
        assertEquals(GoalEvaluator.Status.COMPLETE,
            evaluator.evaluate(delivery, Map.of(), null,
                ActionResult.success("delivered")
                    .observation("delivered", true)
                    .observation("deliveredItem", "bread")
                    .observation("deliveredQuantity", 4)
                    .build(), true).status());
    }

    @Test
    void compoundPositionAndItemGoalRequiresBothConditions() {
        AgentGoal goal = AgentGoal.create("Bring 4 bread to base", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        goal.setConstraints(GoalConstraints.forItem("bread", 4)
            .withTargetPosition(new BlockPos(10, 64, 10), 2));
        GoalEvaluator evaluator = new GoalEvaluator();

        assertEquals(GoalEvaluator.Status.IN_PROGRESS,
            evaluator.evaluate(goal, Map.of("bread", 0), new BlockPos(10, 64, 10), null, true).status());
        assertEquals(GoalEvaluator.Status.IN_PROGRESS,
            evaluator.evaluate(goal, Map.of("bread", 4), new BlockPos(20, 64, 20), null, true).status());
        assertEquals(GoalEvaluator.Status.COMPLETE,
            evaluator.evaluate(goal, Map.of("bread", 4), new BlockPos(10, 64, 10), null, true).status());
    }

    @Test
    void genericSuccessfulActionCannotCompleteAnUnconstrainedGoal() {
        AgentGoal goal = AgentGoal.create("Inspect the area", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);

        GoalEvaluator.Evaluation evaluation = new GoalEvaluator().evaluate(
            goal, Map.of(), null, ActionResult.success("done").build(), true);

        assertEquals(GoalEvaluator.Status.IN_PROGRESS, evaluation.status());
    }

    @Test
    void combatGoalRequiresStructuredKillEvidence() {
        AgentGoal goal = AgentGoal.create("Attack that creeper", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        GoalEvaluator evaluator = new GoalEvaluator();

        ActionResult noProof = ActionResult.success("Combat complete")
            .observation("actionType", "combat")
            .observation("targetsKilled", 0)
            .build();
        assertEquals(GoalEvaluator.Status.IN_PROGRESS,
            evaluator.evaluate(goal, Map.of(), null, noProof, true).status());

        ActionResult killed = ActionResult.success("Target defeated")
            .observation("actionType", "combat")
            .observation("targetsSeen", 1)
            .observation("targetsEngaged", 1)
            .observation("targetsKilled", 1)
            .build();
        assertEquals(GoalEvaluator.Status.COMPLETE,
            evaluator.evaluate(goal, Map.of(), null, killed, true).status());

        ActionResult timeout = ActionResult.failure(ActionResult.ERROR_TIMEOUT, "Combat timed out")
            .observation("actionType", "combat")
            .observation("targetsSeen", 1)
            .observation("targetsEngaged", 1)
            .observation("targetsKilled", 0)
            .build();
        assertEquals(GoalEvaluator.Status.IN_PROGRESS,
            evaluator.evaluate(goal, Map.of(), null, timeout, true).status());

        ActionResult missing = ActionResult.failure(ActionResult.ERROR_TARGET_NOT_FOUND, "No creeper")
            .observation("actionType", "combat")
            .observation("targetsKilled", 0)
            .build();
        assertEquals(GoalEvaluator.Status.IN_PROGRESS,
            evaluator.evaluate(goal, Map.of(), null, missing, true).status());
    }

    @Test
    void buildHouseGoalCompletesOnlyWithStructuredBuildEvidence() {
        AgentGoal goal = AgentGoal.create("construir uma casa", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        GoalEvaluator evaluator = new GoalEvaluator();

        assertEquals(GoalEvaluator.Status.IN_PROGRESS,
            evaluator.evaluate(goal, Map.of(), null, ActionResult.success("done").build(), true).status());
        assertEquals(GoalEvaluator.Status.COMPLETE,
            evaluator.evaluate(goal, Map.of(), null,
                ActionResult.success("Built house")
                    .observation("actionType", "build")
                    .observation("structure", "house")
                    .build(), true).status());
    }

    @Test
    void quantitativeCombatGoalDoesNotCompleteAfterOneKill() {
        AgentGoal goal = AgentGoal.create("Kill 10 zombies", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        goal.setConstraints(GoalConstraints.fromDescription(goal.getDescription()));
        GoalEvaluator evaluator = new GoalEvaluator();
        ActionResult oneKill = ActionResult.success("One zombie defeated")
            .observation("actionType", "combat")
            .observation("targetType", "minecraft:zombie")
            .observation("targetsKilled", 1).build();

        assertEquals(10, goal.getConstraints().targetQuantity());
        assertEquals("zombie", goal.getConstraints().targetBlock());
        assertEquals(GoalEvaluator.Status.IN_PROGRESS,
            evaluator.evaluate(goal, Map.of(), null, oneKill, true).status());

        goal.getProgress().record(10, 10, 20L);
        assertEquals(GoalEvaluator.Status.COMPLETE,
            evaluator.evaluate(goal, Map.of(), null, oneKill, true).status());
    }
}
