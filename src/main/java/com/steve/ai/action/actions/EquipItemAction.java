package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Equips an item from the Steve inventory into the specified equipment slot.
 */
public class EquipItemAction extends BaseAction {
    private String itemName;
    private String slotName;
    private int ticksRunning;
    private static final int MAX_TICKS = 100;

    public EquipItemAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        itemName = task.getStringParameter("item");
        slotName = task.getStringParameter("slot", "main_hand");
        ticksRunning = 0;

        if (itemName == null || itemName.isBlank()) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Missing item parameter").build();
            return;
        }
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        if (ticksRunning > MAX_TICKS) {
            result = ActionResult.failure(ActionResult.ERROR_TIMEOUT, "Equip timeout").build();
            return;
        }

        net.minecraft.world.entity.EquipmentSlot slot = parseSlot(slotName);
        if (slot == null) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Invalid slot: " + slotName).build();
            return;
        }

        Item targetItem = parseItem(itemName);
        if (targetItem == Items.AIR) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Unknown item: " + itemName).build();
            return;
        }

        int available = steve.getSteveInventory().count(targetItem);
        if (available <= 0) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE, "I don't have any " + itemName).build();
            return;
        }

        // Withdraw from main inventory first, then swap into equipment
        ItemStack toEquip = steve.getSteveInventory().withdraw(targetItem, 1);
        if (toEquip.isEmpty()) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE, "Could not withdraw " + itemName).build();
            return;
        }
        ItemStack previouslyEquipped = steve.getSteveInventory().swapEquipment(slot, toEquip);
        // Return previously equipped item to inventory
        if (previouslyEquipped != null && !previouslyEquipped.isEmpty()) {
            steve.getSteveInventory().insert(previouslyEquipped);
        }

        steve.syncEquipmentFromInventory();
        result = ActionResult.success("Equipped " + itemName + " into " + slotName).build();
    }

    @Override
    protected void onCancel() {
    }

    @Override
    public String getDescription() {
        return "Equip " + itemName + " into " + slotName;
    }

    private net.minecraft.world.entity.EquipmentSlot parseSlot(String name) {
        if (name == null) return net.minecraft.world.entity.EquipmentSlot.MAINHAND;
        return switch (name.toLowerCase()) {
            case "main_hand", "mainhand", "hand" -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            case "off_hand", "offhand" -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            case "head", "helmet" -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case "chest", "chestplate" -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case "legs", "leggings" -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case "feet", "boots" -> net.minecraft.world.entity.EquipmentSlot.FEET;
            default -> null;
        };
    }

    private Item parseItem(String name) {
        if (name == null || name.isBlank()) return Items.AIR;
        name = name.toLowerCase().replace(" ", "_");
        if (!name.contains(":")) {
            name = "minecraft:" + name;
        }
        ResourceLocation rl = ResourceLocation.tryParse(name);
        return rl != null ? BuiltInRegistries.ITEM.get(rl) : Items.AIR;
    }
}
