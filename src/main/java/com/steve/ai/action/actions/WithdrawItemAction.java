package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Withdraws items from a nearby container into the Steve inventory.
 */
public class WithdrawItemAction extends BaseAction {
    private String itemName;
    private int quantity;
    private int withdrawn;
    private int ticksRunning;
    private static final int MAX_TICKS = 300;

    public WithdrawItemAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        itemName = task.getStringParameter("item");
        quantity = task.getIntParameter("quantity", 64);
        withdrawn = 0;
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
            result = ActionResult.failure(ActionResult.ERROR_TIMEOUT, "Withdraw timeout").build();
            return;
        }

        if (withdrawn >= quantity) {
            result = ActionResult.success("Withdrew " + withdrawn + " " + itemName).build();
            return;
        }

        BlockEntity blockEntity = findNearbyContainer();
        if (blockEntity == null) {
            if (ticksRunning % 20 == 0) {
                steve.sendChatMessage("I need a chest or barrel nearby to withdraw items from.");
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

        int toWithdraw = Math.min(quantity - withdrawn,
            countInContainer(container, targetItem));
        if (toWithdraw <= 0) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE, "Container doesn't have " + itemName).build();
            return;
        }

        int withdrawnThisTick = 0;
        for (int slot = 0; slot < container.getContainerSize() && withdrawnThisTick < toWithdraw; slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && existing.is(targetItem)) {
                int take = Math.min(existing.getCount(), toWithdraw - withdrawnThisTick);
                ItemStack taken = existing.copy();
                taken.setCount(take);
                existing.shrink(take);
                if (existing.isEmpty()) {
                    container.setItem(slot, ItemStack.EMPTY);
                }

                ItemStack remainder = steve.getSteveInventory().insert(taken);
                int accepted = take - remainder.getCount();
                withdrawnThisTick += accepted;

                if (!remainder.isEmpty()) {
                    if (existing.isEmpty()) {
                        container.setItem(slot, remainder);
                    } else {
                        existing.grow(remainder.getCount());
                    }
                }
            }
        }

        if (blockEntity instanceof ChestBlockEntity chest) {
            chest.setChanged();
        }

        withdrawn += withdrawnThisTick;

        if (withdrawnThisTick == 0) {
            result = ActionResult.failure(ActionResult.ERROR_INVENTORY_FULL, "My inventory is full").build();
            return;
        }
    }

    @Override
    protected void onCancel() {
        steve.getNavigation().stop();
    }

    @Override
    public String getDescription() {
        return "Withdraw " + quantity + " " + itemName + " (" + withdrawn + ")";
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

    private int countInContainer(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
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
