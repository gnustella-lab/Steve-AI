package com.steve.ai.autonomy;

import com.steve.ai.action.ActionResult;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoalEvaluatorTest {
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
                ActionResult.success("delivered").observation("delivered", true).build(), true).status());
    }
}
