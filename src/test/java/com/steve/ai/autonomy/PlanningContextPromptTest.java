package com.steve.ai.autonomy;

import com.steve.ai.llm.PlanningContext;
import com.steve.ai.llm.PromptBuilder;
import com.steve.ai.perception.ObservationSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningContextPromptTest {
    @Test
    void keepsAutonomousContextStableAndBounded() {
        AgentGoal goal = AgentGoal.create("Get 16 iron ingots", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        ObservationSnapshot observation = new ObservationSnapshot.Builder()
            .x(10).y(64).z(20).dimension("overworld")
            .inventorySummary("- oak_log: 12")
            .currentGoal(goal.getDescription())
            .build();
        PlanningContext context = new PlanningContext(goal, null, observation,
            List.of("resource:iron_ore@[0, 12, 0]"),
            List.of("gather oak_log completed"),
            "pathing: route blocked", List.of("mine at [10,64,20]"),
            4, 5, 2);

        String prompt = PromptBuilder.buildPlanningPrompt(context);

        assertTrue(prompt.contains("PRIMARY GOAL"));
        assertTrue(prompt.contains("CURRENT OBSERVATION"));
        assertTrue(prompt.contains("RELEVANT MEMORY"));
        assertTrue(prompt.contains("FAILED APPROACHES"));
        assertTrue(prompt.contains("REMAINING BUDGET"));
        assertTrue(prompt.contains("Get 16 iron ingots"));
        assertTrue(prompt.length() < 16_000);
    }
}
