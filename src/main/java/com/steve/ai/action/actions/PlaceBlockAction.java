package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.security.PermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class PlaceBlockAction extends BaseAction {
    private Block blockToPlace;
    private BlockPos targetPos;
    private int ticksRunning;
    private boolean materialReserved;
    private static final int MAX_TICKS = 200;

    public PlaceBlockAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        String blockName = task.getStringParameter("block");
        int x = task.getIntParameter("x", 0);
        int y = task.getIntParameter("y", 0);
        int z = task.getIntParameter("z", 0);

        targetPos = new BlockPos(x, y, z);
        ticksRunning = 0;
        materialReserved = false;

        blockToPlace = parseBlock(blockName);

        if (blockToPlace == null || blockToPlace == Blocks.AIR) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                "Invalid block type: " + blockName).build();
            return;
        }
    }

    @Override
    protected void onTick() {
        ticksRunning++;

        if (ticksRunning > MAX_TICKS) {
            if (materialReserved) {
                refundReserved();
            }
            steve.getNavigation().stop();
            result = ActionResult.failure(ActionResult.ERROR_TIMEOUT, "Place block timeout").build();
            return;
        }

        if (!steve.blockPosition().closerThan(targetPos, 5.0)) {
            steve.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 1.0);
            return;
        }

        BlockState currentState = steve.level().getBlockState(targetPos);
        if (!currentState.isAir() && !currentState.liquid()) {
            if (materialReserved) {
                refundReserved();
            }
            result = ActionResult.failure(ActionResult.ERROR_BLOCKED, "Position is not empty").build();
            return;
        }

        if (steve.level() instanceof ServerLevel serverLevel
                && PermissionManager.getInstance().isProtected(serverLevel, targetPos)) {
            if (materialReserved) {
                refundReserved();
            }
            result = ActionResult.failure(ActionResult.ERROR_PROTECTED,
                "Position is inside a protected region").build();
            return;
        }

        BlockState desiredState = blockToPlace.defaultBlockState();
        if (steve.level().getBlockState(targetPos).equals(desiredState)) {
            materialReserved = false;
            result = ActionResult.success("Placed " + blockToPlace.getName().getString()).build();
            return;
        }

        boolean survival = SteveConfig.SURVIVAL_CONSTRUCTION.get()
            && !SteveConfig.CREATIVE_CONSTRUCTION.get();
        Item blockItem = blockToPlace.asItem();
        if (survival && blockItem != Items.AIR) {
            if (!materialReserved) {
                int removed = steve.getSteveInventory().remove(blockItem, 1);
                if (removed <= 0) {
                    result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                        "Missing building material in inventory: "
                            + blockToPlace.getName().getString())
                        .retryable(true).build();
                    return;
                }
                materialReserved = true;
            }
        }

        boolean placed = steve.level().setBlock(targetPos, desiredState, 3);
        if (!placed) {
            if (materialReserved && survival && blockItem != Items.AIR) {
                steve.getSteveInventory().insert(new ItemStack(blockItem, 1));
            }
            materialReserved = false;
            result = ActionResult.failure(ActionResult.ERROR_BLOCKED,
                "Minecraft rejected block placement").build();
            return;
        }
        materialReserved = false;
        result = ActionResult.success("Placed " + blockToPlace.getName().getString()).build();
    }

    @Override
    protected void onCancel() {
        if (materialReserved) {
            refundReserved();
        }
        steve.getNavigation().stop();
    }

    private void refundReserved() {
        Item blockItem = blockToPlace.asItem();
        if (blockItem != null && blockItem != Items.AIR) {
            steve.getSteveInventory().insert(new ItemStack(blockItem, 1));
        }
        materialReserved = false;
    }

    @Override
    public String getDescription() {
        String blockName = blockToPlace != null
            ? blockToPlace.getName().getString()
            : task.getStringParameter("block");
        BlockPos position = targetPos != null
            ? targetPos
            : new BlockPos(
                task.getIntParameter("x", 0),
                task.getIntParameter("y", 0),
                task.getIntParameter("z", 0));
        return "Place " + blockName + " at " + position;
    }

    private Block parseBlock(String blockName) {
        if (blockName == null || blockName.isBlank()) {
            return Blocks.AIR;
        }
        blockName = blockName.toLowerCase().replace(" ", "_");
        if (!blockName.contains(":")) {
            blockName = "minecraft:" + blockName;
        }
        ResourceLocation resourceLocation = ResourceLocation.tryParse(blockName);
        return resourceLocation != null ? BuiltInRegistries.BLOCK.get(resourceLocation) : Blocks.AIR;
    }
}
