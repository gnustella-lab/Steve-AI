package com.steve.ai.crafting;

import com.steve.ai.inventory.SteveInventory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plans the steps needed to craft a target item.
 *
 * <p>Builds a dependency graph of recipes, resolves ingredients against the
 * Steve inventory, and produces an ordered list of crafting steps.</p>
 */
public class CraftingPlanner {

    /**
     * A single step in the crafting plan.
     *
     * <p>{@code recipe} is the authoritative Minecraft recipe used to assemble the result
     * (preserves NBT, count, and shaped/shapeless semantics). It may be {@code null} only when
     * the plan was produced from a non-runtime source (e.g., unit tests without Minecraft bootstrapped).</p>
     */
    public record CraftStep(
        String recipeId,
        Recipe<?> recipe,
        RecipeType<?> recipeType,
        String resultItem,
        int resultCount,
        int timesToCraft,
        List<IngredientResolver.IngredientQuantity> ingredients,
        boolean needsCraftingTable
    ) {
    }

    /**
     * The complete crafting plan.
     */
    public record CraftPlan(
        String targetItem,
        int targetQuantity,
        List<CraftStep> steps,
        List<IngredientResolver.IngredientQuantity> missingIngredients,
        boolean achievable,
        String failureReason
    ) {
        public int getTotalSteps() {
            return steps.size();
        }
    }

