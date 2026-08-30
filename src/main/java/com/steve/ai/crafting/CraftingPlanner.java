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
        targetQuantity = Math.max(1, Math.min(2_048, targetQuantity));
        if (inventory == null || level == null || level.getServer() == null) {
            return new CraftPlan(targetItem, targetQuantity, List.of(), List.of(),
                false, "Server-side inventory and level are required");
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

        List<String> order;
        try {
            order = graph.topologicalSort();
        } catch (RecipeDependencyGraph.CircularDependencyException e) {
            return new CraftPlan(targetItem, targetQuantity, List.of(), List.of(),
                false, "Circular dependency: " + e.getMessage());
        }

        CraftRequirements requirements = calculateRequirements(
            order, graph, targetItem, targetQuantity, inventory);
        List<CraftStep> steps = new ArrayList<>();
        for (String recipeId : order) {
            RecipeDependencyGraph.Node node = graph.getNode(recipeId);
            Recipe<?> recipe = recipeMap.get(recipeId);
            if (node == null || recipe == null) continue;
            int timesToCraft = requirements.craftsByRecipe().getOrDefault(recipeId, 0);
            if (timesToCraft <= 0) continue;
            steps.add(new CraftStep(
                recipeId,
                recipe,
                node.recipeType(),
                node.resultItem(),
                node.resultCount(),
                timesToCraft,
                node.ingredients(),
                !recipe.canCraftInDimensions(2, 2)
            ));
        }

        List<IngredientResolver.IngredientQuantity> missingIngredients =
            new ArrayList<>(requirements.missingRawMaterials());

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

        // Recursively collect recipes even when some matching inventory is present. A single
        // existing ingredient must not hide a dependency needed for a larger requested quantity.
        for (var ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
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

    private static String resolveIngredientName(net.minecraft.world.item.crafting.Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return "unknown";
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return "unknown";
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(items[0].getItem());
        return key == null ? "unknown" : key.toString();
    }

    private static CraftRequirements calculateRequirements(List<String> order,
            RecipeDependencyGraph graph, String targetItem, int targetQuantity,
            SteveInventory inventory) {
        Map<String, Integer> outputDemand = new HashMap<>();
        Map<String, Integer> craftsByRecipe = new HashMap<>();
        List<IngredientResolver.IngredientQuantity> missingRawMaterials = new ArrayList<>();
        String normalizedTarget = normalizeItemId(targetItem);

        String targetRecipe = order.stream()
            .filter(recipeId -> {
                RecipeDependencyGraph.Node node = graph.getNode(recipeId);
                return node != null && normalizeItemId(node.resultItem()).equals(normalizedTarget);
            })
            .findFirst()
            .orElse(null);
        if (targetRecipe == null) {
            return new CraftRequirements(Map.of(), List.of());
        }

        outputDemand.put(targetRecipe, targetQuantity);
        InventorySupply supply = new InventorySupply(inventory);

        // Dependencies are topologically before dependants, so traverse backwards to propagate
        // exact output demand from the requested item into every intermediate recipe.
        for (int index = order.size() - 1; index >= 0; index--) {
            String recipeId = order.get(index);
            RecipeDependencyGraph.Node node = graph.getNode(recipeId);
            if (node == null) continue;
            int requiredOutput = Math.max(0, outputDemand.getOrDefault(recipeId, 0));
            if (requiredOutput <= 0) continue;

            int times = ceilDiv(requiredOutput, Math.max(1, node.resultCount()));
            craftsByRecipe.put(recipeId, times);
            for (IngredientResolver.IngredientQuantity ingredient : node.ingredients()) {
                int required = safeMultiply(Math.max(0, ingredient.quantity()), times);
                int remaining = supply.consume(ingredient.ingredient(), required);
                if (remaining <= 0) continue;

                String producer = graph.findProducerForIngredient(ingredient);
                if (producer != null && graph.getNode(producer) != null) {
                    outputDemand.merge(producer, remaining, CraftingPlanner::safeAdd);
                } else {
                    missingRawMaterials.add(new IngredientResolver.IngredientQuantity(
                        ingredient.ingredient(),
                        ingredient.ingredientName() == null || ingredient.ingredientName().isBlank()
                            ? resolveIngredientName(ingredient.ingredient()) : ingredient.ingredientName(),
                        remaining));
                }
            }
        }
        return new CraftRequirements(Map.copyOf(craftsByRecipe), List.copyOf(missingRawMaterials));
    }

    private static int ceilDiv(int numerator, int denominator) {
        if (numerator <= 0) return 0;
        return 1 + (numerator - 1) / Math.max(1, denominator);
    }

    private static int safeMultiply(int left, int right) {
        long value = (long) left * right;
        return (int) Math.min(1_000_000L, Math.max(0L, value));
    }

    private static int safeAdd(int left, int right) {
        return (int) Math.min(1_000_000L, Math.max(0L, (long) left + right));
    }

    private record CraftRequirements(
        Map<String, Integer> craftsByRecipe,
        List<IngredientResolver.IngredientQuantity> missingRawMaterials
    ) {}

    private static final class InventorySupply {
        private final List<ItemStack> stacks = new ArrayList<>();

        private InventorySupply(SteveInventory inventory) {
            if (inventory == null) return;
            for (ItemStack stack : inventory.getContents()) {
                if (stack != null && !stack.isEmpty()) stacks.add(stack.copy());
            }
        }

        private int consume(net.minecraft.world.item.crafting.Ingredient ingredient, int requested) {
            if (ingredient == null || requested <= 0) return Math.max(0, requested);
            int remaining = requested;
            for (ItemStack stack : stacks) {
                if (remaining <= 0) break;
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    int taken = Math.min(stack.getCount(), remaining);
                    stack.shrink(taken);
                    remaining -= taken;
                }
            }
            return remaining;
        }
    }

    private static String normalizeItemId(String itemName) {
        if (itemName == null) return "";
        String normalized = itemName.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }
}
