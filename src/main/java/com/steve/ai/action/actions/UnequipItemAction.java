package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Unequips an item from the specified equipment slot into the main inventory.
 */
public class UnequipItemAction extends BaseAction {
    private String slotName;
    private int ticksRunning;
    private static final int MAX_TICKS = 100;

    public UnequipItemAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        slotName = task.getStringParameter("slot", "main_hand");
        ticksRunning = 0;
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        if (ticksRunning > MAX_TICKS) {
            result = ActionResult.failure(ActionResult.ERROR_TIMEOUT, "Unequip timeout").build();
            return;
        }

        net.minecraft.world.entity.EquipmentSlot slot = parseSlot(slotName);
        if (slot == null) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Invalid slot: " + slotName).build();
            return;
        }

        ItemStack equipped = steve.getSteveInventory().getEquippedItem(slot);
        if (equipped.isEmpty()) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE, "Nothing equipped in " + slotName).build();
            return;
        }

        steve.getSteveInventory().equip(slot, ItemStack.EMPTY);
        ItemStack remainder = steve.getSteveInventory().insert(equipped);

        if (!remainder.isEmpty()) {
            steve.getSteveInventory().equip(slot, remainder);
            result = ActionResult.failure(ActionResult.ERROR_INVENTORY_FULL,
                "Could not unequip " + equipped.getCount() + " items, inventory full").build();
            return;
        }

        steve.syncEquipmentFromInventory();
        result = ActionResult.success("Unequipped " + equipped.getCount() + " from " + slotName).build();
    }

    @Override
    protected void onCancel() {
    }

    @Override
    public String getDescription() {
        return "Unequip from " + slotName;
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
}
