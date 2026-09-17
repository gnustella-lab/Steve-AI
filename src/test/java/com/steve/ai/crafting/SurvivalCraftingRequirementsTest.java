package com.steve.ai.crafting;

import com.steve.ai.inventory.SteveInventory;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SurvivalCraftingRequirementsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void emptyInventoryNeedsLogsNotGatheredPlanksForTable() throws Exception {
        Object requirements = requirements(new SteveInventory(36));
        var method = requirements.getClass().getDeclaredMethod("missingRawMaterials");
        method.setAccessible(true);
        @SuppressWarnings("unchecked") var missing = (List<IngredientResolver.IngredientQuantity>) method.invoke(requirements);
        assertTrue(missing.stream().allMatch(i -> i.ingredientName().equals("minecraft:oak_log")), missing.toString());
        assertEquals(3, missing.stream().mapToInt(IngredientResolver.IngredientQuantity::quantity).sum());
    }

    @Test void threeLogsProduceEnoughPlanksForTableSticksAndPickaxe() throws Exception {
        SteveInventory inventory = new SteveInventory(36);
        inventory.insert(new ItemStack(Items.OAK_LOG, 3));
        Object requirements = requirements(inventory);
        var missing = requirements.getClass().getDeclaredMethod("missingRawMaterials");
        missing.setAccessible(true);
        assertEquals(List.of(), missing.invoke(requirements));
        var crafts = requirements.getClass().getDeclaredMethod("craftsByRecipe");
        crafts.setAccessible(true);
        assertEquals(Map.of("planks", 3, "sticks", 1, "pickaxe", 1), crafts.invoke(requirements));
    }

    private Object requirements(SteveInventory inventory) throws Exception {
        RecipeDependencyGraph graph = new RecipeDependencyGraph();
        graph.addRecipe("planks", RecipeType.CRAFTING, List.of(i(Items.OAK_LOG, 1)), "minecraft:oak_planks", 4);
        graph.addRecipe("sticks", RecipeType.CRAFTING, List.of(i(Items.OAK_PLANKS, 2)), "minecraft:stick", 4);
        graph.addRecipe("pickaxe", RecipeType.CRAFTING, List.of(i(Items.OAK_PLANKS, 3), i(Items.STICK, 2)), "minecraft:wooden_pickaxe", 1);
        Recipe<?> pickaxe = new ShapedRecipe(new ResourceLocation("test:pickaxe"), "", CraftingBookCategory.EQUIPMENT, 3, 3,
            NonNullList.withSize(9, Ingredient.of(Items.OAK_PLANKS)), new ItemStack(Items.WOODEN_PICKAXE));
        var method = CraftingPlanner.class.getDeclaredMethod("calculateRequirements", List.class, RecipeDependencyGraph.class,
            String.class, int.class, SteveInventory.class, Map.class);
        method.setAccessible(true);
        return method.invoke(null, graph.topologicalSort(), graph, "minecraft:wooden_pickaxe", 1, inventory, Map.of("pickaxe", pickaxe));
    }
    private IngredientResolver.IngredientQuantity i(net.minecraft.world.item.Item item, int quantity) {
        return new IngredientResolver.IngredientQuantity(Ingredient.of(item), quantity);
    }
}
