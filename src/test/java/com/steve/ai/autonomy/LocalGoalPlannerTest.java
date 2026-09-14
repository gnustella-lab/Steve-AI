package com.steve.ai.autonomy;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LocalGoalPlannerTest {
    private final LocalGoalPlanner planner = new LocalGoalPlanner();
    private final Set<String> items = Set.of("minecraft:iron_ingot", "minecraft:oak_log",
        "minecraft:oak_planks", "minecraft:stick", "example:metal/plate");

    @Test
    void recognizesExplicitActionsAndPreservesRegisteredPluralIds() {
        var craft = planner.parse("Make 8 oak planks.", items::contains).orElseThrow();
        assertEquals("craft", craft.action());
        assertEquals("minecraft:oak_planks", craft.item());
        assertEquals(8, craft.quantity());
        assertEquals("minecraft:iron_ingot",
            planner.parse("Smelt 16 iron ingots", items::contains).orElseThrow().item());
        assertEquals("gather", planner.parse("Mine 4 oak logs", items::contains).orElseThrow().action());
        assertEquals("example:metal/plate",
            planner.parse("craft example:metal/plate", items::contains).orElseThrow().item());
    }

    @Test
    void plansOnlyInventoryDeficit() {
        var request = planner.parse("smelt 16 iron_ingot", items::contains).orElseThrow();
        assertEquals(1, request.task(15).getIntParameter("quantity", -1));
        assertEquals(0, request.task(16).getIntParameter("quantity", -1));
        assertEquals(0, request.task(20).getIntParameter("quantity", -1));
        assertEquals(16, request.task(-1).getIntParameter("quantity", -1));
    }

    @Test
    void refusesCompoundAmbiguousAndUnboundedRequests() {
        for (String description : new String[]{"craft stick and gather oak_log", "craft stick then stop",
                "gather 4 oak_log near home", "smelt iron_ingot without coal", "get 4 iron_ingot",
                "bring 4 iron_ingot", "craft unknown", "craft 0 stick", "craft -1 stick",
                "craft 2049 stick", "craft 99999999999 stick", "craft 1.5 stick", ""}) {
            assertTrue(planner.parse(description, items::contains).isEmpty(), description);
        }
        assertTrue(planner.parse(null, items::contains).isEmpty());
        assertTrue(planner.parse("craft stick".repeat(100), items::contains).isEmpty());
    }
}
