package com.steve.ai.crafting;

import com.steve.ai.action.Task;
import com.steve.ai.inventory.SteveInventory;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CraftingGoalDecomposer {
    
    public record DecomposedGoal(
        String targetItem,
        int targetQuantity,
        List<Task> tasks,
        boolean achievable,
        String failureReason,
        List<String> missingRawMaterials
    ) {}
    
    /**
     * Decomposes a crafting goal into a sequence of tasks.
     * 
     * Example: "iron_pickaxe" x1 decomposes to:
     * 1. mine(block=oak_log, quantity=1)     -- if no planks available
     * 2. craft(item=oak_planks, quantity=4)   -- if no planks available  
     * 3. craft(item=stick, quantity=2)         -- if no sticks available
     * 4. mine(block=iron_ore, quantity=3)     -- if no iron ingots available
     * 5. smelt(item=iron_ingot, quantity=3)   -- if raw iron needs smelting
     * 6. craft(item=iron_pickaxe, quantity=1) -- final assembly
     * 
     * @param targetItem     The item to craft
     * @param targetQuantity The quantity needed
     * @param inventory      The Steve inventory
     * @param level          The server level
     * @return A decomposed goal containing the ordered list of tasks
     */
    public static DecomposedGoal decompose(
        String targetItem,
        int targetQuantity,
        SteveInventory inventory,
        ServerLevel level
    ) {
        CraftingPlanner.CraftPlan plan = CraftingPlanner.plan(targetItem, targetQuantity, inventory, level);
        return decomposeFromPlan(plan);
    }
    
    /**
     * Internal method to convert a CraftPlan into a DecomposedGoal.
     * Visible for testing.
     */
    static DecomposedGoal decomposeFromPlan(CraftingPlanner.CraftPlan plan) {
        List<Task> tasks = new ArrayList<>();
        List<String> missingRawMaterials = new ArrayList<>();
        
        // 1. Gather tasks for missing ingredients (dependencies)
        for (IngredientResolver.IngredientQuantity missing : plan.missingIngredients()) {
            String name = missing.ingredientName();
            if (name == null || name.isBlank()) {
                name = "unknown";
            }
            missingRawMaterials.add(name);
            tasks.add(new Task("mine", Map.of(
                "block", name,
                "quantity", missing.quantity()
            )));
        }
        
        // 2. Craft/Smelt tasks in dependency order
        for (CraftingPlanner.CraftStep step : plan.steps()) {
            String action = "craft";
            if (step.recipeType() != null) {
                String typeStr = step.recipeType().toString().toLowerCase();
                if (typeStr.contains("smelting") || typeStr.contains("blasting") || typeStr.contains("smoking")) {
                    action = "smelt";
                }
            }
            int quantity = step.resultCount() * step.timesToCraft();
            tasks.add(new Task(action, Map.of(
                "item", step.resultItem(),
                "quantity", quantity
            )));
        }
        
        return new DecomposedGoal(
            plan.targetItem(),
            plan.targetQuantity(),
            tasks,
            plan.achievable(),
            plan.failureReason(),
            missingRawMaterials
        );
    }
}
