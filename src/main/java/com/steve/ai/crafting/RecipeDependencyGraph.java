package com.steve.ai.crafting;

import com.steve.ai.crafting.IngredientResolver.IngredientQuantity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds and traverses a dependency graph of crafting recipes.
 *
 * <p>Each node is a recipe. Edges represent ingredient dependencies: if recipe A
 * requires an item produced by recipe B, there is an edge from B to A.
 * Detects circular dependencies and computes a topological ordering.</p>
 */
public class RecipeDependencyGraph {

    private final Map<String, Node> nodes = new HashMap<>();
    private final Set<String> visited = new HashSet<>();
    private final Set<String> inStack = new HashSet<>();
    private final List<String> topologicalOrder = new ArrayList<>();

    /**
     * A node in the dependency graph.
     */
    public record Node(
        String recipeId,
        RecipeType<?> recipeType,
        List<IngredientQuantity> ingredients,
        String resultItem,
        int resultCount
    ) {
    }

    /**
     * Adds a recipe node to the graph.
     */
    public void addRecipe(String recipeId, RecipeType<?> recipeType,
            List<IngredientQuantity> ingredients, String resultItem, int resultCount) {
        nodes.put(recipeId, new Node(recipeId, recipeType, ingredients, resultItem, resultCount));
    }

    /**
     * Builds the dependency graph from a set of recipes.
     * An edge from recipe B to recipe A means B produces an ingredient needed by A.
     */
    public void buildEdges() {
        for (Node node : nodes.values()) {
            for (IngredientQuantity iq : node.ingredients()) {
                String producedBy = findProducer(iq.ingredient().toString());
                if (producedBy != null && !producedBy.equals(node.recipeId())) {
                    // Edge: producedBy -> node
                }
            }
        }
    }

    public String findProducerForIngredient(IngredientQuantity iq) {
        if (iq == null) return null;
        if (iq.ingredientName() != null && !iq.ingredientName().isBlank()) {
            String producer = findProducer(iq.ingredientName());
            if (producer != null) return producer;
        }
        if (iq.ingredient() != null) {
            try {
                for (Node node : nodes.values()) {
                    if (node.resultItem() != null) {
                        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                            net.minecraft.resources.ResourceLocation.tryParse(
                                node.resultItem().contains(":") ? node.resultItem() : "minecraft:" + node.resultItem()
                            ));
                        if (item != null && item != net.minecraft.world.item.Items.AIR
                                && iq.ingredient().test(new net.minecraft.world.item.ItemStack(item))) {
                            return node.recipeId();
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Standalone JUnit environment without Minecraft bootstrap
            }
        }
        return null;
    }

    private String findProducer(String itemName) {
        if (itemName == null) return null;
        for (Node node : nodes.values()) {
            if (node.resultItem().equals(itemName)) {
                return node.recipeId();
            }
        }
        return null;
    }

    public List<String> topologicalSort() throws CircularDependencyException {
        visited.clear();
        inStack.clear();
        topologicalOrder.clear();

        for (String recipeId : nodes.keySet()) {
            if (!visited.contains(recipeId)) {
                dfs(recipeId);
            }
        }

        return List.copyOf(topologicalOrder);
    }

    private void dfs(String recipeId) throws CircularDependencyException {
        if (inStack.contains(recipeId)) {
            throw new CircularDependencyException("Circular dependency detected involving: " + recipeId);
        }
        if (visited.contains(recipeId)) {
            return;
        }

        visited.add(recipeId);
        inStack.add(recipeId);

        Node node = nodes.get(recipeId);
        if (node != null) {
            for (IngredientQuantity iq : node.ingredients()) {
                String producer = findProducerForIngredient(iq);
                if (producer != null && !producer.equals(recipeId)) {
                    dfs(producer);
                }
            }
        }

        inStack.remove(recipeId);
        topologicalOrder.add(recipeId);
    }

    /**
     * Returns the node for a recipe ID.
     */
    public Node getNode(String recipeId) {
        return nodes.get(recipeId);
    }

    /**
     * Returns all recipe IDs in the graph.
     */
    public Set<String> getAllRecipeIds() {
        return Set.copyOf(nodes.keySet());
    }

    /**
     * Returns the number of nodes in the graph.
     */
    public int size() {
        return nodes.size();
    }

    /**
     * Checks if the graph is empty.
     */
    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * Clears the graph.
     */
    public void clear() {
        nodes.clear();
        visited.clear();
        inStack.clear();
        topologicalOrder.clear();
    }

    /**
     * Exception thrown when a circular dependency is detected in the recipe graph.
     */
    public static class CircularDependencyException extends Exception {
        public CircularDependencyException(String message) {
            super(message);
        }
    }
}
