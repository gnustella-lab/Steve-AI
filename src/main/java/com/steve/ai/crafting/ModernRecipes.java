package com.steve.ai.crafting;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridges the pre-26.3 recipe queries used by the planning code to the
 * display-driven recipe API introduced in modern Minecraft versions.
 */
public final class ModernRecipes {
    private ModernRecipes() {
    }

    public static List<RecipeHolder<?>> allRecipes(MinecraftServer server) {
        return new ArrayList<>(server.getRecipeManager().getRecipes());
    }

    /** Resolves the result stack advertised by a recipe's display entries. */
    public static ItemStack resultOf(Recipe<?> recipe) {
        for (RecipeDisplay display : recipe.display()) {
            ItemStack result = display.result().resolveForFirstStack(ContextMap.EMPTY);
            if (!result.isEmpty()) {
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    public static List<ItemStack> itemsOf(Ingredient ingredient) {
        return ingredient.items().map(holder -> holder.value().getDefaultInstance()).toList();
    }

    public static List<Ingredient> ingredientsOf(Recipe<?> recipe) {
        return recipe.placementInfo().ingredients();
    }

    /** True when a recipe cannot be performed in the 2x2 player inventory grid. */
    public static boolean needsCraftingTable(Recipe<?> recipe) {
        if (recipe.getType() != RecipeType.CRAFTING) {
            return true;
        }
        for (RecipeDisplay display : recipe.display()) {
            if (display instanceof ShapedCraftingRecipeDisplay shaped
                    && (shaped.width() > 2 || shaped.height() > 2)) {
                return true;
            }
        }
        return false;
    }
}
