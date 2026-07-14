package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.security.PermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class PlaceBlockAction extends BaseAction {
    private Block blockToPlace;
    private BlockPos targetPos;
    private int ticksRunning;
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
        
        blockToPlace = parseBlock(blockName);
        
        if (blockToPlace == null || blockToPlace == Blocks.AIR) {
            result = ActionResult.failure("Invalid block type: " + blockName);
            return;
        }
        
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        
        if (ticksRunning > MAX_TICKS) {
            steve.getNavigation().stop();
            result = ActionResult.failure("Place block timeout");
            return;
        }
        
        if (!steve.blockPosition().closerThan(targetPos, 5.0)) {
            steve.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 1.0);
            return;
        }
        
        BlockState currentState = steve.level().getBlockState(targetPos);
        if (!currentState.isAir() && !currentState.liquid()) {
            result = ActionResult.failure("Position is not empty");
            return;
        }

        if (steve.level() instanceof ServerLevel serverLevel
                && PermissionManager.getInstance().isProtected(serverLevel, targetPos)) {
            result = ActionResult.failure("Position is inside a protected region", false);
            return;
        }

        var desiredState = blockToPlace.defaultBlockState();
        boolean placed = steve.level().getBlockState(targetPos).equals(desiredState)
            || steve.level().setBlock(targetPos, desiredState, 3);
        if (!placed) {
            result = ActionResult.failure("Minecraft rejected block placement");
            return;
        }
        result = ActionResult.success("Placed " + blockToPlace.getName().getString());
    }

    @Override
    protected void onCancel() {
        steve.getNavigation().stop();
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

