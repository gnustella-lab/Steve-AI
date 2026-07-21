package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.List;

/**
 * Deposits items from the Steve inventory into a nearby container (chest, barrel, etc.).
 */
public class DepositItemAction extends BaseAction {
    private String itemName;
    private int quantity;
    private int deposited;
    private int ticksRunning;
    private static final int MAX_TICKS = 300;

    public DepositItemAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        itemName = task.getStringParameter("item");
        quantity = task.getIntParameter("quantity", 64);
        deposited = 0;
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
            result = ActionResult.failure(ActionResult.ERROR_TIMEOUT, "Deposit timeout").build();
            return;
        }

        if (deposited >= quantity) {
            result = ActionResult.success("Deposited " + deposited + " " + itemName).build();
            return;
        }

        BlockEntity blockEntity = findNearbyContainer();
        if (blockEntity == null) {
            if (ticksRunning % 20 == 0) {
                steve.sendChatMessage("I need a chest or barrel nearby to deposit items.");
            }
            return;
        }

        if (!(blockEntity instanceof Container container)) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Block is not a container").build();
            return;
        }

        if (!steve.blockPosition().closerThan(blockEntity.getBlockPos(), 3.0)) {
            steve.getNavigation().moveTo(
                blockEntity.getBlockPos().getX() + 0.5,
                blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5, 1.0);
            return;
        }

        steve.getNavigation().stop();

        Item targetItem = parseItem(itemName);
        if (targetItem == Items.AIR) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Unknown item: " + itemName).build();
            return;
        }

        int toDeposit = Math.min(quantity - deposited, steve.getSteveInventory().count(targetItem));
        if (toDeposit <= 0) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                "I don't have any " + itemName + " to deposit").build();
            return;
        }

        int depositedThisTick = 0;
        for (int i = 0; i < toDeposit; i++) {
            ItemStack stack = steve.getSteveInventory().withdraw(targetItem, 1);
            if (stack.isEmpty()) break;

            boolean added = false;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack existing = container.getItem(slot);
                if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stack)) {
                    int limit = Math.min(existing.getMaxStackSize(), stack.getMaxStackSize());
                    int canAdd = limit - existing.getCount();
                    if (canAdd > 0) {
                        int add = Math.min(canAdd, stack.getCount());
                        existing.grow(add);
                        stack.shrink(add);
                        depositedThisTick += add;
                        if (stack.isEmpty()) {
                            added = true;
                            break;
                        }
                    }
                }
            }

            if (!added && !stack.isEmpty()) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    if (container.getItem(slot).isEmpty()) {
                        container.setItem(slot, stack);
                        depositedThisTick += stack.getCount();
                        break;
                    }
                }
            }
        }

        if (blockEntity instanceof ChestBlockEntity chest) {
            chest.setChanged();
        }

        deposited += depositedThisTick;

        if (depositedThisTick == 0) {
            result = ActionResult.failure(ActionResult.ERROR_INVENTORY_FULL, "Container is full").build();
            return;
        }
    }

    @Override
    protected void onCancel() {
        steve.getNavigation().stop();
    }

    @Override
    public String getDescription() {
        return "Deposit " + quantity + " " + itemName + " (" + deposited + ")";
    }

    private BlockEntity findNearbyContainer() {
        if (!(steve.level() instanceof ServerLevel serverLevel)) return null;
        BlockPos stevePos = steve.blockPosition();
        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = stevePos.offset(x, y, z);
                    BlockEntity be = serverLevel.getBlockEntity(pos);
                    if (be instanceof Container) {
                        return be;
                    }
                }
            }
        }
        return null;
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
