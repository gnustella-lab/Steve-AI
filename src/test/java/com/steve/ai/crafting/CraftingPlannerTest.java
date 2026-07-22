package com.steve.ai.crafting;

import com.steve.ai.crafting.IngredientResolver.IngredientQuantity;
import com.steve.ai.inventory.SteveInventory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftingPlannerTest {

    @Test
    void testRecipeDependencyGraphSort() throws Exception {
        RecipeDependencyGraph graph = new RecipeDependencyGraph();

        // Recipe 1: planks from log
        graph.addRecipe("recipe_planks", null,
            List.of(new IngredientQuantity("minecraft:oak_log", 1)), "minecraft:oak_planks", 4);

        // Recipe 2: sticks from planks
        graph.addRecipe("recipe_sticks", null,
            List.of(new IngredientQuantity("minecraft:oak_planks", 2)), "minecraft:stick", 4);

        List<String> order = graph.topologicalSort();

        assertEquals(2, order.size());
        assertEquals("recipe_planks", order.get(0));
        assertEquals("recipe_sticks", order.get(1));
    }

    @Test
    void testCircularDependencyDetection() {
        RecipeDependencyGraph cycleGraph = new RecipeDependencyGraph();
        cycleGraph.addRecipe("recipe_1", null, List.of(new IngredientQuantity("minecraft:iron_ingot", 1)), "minecraft:stick", 1);
        cycleGraph.addRecipe("recipe_2", null, List.of(new IngredientQuantity("minecraft:stick", 1)), "minecraft:iron_ingot", 1);

        assertThrows(RecipeDependencyGraph.CircularDependencyException.class, cycleGraph::topologicalSort);
    }

    @Test
    void testIngredientResolverDeficitCalculation() {
        IngredientResolver.Resolution emptyResolution = new IngredientResolver.Resolution(
            java.util.Map.of(),
            java.util.Map.of(),
            true
        );
        assertTrue(emptyResolution.fullySatisfied());
        assertEquals(0, emptyResolution.getTotalDeficit());
    }
}
