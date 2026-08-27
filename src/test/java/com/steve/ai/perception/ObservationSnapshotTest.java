package com.steve.ai.perception;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ObservationSnapshotTest {

    @Test
    public void testBuilderDefaults() {
        ObservationSnapshot snapshot = new ObservationSnapshot.Builder().build();

        assertEquals(0, snapshot.getX());
        assertEquals("unknown", snapshot.getDimension());
        assertEquals(20.0f, snapshot.getHealth());
        assertEquals("", snapshot.getInventorySummary());
        assertTrue(snapshot.getNearbyPlayers().isEmpty());
    }

    @Test
    public void testEquality() {
        ObservationSnapshot snapshot1 = new ObservationSnapshot.Builder()
            .x(10).y(64).z(20)
            .dimension("overworld")
            .biome("plains")
            .health(15.0f)
            .build();

        ObservationSnapshot snapshot2 = new ObservationSnapshot.Builder()
            .x(10).y(64).z(20)
            .dimension("overworld")
            .biome("plains")
            .health(15.0f)
            .build();

        ObservationSnapshot snapshot3 = new ObservationSnapshot.Builder()
            .x(11).y(64).z(20)
            .build();

        assertEquals(snapshot1, snapshot2);
        assertNotEquals(snapshot1, snapshot3);
        assertEquals(snapshot1.hashCode(), snapshot2.hashCode());
    }

    @Test
    public void testToPromptContextFormat() {
        ObservationSnapshot snapshot = new ObservationSnapshot.Builder()
            .x(10).y(64).z(20)
            .dimension("overworld")
            .biome("plains")
            .dayTime(48000) // Day 3
            .isNight(false)
            .lightLevel(15)
            .health(20.0f)
            .maxHealth(20.0f)
            .inventorySummary("- oak_log: 32\n- cobblestone: 64\n- iron_pickaxe: 1, durability 71%")
            .equipmentSummary("main_hand: iron_pickaxe")
            .nearbyPlayers(List.of("Steve (5 blocks)"))
            .nearbyThreats(List.of("zombie (8 blocks)"))
            .currentGoal("Mine 32 iron ore")
            .recentActions(List.of("Mined 4 iron_ore", "Equipped iron_pickaxe"))
            .build();

        String prompt = snapshot.toPromptContext();
        
        // Assert token efficiency
        assertTrue(prompt.length() < 500, "Prompt context should be under 500 chars for a typical case");

        // Assert content
        assertTrue(prompt.contains("Position: [10, 64, 20] in overworld"));
        assertTrue(prompt.contains("Time: Day 3, morning | Biome: plains | Light: 15"));
        assertTrue(prompt.contains("Health: 20/20"));
        assertTrue(prompt.contains("Inventory:\n- oak_log: 32"));
        assertTrue(prompt.contains("Equipment: main_hand: iron_pickaxe"));
        assertTrue(prompt.contains("Nearby players: Steve (5 blocks)"));
        assertTrue(prompt.contains("Nearby threats: zombie (8 blocks)"));
        assertTrue(prompt.contains("Goal: Mine 32 iron ore"));
        assertTrue(prompt.contains("Recent: Mined 4 iron_ore, Equipped iron_pickaxe"));
        
        // Expected string format
        String expected = """
            Position: [10, 64, 20] in overworld
            Time: Day 3, morning | Biome: plains | Light: 15
            Health: 20/20
            Inventory:
            - oak_log: 32
            - cobblestone: 64
            - iron_pickaxe: 1, durability 71%
            Equipment: main_hand: iron_pickaxe
            Nearby players: Steve (5 blocks)
            Nearby threats: zombie (8 blocks)
            Goal: Mine 32 iron ore
            Recent: Mined 4 iron_ore, Equipped iron_pickaxe""".trim();
            
        assertEquals(expected, prompt);
    }
    
    @Test
    public void testToPromptContextEmptyLists() {
        ObservationSnapshot snapshot = new ObservationSnapshot.Builder()
            .x(0).y(0).z(0)
            .dimension("overworld")
            .biome("plains")
            .build();
            
        String prompt = snapshot.toPromptContext();
        
        // Shouldn't contain empty sections if not relevant
        assertFalse(prompt.contains("Nearby players:"));
        assertFalse(prompt.contains("Nearby threats:"));
        assertFalse(prompt.contains("Goal:"));
        assertFalse(prompt.contains("Recent:"));
        
        assertTrue(prompt.contains("Inventory:\n- empty"));
    }

    @Test
    public void boundsAndCopiesImportantPositionedObservations() {
        List<String> oversized = java.util.stream.IntStream.range(0, 40)
            .mapToObj(index -> "resource@[" + index + ",64,0]")
            .toList();
        String hugeInventory = "inventory ".repeat(200);

        ObservationSnapshot snapshot = new ObservationSnapshot.Builder()
            .inventorySummary(hugeInventory)
            .nearbyResources(oversized)
            .nearbyStations(oversized)
            .nearbyContainers(oversized)
            .nearbyHazards(oversized)
            .build();

        assertEquals(ObservationSnapshot.MAX_POSITIONED_OBSERVATIONS, snapshot.getNearbyResources().size());
        assertEquals(ObservationSnapshot.MAX_POSITIONED_OBSERVATIONS, snapshot.getNearbyStations().size());
        assertEquals(ObservationSnapshot.MAX_POSITIONED_OBSERVATIONS, snapshot.getNearbyContainers().size());
        assertEquals(ObservationSnapshot.MAX_POSITIONED_OBSERVATIONS, snapshot.getNearbyHazards().size());
        assertTrue(snapshot.getInventorySummary().length() <= ObservationSnapshot.MAX_FIELD_LENGTH);
        assertTrue(snapshot.toPromptContext().length() <= ObservationSnapshot.MAX_PROMPT_CONTEXT_LENGTH);
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.getNearbyResources().add("mutated"));
    }

    @Test
    public void promptIncludesPositionedObservationSections() {
        ObservationSnapshot snapshot = new ObservationSnapshot.Builder()
            .nearbyResources(List.of("iron_ore@[11,63,20]"))
            .nearbyStations(List.of("crafting_table@[12,64,20]"))
            .nearbyContainers(List.of("chest@[13,64,20]"))
            .nearbyHazards(List.of("lava@[14,63,20]"))
            .build();

        String prompt = snapshot.toPromptContext();

        assertTrue(prompt.contains("Nearby resources: iron_ore@[11,63,20]"));
        assertTrue(prompt.contains("Nearby stations: crafting_table@[12,64,20]"));
        assertTrue(prompt.contains("Nearby containers: chest@[13,64,20]"));
        assertTrue(prompt.contains("Nearby hazards: lava@[14,63,20]"));
    }

    @Test
    public void worldKnowledgeClampsSamplingAndExposesBoundedPositionCategories() {
        com.steve.ai.memory.WorldKnowledge knowledge =
            new com.steve.ai.memory.WorldKnowledge(null, 10_000, 10_000_000);

        assertEquals(32, knowledge.getScanRadius());
        assertEquals(2_048, knowledge.getMaxBlockSamples());
        assertTrue(knowledge.getNearbyBlockPositions().isEmpty());
        assertTrue(knowledge.getNearbyResourcePositions().isEmpty());
        assertTrue(knowledge.getNearbyStationPositions().isEmpty());
        assertTrue(knowledge.getNearbyContainerPositions().isEmpty());
        assertTrue(knowledge.getNearbyHazardPositions().isEmpty());
        assertTrue(com.steve.ai.memory.WorldKnowledge.isResourceName("iron_ore"));
        assertTrue(com.steve.ai.memory.WorldKnowledge.isStationName("crafting_table"));
        assertTrue(com.steve.ai.memory.WorldKnowledge.isContainerName("chest"));
        assertTrue(com.steve.ai.memory.WorldKnowledge.isHazardName("lava"));
        assertFalse(com.steve.ai.memory.WorldKnowledge.canSample(null, new net.minecraft.core.BlockPos(0, 64, 0)));
    }
}
