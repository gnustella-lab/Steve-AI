package com.steve.ai.memory;

import com.steve.ai.autonomy.AgentGoal;
import com.steve.ai.autonomy.GoalOrigin;
import com.steve.ai.autonomy.GoalPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredMemoryTest {
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
}
