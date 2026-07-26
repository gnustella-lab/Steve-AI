package com.steve.ai.crafting;

import com.steve.ai.action.Task;
import net.minecraft.world.item.crafting.RecipeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftingGoalDecomposerTest {

    @Test
    void testDecompositionProducesTasksInOrder() {
        RecipeType<?> smeltingType = new RecipeType<>() {
            @Override
            public String toString() { return "smelting"; }
        };

        // Create synthetic plan
        CraftingPlanner.CraftStep step1 = new CraftingPlanner.CraftStep(
            "craft_planks", null, null, "minecraft:oak_planks", 4, 1, List.of(), false
        );
        CraftingPlanner.CraftStep step2 = new CraftingPlanner.CraftStep(
            "smelt_iron", null, smeltingType, "minecraft:iron_ingot", 1, 3, List.of(), false
        );
        
        CraftingPlanner.CraftPlan plan = new CraftingPlanner.CraftPlan(
            "minecraft:iron_pickaxe",
            1,
            List.of(step1, step2),
            List.of(
                new IngredientResolver.IngredientQuantity("minecraft:oak_log", 1),
                new IngredientResolver.IngredientQuantity("minecraft:iron_ore", 3)
            ),
            false,
            "Missing ingredients"
        );

        CraftingGoalDecomposer.DecomposedGoal result = CraftingGoalDecomposer.decomposeFromPlan(plan);

        assertNotNull(result);
        assertEquals("minecraft:iron_pickaxe", result.targetItem());
        assertEquals(1, result.targetQuantity());
        assertFalse(result.achievable());
        
        List<Task> tasks = result.tasks();
        assertEquals(4, tasks.size(), "Should produce 4 tasks (2 gather, 2 process)");
        
        // Mine tasks first
        assertEquals("mine", tasks.get(0).getAction());
        assertEquals("minecraft:oak_log", tasks.get(0).getStringParameter("block"));
        assertEquals(1, tasks.get(0).getIntParameter("quantity", 0));

        assertEquals("mine", tasks.get(1).getAction());
        assertEquals("minecraft:iron_ore", tasks.get(1).getStringParameter("block"));
        assertEquals(3, tasks.get(1).getIntParameter("quantity", 0));

        // Crafting steps next
        assertEquals("craft", tasks.get(2).getAction());
        assertEquals("minecraft:oak_planks", tasks.get(2).getStringParameter("item"));
        assertEquals(4, tasks.get(2).getIntParameter("quantity", 0));

        assertEquals("smelt", tasks.get(3).getAction());
        assertEquals("minecraft:iron_ingot", tasks.get(3).getStringParameter("item"));
        assertEquals(3, tasks.get(3).getIntParameter("quantity", 0));
        
        List<String> missing = result.missingRawMaterials();
        assertTrue(missing.contains("minecraft:oak_log"));
        assertTrue(missing.contains("minecraft:iron_ore"));
    }

    @Test
    void testHandlingNullOrBlankTarget() {
        CraftingPlanner.CraftPlan plan = new CraftingPlanner.CraftPlan(
            "", 1, List.of(), List.of(), false, "Target item is null or blank"
        );
        CraftingGoalDecomposer.DecomposedGoal result = CraftingGoalDecomposer.decomposeFromPlan(plan);
        assertFalse(result.achievable());
        assertTrue(result.tasks().isEmpty());
        assertEquals("", result.targetItem());
    }

    @Test
    void testWithEmptyInventoryEverythingNeedsGathering() {
        CraftingPlanner.CraftPlan plan = new CraftingPlanner.CraftPlan(
            "stick", 2, List.of(new CraftingPlanner.CraftStep("stick", null, null, "stick", 4, 1, List.of(), false)),
            List.of(new IngredientResolver.IngredientQuantity("planks", 2)), false, "Missing ingredients"
        );
        CraftingGoalDecomposer.DecomposedGoal result = CraftingGoalDecomposer.decomposeFromPlan(plan);
        
        assertEquals(2, result.tasks().size());
        assertEquals("mine", result.tasks().get(0).getAction());
        assertEquals("planks", result.tasks().get(0).getStringParameter("block"));
        
        assertEquals("craft", result.tasks().get(1).getAction());
        assertEquals("stick", result.tasks().get(1).getStringParameter("item"));
        assertEquals(4, result.tasks().get(1).getIntParameter("quantity", 0));
    }
    
    @Test
    void testWithPartialInventory() {
        // Plan has no missing ingredients, achievable is true
        CraftingPlanner.CraftPlan plan = new CraftingPlanner.CraftPlan(
            "stick", 2, List.of(new CraftingPlanner.CraftStep("stick", null, null, "stick", 4, 1, List.of(), false)),
            List.of(), true, null
        );
        CraftingGoalDecomposer.DecomposedGoal result = CraftingGoalDecomposer.decomposeFromPlan(plan);
        
        assertTrue(result.achievable());
        assertTrue(result.missingRawMaterials().isEmpty());
        assertEquals(1, result.tasks().size());
        assertEquals("craft", result.tasks().get(0).getAction());
        assertEquals("stick", result.tasks().get(0).getStringParameter("item"));
        assertEquals(4, result.tasks().get(0).getIntParameter("quantity", 0));
    }
}
