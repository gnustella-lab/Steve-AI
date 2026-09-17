package com.steve.ai.autonomy;

import com.steve.ai.action.Task;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LocalCommandsTest {
    private final Set<String> items = Set.of(
        "minecraft:oak_planks", "minecraft:oak_log", "minecraft:stick", "minecraft:iron_ingot");

    @Test
    void expandsPortugueseLocalCommandToCraftTask() {
        Task task = new Task("local", Map.of("command", "fazer 8 tábuas de carvalho"));

        Task expanded = LocalCommands.expand(task, items::contains, item -> 0);

        assertEquals("craft", expanded.getAction());
        assertEquals("minecraft:oak_planks", expanded.getStringParameter("item"));
        assertEquals(8, expanded.getIntParameter("quantity", -1));
    }

    @Test
    void localCommandUsesInventoryDeficit() {
        Task task = new Task("local", Map.of("command", "fazer 8 tábuas de carvalho"));

        Task expanded = LocalCommands.expand(task, items::contains, item -> 3);

        assertEquals(5, expanded.getIntParameter("quantity", -1));
    }

    @Test
    void alreadySatisfiedLocalCommandHasZeroQuantity() {
        Task task = new Task("local", Map.of("command", "coletar 4 troncos de carvalho"));

        Task expanded = LocalCommands.expand(task, items::contains, item -> 4);

        assertEquals("gather", expanded.getAction());
        assertEquals(0, expanded.getIntParameter("quantity", -1));
    }

    @Test
    void rewritesPortugueseItemOnDirectCraftTask() {
        Task task = new Task("craft", Map.of("item", "tábuas de carvalho", "quantity", 8));

        Task expanded = LocalCommands.expand(task, items::contains, item -> 0);

        assertEquals("minecraft:oak_planks", expanded.getStringParameter("item"));
        assertEquals(8, expanded.getIntParameter("quantity", -1));
    }

    @Test
    void unknownLocalCommandIsLeftUnchanged() {
        Task task = new Task("local", Map.of("command", "construir um castelo gigante"));

        Task expanded = LocalCommands.expand(task, items::contains, item -> 0);

        assertEquals("local", expanded.getAction());
        assertEquals("construir um castelo gigante", expanded.getStringParameter("command"));
    }

    @Test
    void expandsHouseCommandToBuildTask() {
        Task task = new Task("local", Map.of("command", "construir uma casa"));

        Task expanded = LocalCommands.expand(task, items::contains, item -> 0);

        assertEquals("build", expanded.getAction());
        assertEquals("house", expanded.getStringParameter("structure"));
        assertEquals(7, expanded.getIntParameter("width", -1));
    }
}
