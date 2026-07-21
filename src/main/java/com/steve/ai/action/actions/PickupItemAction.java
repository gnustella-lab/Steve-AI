package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.security.PermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;

import java.util.List;

/**
 * Picks up item entities from the ground into the Steve inventory.
 */
public class PickupItemAction extends BaseAction {
    private String itemName;
    private int quantity;
    private int pickedUp;
    private int ticksRunning;
    private static final int MAX_TICKS = 200;

    public PickupItemAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        itemName = task.getStringParameter("item");
        quantity = task.getIntParameter("quantity", 64);
        pickedUp = 0;
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
            result = ActionResult.failure(ActionResult.ERROR_TIMEOUT, "Pickup timeout").build();
            return;
        }

        if (pickedUp >= quantity) {
            result = ActionResult.success("Picked up " + pickedUp + " " + itemName).build();
            return;
        }

        Item targetItem = parseItem(itemName);
        if (targetItem == Items.AIR) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Unknown item: " + itemName).build();
            return;
        }

        List<ItemEntity> items = steve.level().getEntitiesOfClass(
            ItemEntity.class,
            steve.getBoundingBox().inflate(8.0),
            entity -> !entity.isRemoved() && entity.getItem().is(targetItem)
        );

        if (items.isEmpty()) {
            if (ticksRunning % 20 == 0) {
                steve.getNavigation().moveTo(
                    steve.getX() + (Math.random() - 0.5) * 10,
                    steve.getY(),
                    steve.getZ() + (Math.random() - 0.5) * 10, 1.0);
            }
            return;
        }

        ItemEntity itemEntity = items.get(0);
        if (!steve.blockPosition().closerThan(itemEntity.blockPosition(), 3.0)) {
            steve.getNavigation().moveTo(itemEntity, 1.0);
            return;
        }

        steve.getNavigation().stop();
        ItemStack offered = itemEntity.getItem().copy();
        int toTake = Math.min(offered.getCount(), quantity - pickedUp);
        offered.setCount(toTake);

        ItemStack remainder = steve.getSteveInventory().insert(offered);
        int accepted = toTake - remainder.getCount();
        pickedUp += accepted;

        if (accepted > 0) {
            itemEntity.getItem().shrink(accepted);
            if (itemEntity.getItem().isEmpty()) {
                itemEntity.discard();
            }
        }

        if (accepted == 0) {
            result = ActionResult.failure(ActionResult.ERROR_INVENTORY_FULL, "Inventory full").build();
            return;
        }
    }

    @Override
    protected void onCancel() {
        steve.getNavigation().stop();
    }

    @Override
    public String getDescription() {
        return "Pickup " + quantity + " " + itemName + " (" + pickedUp + ")";
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
