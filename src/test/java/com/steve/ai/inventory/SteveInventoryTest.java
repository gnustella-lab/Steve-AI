package com.steve.ai.inventory;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteveInventoryTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void stacksItemsAndReturnsOnlyTheOverflow() {
        SteveInventory inventory = new SteveInventory(1);

        assertTrue(inventory.canInsert(new ItemStack(Items.COBBLESTONE, 1)));
        ItemStack remainder = inventory.insert(new ItemStack(Items.COBBLESTONE, 80));

        assertEquals(64, inventory.count(Items.COBBLESTONE));
        assertEquals(16, remainder.getCount());
        assertFalse(inventory.canInsert(new ItemStack(Items.COBBLESTONE, 1)));
    }

    @Test
    void removesOnlyCommittedItems() {
        SteveInventory inventory = new SteveInventory(2);
        inventory.insert(new ItemStack(Items.BREAD, 5));

        assertEquals(3, inventory.remove(Items.BREAD, 3));
        assertEquals(2, inventory.count(Items.BREAD));
        assertEquals(2, inventory.remove(Items.BREAD, 10));
        assertEquals(0, inventory.count(Items.BREAD));
    }

    @Test
    void drainingForDeathOrTransferCannotDuplicateItems() {
        SteveInventory inventory = new SteveInventory(2);
        inventory.insert(new ItemStack(Items.BREAD, 5));
        inventory.insert(new ItemStack(Items.IRON_PICKAXE));

        java.util.List<ItemStack> committed = inventory.drainAll();

        assertEquals(2, committed.size());
        assertEquals(0, inventory.count(Items.BREAD));
        assertTrue(inventory.drainAll().isEmpty());
    }

    @Test
    void roundTripNbtPreservesStacksAndDurability() {
        SteveInventory original = new SteveInventory(3);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.setDamageValue(73);
        original.insert(pickaxe);
        original.insert(new ItemStack(Items.OAK_LOG, 32));

        CompoundTag tag = original.save();
        SteveInventory restored = new SteveInventory(3);
        restored.load(tag);

        assertEquals(1, restored.count(Items.IRON_PICKAXE));
        assertEquals(32, restored.count(Items.OAK_LOG));
        ItemStack restoredPickaxe = restored.getContents().stream()
            .filter(stack -> stack.is(Items.IRON_PICKAXE))
            .findFirst()
            .orElseThrow();
        assertEquals(73, restoredPickaxe.getDamageValue());
        assertTrue(restored.getContents().stream().noneMatch(ItemStack::isEmpty));
    }

    @Test
    void retainsPersistedCapacityWhenConfigurationShrinks() {
        SteveInventory original = new SteveInventory(3);
        original.insert(new ItemStack(Items.COBBLESTONE, 64));
        original.insert(new ItemStack(Items.DIRT, 64));
        original.insert(new ItemStack(Items.OAK_LOG, 64));

        SteveInventory restored = new SteveInventory(1);
        restored.load(original.save());

        assertEquals(3, restored.getSlotCount());
        assertEquals(64, restored.count(Items.COBBLESTONE));
        assertEquals(64, restored.count(Items.DIRT));
        assertEquals(64, restored.count(Items.OAK_LOG));
    }

    @Test
    void loadsLegacyInventoryWithoutVersionOrSizeTags() {
        SteveInventory original = new SteveInventory(2);
        original.insert(new ItemStack(Items.BREAD, 5));
        CompoundTag legacyTag = original.save();
        legacyTag.remove("DataVersion");
        legacyTag.remove("Size");

        SteveInventory restored = new SteveInventory(2);
        restored.load(legacyTag);

        assertEquals(5, restored.count(Items.BREAD));
    }

    @Test
    void producesACompactAggregatedPlannerSummary() {
        SteveInventory inventory = new SteveInventory(4);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.setDamageValue(73);
        inventory.insert(new ItemStack(Items.OAK_LOG, 32));
        inventory.insert(new ItemStack(Items.BREAD, 5));
        inventory.insert(pickaxe);

        String summary = inventory.summarize(10);

        assertTrue(summary.contains("oak_log: 32"));
        assertTrue(summary.contains("bread: 5"));
        assertTrue(summary.contains("iron_pickaxe: 1, durability 71%"));
    }

    @Test
    void roundTripNbtPreservesEquipmentAndDurability() {
        SteveInventory original = new SteveInventory(3);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.setDamageValue(91);
        original.setMainHandItem(pickaxe);
        original.setArmor(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));

        SteveInventory restored = new SteveInventory(3);
        restored.load(original.save());

        assertTrue(restored.getMainHandItem().is(Items.IRON_PICKAXE));
        assertEquals(91, restored.getMainHandItem().getDamageValue());
        assertTrue(restored.getArmor(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE));
    }

    @Test
    void loadingSnapshotClearsEquipmentMissingFromSnapshot() {
        SteveInventory restored = new SteveInventory(3);
        restored.setMainHandItem(new ItemStack(Items.DIAMOND_SWORD));

        restored.load(new SteveInventory(3).save());

        assertTrue(restored.getMainHandItem().isEmpty());
    }
}
