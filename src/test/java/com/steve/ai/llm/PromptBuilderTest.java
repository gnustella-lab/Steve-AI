package com.steve.ai.llm;

import com.steve.ai.autonomy.AgentGoal;
import com.steve.ai.autonomy.GoalOrigin;
import com.steve.ai.autonomy.GoalPriority;
import com.steve.ai.plugin.ActionCapability;
import com.steve.ai.plugin.ActionDescriptor;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.plugin.JsonSchema;
import com.steve.ai.security.ActionPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    @AfterEach
    void clearRegistry() {
        ActionRegistry.getInstance().clear();
    }

    @Test
    void buildsAvailableActionsFromRegistryMetadata() {
        ActionDescriptor descriptor = new ActionDescriptor(
            "dance",
            "Performs a harmless dance",
            "example-plugin",
            "1",
            ActionPermission.INTERACTION,
            JsonSchema.object().optionalInteger("seconds", 1, 30).build(),
            List.of("{\"action\":\"dance\",\"parameters\":{\"seconds\":5}}"),
            Set.of(ActionCapability.MOVEMENT));
        ActionRegistry.getInstance().register(descriptor, (steve, task, context) -> null, 0);

        String prompt = PromptBuilder.buildSystemPrompt();

        assertTrue(prompt.contains("dance"));
        assertTrue(prompt.contains("Performs a harmless dance"));
        assertTrue(prompt.contains("seconds"));
        assertTrue(prompt.contains("\"summary\""));
        assertFalse(prompt.contains("brief thought"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void excludesMetadataFreeLegacyActionsFromTheLlmCatalog() {
        ActionRegistry.getInstance().register("legacy_action", (steve, task, context) -> null);

        String prompt = PromptBuilder.buildSystemPrompt();

        assertFalse(prompt.contains("legacy_action"));
    }

    @Test
    void keepsPrimaryGoalAndActiveSubgoalDistinctWhileBoundingOperationalContext() {
        AgentGoal primary = AgentGoal.create("Build a protected storage room", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        AgentGoal subgoal = AgentGoal.create("Gather stone for the foundation", GoalOrigin.PREREQUISITE,
            GoalPriority.PREREQUISITE, primary.getId(), 2L);
        PlanningContext context = new PlanningContext(primary, subgoal,
            new com.steve.ai.perception.ObservationSnapshot.Builder()
                .currentGoal("wrong observation goal")
                .activeSubgoal("wrong observation subgoal")
                .build(),
            java.util.stream.IntStream.range(0, 40).mapToObj(index -> "memory-" + index + " ".repeat(500)).toList(),
            java.util.List.of("stone gathered"), "none", java.util.List.of(), 4, 3, 2);

        String prompt = PromptBuilder.buildPlanningPrompt(context);

        assertTrue(prompt.length() <= 16_000);
        assertTrue(prompt.indexOf("=== PRIMARY GOAL ===") < prompt.indexOf("=== ACTIVE SUBGOAL ==="));
        assertTrue(prompt.contains("Build a protected storage room"));
        assertTrue(prompt.contains("Gather stone for the foundation"));
        assertTrue(prompt.contains("server-authorized"));
        assertTrue(prompt.contains("horizon=4, llmCalls=3, replans=2"));
        assertFalse(prompt.contains("chain-of-thought"));
        assertFalse(prompt.contains("reasoning"));
    }
}
