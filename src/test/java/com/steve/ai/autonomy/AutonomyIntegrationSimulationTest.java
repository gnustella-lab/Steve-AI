package com.steve.ai.autonomy;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.llm.PlanningContext;
import com.steve.ai.llm.ResponseParser;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.plugin.CoreActionsPlugin;
import com.steve.ai.di.SimpleServiceContainer;
import com.steve.ai.perception.ObservationSnapshot;
import com.steve.ai.planning.Plan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Simulates the executive boundary with a sequential fake planner, without provider calls. */
class AutonomyIntegrationSimulationTest {
    @BeforeEach
    void registerActions() {
        ActionRegistry.getInstance().clear();
        new CoreActionsPlugin().onLoad(ActionRegistry.getInstance(), new SimpleServiceContainer());
    }

    @AfterEach
    void clearActions() {
        ActionRegistry.getInstance().clear();
    }

    @Test
    void failureObservationRecoveryAndReplanContinueWithoutAnotherUserCommand() {
        ResponseParser.ParsedResponse first = parse("""
            {"decision":"act","summary":"Try the first route","goalStatus":"in_progress",
             "tasks":[{"action":"pathfind","parameters":{"x":10,"y":64,"z":10}}]}
            """);
        ResponseParser.ParsedResponse second = parse("""
            {"decision":"act","summary":"Use the alternate route","goalStatus":"in_progress",
             "tasks":[{"action":"search_resource","parameters":{"resource":"iron_ore","maxDistance":16}}]}
            """);
        FakePlanner planner = new FakePlanner(first, second);
        AgentGoal goal = AgentGoal.create("Get iron", GoalOrigin.USER, GoalPriority.USER, null, 1L);
        FailureTracker failures = new FailureTracker(2);
        RecoveryEngine recovery = new RecoveryEngine();
        GoalEvaluator evaluator = new GoalEvaluator();

        PlanningContext context = context(goal);
        Plan firstPlan = new Plan(goal.getId(), goal.getDescription(), null, null, 3, 4, 4, 0, 1);
        firstPlan.loadHorizon(planner.plan(context).join().getTasks(), "first", "initial", 1);
        ActionResult pathFailure = ActionResult.failure(ActionResult.ERROR_PATHING, "route blocked")
            .retryable(true).requiresReplanning(true).build();
        RecoveryDecision decision = recovery.decide(goal, firstPlan.getCurrentTask(), pathFailure,
            failures, new net.minecraft.core.BlockPos(0, 64, 0));
        assertEquals(RecoveryDecision.Kind.REPLAN, decision.kind());

        goal.incrementReplan();
        Plan secondPlan = new Plan(goal.getId(), goal.getDescription(), null, null, 3, 4, 4, 0, 2);
        secondPlan.loadHorizon(planner.plan(context).join().getTasks(), "second", "pathing failure", 2);
        ActionResult success = ActionResult.success("resource found").build();
        GoalEvaluator.Evaluation evaluation = evaluator.evaluate(goal, Map.of(), null, success, true);

        assertEquals(2, planner.calls);
        assertEquals("search_resource", secondPlan.getCurrentTask().getAction());
        assertEquals(GoalEvaluator.Status.COMPLETE, evaluation.status());
        assertTrue(failures.failedApproaches().size() == 1);
    }

    private static PlanningContext context(AgentGoal goal) {
        return new PlanningContext(goal, null, new ObservationSnapshot.Builder().build(),
            List.of(), List.of(), "", List.of(), 4, 3, 3);
    }

    private static ResponseParser.ParsedResponse parse(String json) {
        return ResponseParser.parseAIResponse(json);
    }

    private static final class FakePlanner {
        private final Deque<ResponseParser.ParsedResponse> responses = new ArrayDeque<>();
        private int calls;

        private FakePlanner(ResponseParser.ParsedResponse... responses) {
            this.responses.addAll(List.of(responses));
        }

        private CompletableFuture<ResponseParser.ParsedResponse> plan(PlanningContext ignored) {
            calls++;
            return CompletableFuture.completedFuture(responses.removeFirst());
        }
    }
}
