package com.steve.ai.perception;

import com.steve.ai.memory.WorldFact;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationServiceTest {
    @Test
    void selectsRelevantFactsByTokensDimensionTimeAndProximity() {
        WorldFact nearbyIron = new WorldFact(WorldFact.Kind.RESOURCE, "iron_ore", "overworld",
            new BlockPos(2, 64, 2), 90, 0.8, 100, Map.of());
        WorldFact farIron = new WorldFact(WorldFact.Kind.RESOURCE, "iron_ore", "overworld",
            new BlockPos(200, 64, 200), 99, 1.0, 100, Map.of());
        WorldFact otherDimension = new WorldFact(WorldFact.Kind.RESOURCE, "iron_ore", "nether",
            new BlockPos(1, 64, 1), 99, 1.0, 100, Map.of());
        WorldFact expired = new WorldFact(WorldFact.Kind.RESOURCE, "iron_ore", "overworld",
            new BlockPos(1, 64, 1), 1, 1.0, 10, Map.of());
        WorldFact station = new WorldFact(WorldFact.Kind.CRAFTING_STATION, "crafting_table", "overworld",
            new BlockPos(3, 64, 3), 99, 0.7, 100, Map.of("purpose", "craft tools"));

        List<WorldFact> selected = ObservationService.selectRelevantFacts(
            List.of(farIron, otherDimension, expired, station, nearbyIron),
            "mine iron ore", "overworld", new BlockPos(0, 64, 0), 100, 3);

        assertEquals(List.of(nearbyIron, farIron, station), selected);
        assertTrue(selected.stream().noneMatch(fact -> fact.dimension().equals("nether")));
    }

    @Test
    void observationClockMatchesExecutiveTickCount() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/steve/ai/perception/ObservationService.java"));
        assertTrue(source.contains("getTickCount()"));
        assertFalse(source.contains("getGameTime()"));
    }

    @Test
    void ttlUsesSameClockAsFactWriters() {
        WorldFact recent = WorldFact.resource("iron_ore", "overworld",
            new BlockPos(2, 64, 2), 1_000L, 1.0);

        List<WorldFact> selected = ObservationService.selectRelevantFacts(
            List.of(recent), "iron ore", "overworld", new BlockPos(0, 64, 0), 1_100L, 8);

        assertEquals(List.of(recent), selected);
        assertTrue(ObservationService.selectRelevantFacts(
            List.of(recent), "iron ore", "overworld", new BlockPos(0, 64, 0), 30_000L, 8)
            .isEmpty());
    }
}
