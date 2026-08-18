package com.steve.ai.inventory;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteveInventoryToolSwapTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void equippingAndRestoringToolConservesEveryStack() {
        SteveInventory inventory = new SteveInventory(4);
        inventory.insert(new ItemStack(Items.DIAMOND_SWORD));
        inventory.setMainHandItem(new ItemStack(Items.TORCH, 16));

        ItemStack previous = inventory.equipBestTool(Blocks.COBWEB);

        assertEquals(Items.TORCH, previous.getItem());
        assertEquals(Items.DIAMOND_SWORD, inventory.getMainHandItem().getItem());
        assertEquals(0, inventory.count(Items.DIAMOND_SWORD));
        assertEquals(16, inventory.count(Items.TORCH));
        assertTrue(inventory.restoreMainHand(previous));
        assertEquals(Items.TORCH, inventory.getMainHandItem().getItem());
        assertEquals(1, inventory.count(Items.DIAMOND_SWORD));
        assertEquals(0, inventory.count(Items.TORCH));
    }

    @Test
    void restoringBrokenToolRecoversPreviousEmptyHand() {
        SteveInventory inventory = new SteveInventory(4);
        inventory.insert(new ItemStack(Items.IRON_SWORD));

        ItemStack previous = inventory.equipBestTool(Blocks.COBWEB);
        inventory.setMainHandItem(ItemStack.EMPTY);

        assertTrue(previous.isEmpty());
        assertTrue(inventory.restoreMainHand(previous));
        assertTrue(inventory.getMainHandItem().isEmpty());
        assertEquals(0, inventory.count(Items.IRON_SWORD));
    }

    @Test
    void restoreFailsClosedWhenSwapAnchorWasRemoved() {
        SteveInventory inventory = new SteveInventory(4);
        inventory.insert(new ItemStack(Items.IRON_SWORD));
        inventory.setMainHandItem(new ItemStack(Items.TORCH));
        ItemStack previous = inventory.equipBestTool(Blocks.COBWEB);
        assertEquals(1, inventory.remove(Items.TORCH, 1));

        assertFalse(inventory.restoreMainHand(previous));
        assertEquals(Items.IRON_SWORD, inventory.getMainHandItem().getItem());
    }
}
