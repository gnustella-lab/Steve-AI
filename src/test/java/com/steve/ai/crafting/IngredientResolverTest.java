package com.steve.ai.crafting;

import com.steve.ai.inventory.SteveInventory;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientResolverTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void consumeRespectsRepeatMultiplier() {
        SteveInventory inventory = new SteveInventory(4);
        inventory.insert(new ItemStack(Items.IRON_INGOT, 8));
        inventory.insert(new ItemStack(Items.STICK, 4));

        IngredientResolver.IngredientQuantity ingot =
            new IngredientResolver.IngredientQuantity(Ingredient.of(Items.IRON_INGOT), 3);
        IngredientResolver.IngredientQuantity stick =
            new IngredientResolver.IngredientQuantity(Ingredient.of(Items.STICK), 2);

        List<ItemStack> consumed = IngredientResolver.consume(
            inventory, List.of(ingot, stick), 2);

        assertEquals(2, consumed.size());
        assertEquals(6, totalOfType(consumed, Items.IRON_INGOT));
        assertEquals(4, totalOfType(consumed, Items.STICK));
        assertEquals(2, inventory.count(Items.IRON_INGOT));
        assertEquals(0, inventory.count(Items.STICK));
    }

    @Test
    void consumeRollsBackWhenInsufficientForFullRepeat() {
        SteveInventory inventory = new SteveInventory(4);
        inventory.insert(new ItemStack(Items.OAK_PLANKS, 3));

        IngredientResolver.IngredientQuantity planks =
            new IngredientResolver.IngredientQuantity(Ingredient.of(Items.OAK_PLANKS), 2);

        List<ItemStack> consumed = IngredientResolver.consume(
            inventory, List.of(planks), 2);

        assertTrue(consumed.isEmpty());
        assertEquals(3, inventory.count(Items.OAK_PLANKS));
    }

    @Test
    void restoreReinsertsConsumedStacksForRefund() {
        SteveInventory inventory = new SteveInventory(4);
        inventory.insert(new ItemStack(Items.BREAD, 5));

        IngredientResolver.IngredientQuantity bread =
            new IngredientResolver.IngredientQuantity(Ingredient.of(Items.BREAD), 5);

        List<ItemStack> consumed = IngredientResolver.consume(
            inventory, List.of(bread), 1);
        assertEquals(5, totalOfType(consumed, Items.BREAD));
        assertEquals(0, inventory.count(Items.BREAD));

        IngredientResolver.restore(inventory, consumed);
        assertEquals(5, inventory.count(Items.BREAD));
    }

    private static int totalOfType(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
