package com.steve.ai.inventory;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteveInventorySurvivalTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private SteveInventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new SteveInventory(36);
    }

    @Test
    void testEquipBestWeapon() {
        inventory.insert(new ItemStack(Items.WOODEN_SWORD));
        inventory.insert(new ItemStack(Items.DIAMOND_SWORD));
        inventory.insert(new ItemStack(Items.IRON_AXE));
        
        ItemStack previous = inventory.equipBestWeapon();
        
        assertTrue(previous == null || previous.isEmpty());
        assertEquals(Items.DIAMOND_SWORD, inventory.getMainHandItem().getItem());
        assertEquals(1, inventory.count(Items.WOODEN_SWORD));
        assertEquals(1, inventory.count(Items.IRON_AXE));
        assertEquals(0, inventory.count(Items.DIAMOND_SWORD));
    }

    @Test
    void testEquipBestArmor() {
        inventory.insert(new ItemStack(Items.LEATHER_CHESTPLATE));
        inventory.insert(new ItemStack(Items.DIAMOND_CHESTPLATE));
        inventory.insert(new ItemStack(Items.IRON_BOOTS));
        
        inventory.equipBestArmor();
        
        assertEquals(Items.DIAMOND_CHESTPLATE, inventory.getArmor(EquipmentSlot.CHEST).getItem());
        assertEquals(Items.IRON_BOOTS, inventory.getArmor(EquipmentSlot.FEET).getItem());
        assertTrue(inventory.getArmor(EquipmentSlot.HEAD).isEmpty());
        assertTrue(inventory.getArmor(EquipmentSlot.LEGS).isEmpty());
        
        assertEquals(1, inventory.count(Items.LEATHER_CHESTPLATE));
        assertEquals(0, inventory.count(Items.DIAMOND_CHESTPLATE));
        assertEquals(0, inventory.count(Items.IRON_BOOTS));
    }

    @Test
    void equippingAndRestoringMiningToolConservesEveryStack() {
        inventory.insert(new ItemStack(Items.DIAMOND_SWORD));
        inventory.setMainHandItem(new ItemStack(Items.TORCH, 16));

        ItemStack previous = inventory.equipBestTool(net.minecraft.world.level.block.Blocks.COBWEB);

        assertEquals(Items.TORCH, previous.getItem());
        assertEquals(Items.DIAMOND_SWORD, inventory.getMainHandItem().getItem());
        assertEquals(0, inventory.count(Items.DIAMOND_SWORD));
        assertEquals(16, inventory.count(Items.TORCH));

        assertTrue(inventory.restoreMainHand(previous));
        assertEquals(Items.TORCH, inventory.getMainHandItem().getItem());
        assertEquals(16, inventory.getMainHandItem().getCount());
        assertEquals(1, inventory.count(Items.DIAMOND_SWORD));
        assertEquals(0, inventory.count(Items.TORCH));
    }

    @Test
    void restoringBrokenToolStillRecoversPreviousEmptyHand() {
        inventory.insert(new ItemStack(Items.IRON_SWORD));

        ItemStack previous = inventory.equipBestTool(net.minecraft.world.level.block.Blocks.COBWEB);
        inventory.setMainHandItem(ItemStack.EMPTY);

        assertTrue(previous.isEmpty());
        assertTrue(inventory.restoreMainHand(previous));
        assertTrue(inventory.getMainHandItem().isEmpty());
        assertEquals(0, inventory.count(Items.IRON_SWORD));
    }

    @Test
    void restoreFailsClosedWhenSwapAnchorWasExternallyRemoved() {
        inventory.insert(new ItemStack(Items.IRON_SWORD));
        inventory.setMainHandItem(new ItemStack(Items.TORCH));
        ItemStack previous = inventory.equipBestTool(net.minecraft.world.level.block.Blocks.COBWEB);
        assertEquals(1, inventory.remove(Items.TORCH, 1));
        assertEquals(0, inventory.count(Items.TORCH));

        assertFalse(inventory.restoreMainHand(previous));
        assertEquals(Items.IRON_SWORD, inventory.getMainHandItem().getItem());
    }
}
