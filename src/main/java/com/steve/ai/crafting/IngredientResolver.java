package com.steve.ai.crafting;

import com.steve.ai.inventory.SteveInventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves recipe ingredients against the Steve inventory.
 *
 * <p>Determines which ingredients are already available, which need to be gathered,
 * and computes the deficit for each ingredient.</p>
 */
public class IngredientResolver {

    /**
     * Result of resolving ingredients against the inventory.
     */
    public record Resolution(
        Map<Ingredient, Integer> available,
        Map<Ingredient, Integer> deficit,
        boolean fullySatisfied
    ) {
        public int getTotalDeficit() {
            return deficit.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int getTotalAvailable() {
            return available.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /**
     * Resolves a list of ingredient-quantity pairs against the Steve inventory.
     *
     * @param inventory   The Steve inventory
     * @param ingredients List of ingredient-quantity pairs needed
     * @return Resolution result with available and deficit amounts
     */
    public static Resolution resolve(SteveInventory inventory, List<IngredientQuantity> ingredients) {
        Map<Ingredient, Integer> available = new HashMap<>();
        Map<Ingredient, Integer> deficit = new HashMap<>();

        for (IngredientQuantity iq : ingredients) {
            int needed = iq.quantity();
            int have = countIngredient(inventory, iq.ingredient());
            int satisfied = Math.min(have, needed);
            int remaining = needed - satisfied;

            if (satisfied > 0) {
                available.put(iq.ingredient(), satisfied);
            }
            if (remaining > 0) {
                deficit.put(iq.ingredient(), remaining);
            }
        }

        return new Resolution(
            Map.copyOf(available),
            Map.copyOf(deficit),
            deficit.isEmpty()
        );
    }

    /**
     * Counts how many items in the inventory match the given ingredient.
     */
    private static int countIngredient(SteveInventory inventory, Ingredient ingredient) {
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (ingredient.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Consumes ingredients from the inventory for one crafting operation.
     * Returns the list of consumed items for potential refund on failure.
     */
    public static List<ItemStack> consume(SteveInventory inventory, List<IngredientQuantity> ingredients) {
        List<ItemStack> consumed = new ArrayList<>();
        for (IngredientQuantity iq : ingredients) {
            int remaining = iq.quantity();
            for (ItemStack stack : inventory.getContents()) {
                if (remaining <= 0) break;
                if (iq.ingredient().test(stack)) {
                    int take = Math.min(stack.getCount(), remaining);
                    ItemStack taken = stack.copy();
                    taken.setCount(take);
                    inventory.remove(stack.getItem(), take);
                    consumed.add(taken);
                    remaining -= take;
                }
            }
        }
        return consumed;
    }

    /**
     * Restores consumed items back to the inventory (for refund on failure).
     */
    public static void restore(SteveInventory inventory, List<ItemStack> consumed) {
        for (ItemStack stack : consumed) {
            inventory.insert(stack);
        }
    }

    /**
     * Represents an ingredient and the quantity needed.
     */
    public record IngredientQuantity(Ingredient ingredient, String ingredientName, int quantity) {
        public IngredientQuantity(Ingredient ingredient, int quantity) {
            this(ingredient, ingredient != null ? ingredient.toString() : "", quantity);
        }
        public IngredientQuantity(String ingredientName, int quantity) {
            this(null, ingredientName, quantity);
        }
    }
}
