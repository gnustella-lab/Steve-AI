package com.steve.ai.memory;

import com.steve.ai.autonomy.AgentGoal;
import com.steve.ai.autonomy.GoalOrigin;
import com.steve.ai.autonomy.GoalPriority;
import com.steve.ai.autonomy.AutonomyMode;
import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.planning.Plan;
import com.steve.ai.planning.PlanStep;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredMemoryTest {
    @Test
    void persistsBoundedPlanCheckpointAndNullableModeOverride() {
        SteveMemory memory = new SteveMemory(null);
        AgentGoal goal = AgentGoal.create("Gather oak logs", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        memory.setActiveGoal(goal);
        Plan plan = new Plan(goal.getId(), goal.getDescription(), null, null, 3, 3, 3, 0, 2L);
        plan.loadHorizon(List.of(new Task("mine", Map.of("block", "oak_log"))),
            "gather", "initial", 3L);
        plan.markCurrentStepActive(4L);
        plan.recordCurrentStepResult(ActionResult.failure(ActionResult.ERROR_PATHING, "blocked").build(), 5L);
        memory.setActivePlan(plan);
        memory.setAutonomyModeOverride(AutonomyMode.PROACTIVE);

        CompoundTag tag = new CompoundTag();
        memory.saveToNBT(tag);
        SteveMemory restored = new SteveMemory(null);
        restored.loadFromNBT(tag);

        assertEquals(plan.getPlanId(), restored.getActivePlan().getPlanId());
        assertEquals(PlanStep.Status.FAILED, restored.getActivePlan().getCurrentStep().getStatus());
        assertEquals(AutonomyMode.PROACTIVE, restored.getAutonomyModeOverride());
    }

    @Test
    void fullLoadClearsStateMissingFromTheIncomingTag() {
        SteveMemory memory = new SteveMemory(null);
        AgentGoal goal = AgentGoal.create("old", GoalOrigin.USER, GoalPriority.USER, null, 1L);
        memory.setActiveGoal(goal);
        memory.rememberGoal(AgentGoal.create("pending", GoalOrigin.USER, GoalPriority.USER, null, 2L));
        memory.addAction("old action");
        memory.addEpisode(new EpisodicMemoryEntry(goal.getId(), "mine", "success", "old",
            "overworld", new BlockPos(1, 64, 1), 3L));
        memory.rememberWorldFact(WorldFact.resource("old", "overworld", new BlockPos(1, 64, 1), 3L, 1.0));
        memory.setAutonomyModeOverride(AutonomyMode.OFF);

        CompoundTag legacy = new CompoundTag();
        legacy.putString("CurrentGoal", "legacy command");
        memory.loadFromNBT(legacy);

        assertEquals("legacy command", memory.getCurrentGoal());
        assertEquals(0, memory.getPersistedGoals().size());
        assertEquals(0, memory.getRecentActions(10).size());
        assertEquals(0, memory.getEpisodes().size());
        assertEquals(0, memory.getWorldFacts().size());
        assertEquals(null, memory.getActivePlan());
        assertEquals(null, memory.getAutonomyModeOverride());
    }

    @Test
    void enhancedRecallFiltersTtlAndDimensionAndRanksMatchingTokensByProximity() {
        SteveMemory memory = new SteveMemory(null);
        memory.rememberWorldFact(WorldFact.resource("iron_ore", "overworld",
            new BlockPos(2, 64, 2), 90L, 0.8));
        memory.rememberWorldFact(WorldFact.resource("iron_ore", "overworld",
            new BlockPos(100, 64, 100), 90L, 1.0));
        memory.rememberWorldFact(new WorldFact(WorldFact.Kind.RESOURCE, "iron_ore", "overworld",
            new BlockPos(1, 64, 1), 1L, 1.0, 5L, Map.of()));
        memory.rememberWorldFact(WorldFact.resource("iron_ore", "nether",
            new BlockPos(1, 64, 1), 90L, 1.0));

        List<WorldFact> relevant = memory.getRelevantFacts("find iron ore", 10,
            100L, "overworld", new BlockPos(0, 64, 0));

        assertEquals(2, relevant.size());
        assertEquals(new BlockPos(2, 64, 2), relevant.get(0).position());
    }

    @Test
    void persistsActiveGoalEpisodesAndSpatialFactsWithHardBounds() {
        SteveMemory memory = new SteveMemory(null);
        AgentGoal goal = AgentGoal.create("Gather oak logs", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        memory.setActiveGoal(goal);
        AgentGoal pending = AgentGoal.create("Craft a pickaxe", GoalOrigin.PREREQUISITE,
            GoalPriority.PREREQUISITE, goal.getId(), 2L);
        memory.rememberGoal(pending);

        for (int i = 0; i < 200; i++) {
            memory.rememberWorldFact(WorldFact.resource(
                "oak_log", "minecraft:overworld", new BlockPos(i, 64, i), 10L + i, 0.8));
            memory.addEpisode(new EpisodicMemoryEntry(goal.getId(), "mine", "success",
                "mined", "minecraft:overworld", new BlockPos(i, 64, i), 10L + i));
        }

        CompoundTag tag = new CompoundTag();
        memory.saveToNBT(tag);
        SteveMemory restored = new SteveMemory(null);
        restored.loadFromNBT(tag);

        assertEquals(goal.getId(), restored.getActiveGoal().getId());
        assertEquals(1, restored.getPersistedGoals().size());
        assertTrue(restored.getWorldFacts().size() <= SteveMemory.MAX_WORLD_FACTS);
        assertTrue(restored.getEpisodes().size() <= SteveMemory.MAX_EPISODES);
        assertTrue(restored.getRelevantFacts("oak_log", 5).size() <= 5);
    }

    @Test
    void loadsLegacyStringGoalWithoutInventingExecutionState() {
        CompoundTag tag = new CompoundTag();
        tag.putString("CurrentGoal", "legacy command");
        SteveMemory memory = new SteveMemory(null);

        memory.loadFromNBT(tag);

        assertEquals("legacy command", memory.getCurrentGoal());
        assertTrue(memory.getActiveGoal() == null);
    }

    @Test
    void malformedAutonomyModeOverrideDoesNotEnableAutonomy() {
        CompoundTag tag = new CompoundTag();
        tag.putString("AutonomyModeOverride", "not-a-mode");
        SteveMemory garbage = new SteveMemory(null);
        garbage.loadFromNBT(tag);
        assertEquals(null, garbage.getAutonomyModeOverride());

        CompoundTag blank = new CompoundTag();
        blank.putString("AutonomyModeOverride", "");
        SteveMemory empty = new SteveMemory(null);
        empty.loadFromNBT(blank);
        assertEquals(null, empty.getAutonomyModeOverride());

        assertEquals(null, AutonomyMode.tryParse("PROACTIVEX"));
        assertEquals(AutonomyMode.OFF, AutonomyMode.tryParse("off"));
    }
}
