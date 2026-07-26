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
}