    /**
     * Plans the crafting of a target item.
     *
     * @param targetItem     The item to craft
     * @param targetQuantity The quantity needed
     * @param inventory      The Steve inventory
     * @param level          The server level
     * @return A craft plan with ordered steps
     */
    public static CraftPlan plan(String targetItem, int targetQuantity,
            SteveInventory inventory, ServerLevel level) {

        if (targetItem == null || targetItem.isBlank()) {
            return new CraftPlan(targetItem, targetQuantity, List.of(), List.of(),
                false, "Target item is null or blank");
        }

        // Find the target recipe
        Recipe<?> targetRecipe = findRecipeForItem(level, targetItem);
        if (targetRecipe == null) {
            return new CraftPlan(targetItem, targetQuantity, List.of(), List.of(),
                false, "No recipe found for: " + targetItem);
        }

        // Build dependency graph
        RecipeDependencyGraph graph = new RecipeDependencyGraph();
        Map<String, Recipe<?>> recipeMap = new HashMap<>();

        // Collect all recipes that could be involved
        collectRelevantRecipes(level, targetItem, graph, recipeMap, new java.util.HashSet<>(), inventory);

        // Build the plan
        List<CraftStep> steps = new ArrayList<>();
        List<IngredientResolver.IngredientQuantity> missingIngredients = new ArrayList<>();

        try {
            List<String> order = graph.topologicalSort();
            java.util.Set<String> producedItems = new java.util.HashSet<>();
            for (String recipeId : order) {
                RecipeDependencyGraph.Node node = graph.getNode(recipeId);
                if (node == null) continue;

                Recipe<?> recipe = recipeMap.get(recipeId);
                if (recipe == null) continue;

                // Calculate how many times to craft
                int timesToCraft = calculateTimesToCraft(node, targetItem, targetQuantity);

                // Resolve ingredients
                List<IngredientResolver.IngredientQuantity> ingredients = node.ingredients();
                IngredientResolver.Resolution resolution = IngredientResolver.resolve(inventory, ingredients);

                if (!resolution.fullySatisfied()) {
                    for (var entry : resolution.deficit().entrySet()) {
                        IngredientResolver.IngredientQuantity iq = entry.getKey() != null
                            ? new IngredientResolver.IngredientQuantity(entry.getKey(),
                                resolveIngredientName(entry.getKey()), entry.getValue())
                            : null;
                        boolean producedByPlan = false;
                        if (iq != null && iq.ingredient() != null) {
                            for (var producedName : producedItems) {
                                String full = producedName.contains(":") ? producedName : "minecraft:" + producedName;
                                var rl = ResourceLocation.tryParse(full);
                                if (rl != null) {
                                    var prodItem = BuiltInRegistries.ITEM.get(rl);
                                    if (prodItem != net.minecraft.world.item.Items.AIR
                                            && iq.ingredient().test(new ItemStack(prodItem))) {
                                        producedByPlan = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (!producedByPlan) {
                            missingIngredients.add(iq != null
                                ? iq : new IngredientResolver.IngredientQuantity("unknown", entry.getValue()));
                        }
                    }
                }

                boolean needsCraftingTable = !recipe.canCraftInDimensions(2, 2);

                steps.add(new CraftStep(
                    recipeId,
                    recipe,
                    node.recipeType(),
                    node.resultItem(),
                    node.resultCount(),
                    timesToCraft,
                    ingredients,
                    needsCraftingTable
                ));
                producedItems.add(node.resultItem());
            }
        } catch (RecipeDependencyGraph.CircularDependencyException e) {
            return new CraftPlan(targetItem, targetQuantity, List.of(), List.of(),
                false, "Circular dependency: " + e.getMessage());
        }

        boolean achievable = missingIngredients.isEmpty();
        String failureReason = achievable ? null : "Missing ingredients: " + missingIngredients.size();

        return new CraftPlan(targetItem, targetQuantity, steps, missingIngredients,
            achievable, failureReason);
    }

    private static Recipe<?> findRecipeForItem(ServerLevel level, String itemName) {
        var recipeManager = level.getServer().getRecipeManager();
        var recipes = recipeManager.getRecipes();

        for (Recipe<?> recipe : recipes) {
            if (recipe.getResultItem(level.registryAccess()).is(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.tryParse(
                        itemName.contains(":") ? itemName : "minecraft:" + itemName
                    )
                )
            )) {
                return recipe;
            }
        }
        return null;
    }

    private static void collectRelevantRecipes(
            ServerLevel level,
            String targetItem,
            RecipeDependencyGraph graph,
            Map<String, Recipe<?>> recipeMap,
            java.util.Set<String> visited,
            SteveInventory inventory) {

        if (visited.contains(targetItem)) return;
        visited.add(targetItem);

        Recipe<?> recipe = findRecipeForItem(level, targetItem);
        if (recipe == null) return;

        String recipeId = recipe.getId().toString();
        if (recipeMap.containsKey(recipeId)) return;

        recipeMap.put(recipeId, recipe);

        // Add to graph
        List<IngredientResolver.IngredientQuantity> ingredients = new ArrayList<>();
        for (var ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) {
                ingredients.add(new IngredientResolver.IngredientQuantity(ingredient, 1));
            }
        }

        String resultItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
            recipe.getResultItem(level.registryAccess()).getItem()).toString();
        int resultCount = recipe.getResultItem(level.registryAccess()).getCount();

        graph.addRecipe(recipeId, recipe.getType(), ingredients, resultItem, resultCount);

        // Recursively collect recipes for ingredients
        for (var ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty() || hasMatchingIngredient(inventory, ingredient)) continue;
            for (var item : ingredient.getItems()) {
                String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    item.getItem()).toString();
                if (findRecipeForItem(level, itemName) != null) {
                    collectRelevantRecipes(level, itemName, graph, recipeMap, visited, inventory);
                    break;
                }
            }
        }
    }

    private static boolean hasMatchingIngredient(SteveInventory inventory,
            net.minecraft.world.item.crafting.Ingredient ingredient) {
        if (inventory == null || ingredient == null || ingredient.isEmpty()) return false;
        return inventory.getContents().stream().anyMatch(ingredient::test);
    }

    private static String resolveIngredientName(net.minecraft.world.item.crafting.Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return "unknown";
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return "unknown";
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(items[0].getItem());
        return key == null ? "unknown" : key.toString();
    }

    private static int calculateTimesToCraft(RecipeDependencyGraph.Node node,
            String targetItem, int targetQuantity) {
        if (normalizeItemId(node.resultItem()).equals(normalizeItemId(targetItem))) {
            return (int) Math.ceil((double) targetQuantity / node.resultCount());
        }
        return 1;
    }

    private static String normalizeItemId(String itemName) {
        if (itemName == null) return "";
        String normalized = itemName.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }
}
