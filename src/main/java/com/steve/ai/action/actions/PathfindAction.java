package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.BlockPos;

public class PathfindAction extends BaseAction {
    private BlockPos targetPos;
    private int ticksRunning;
    private int ticksWithoutProgress;
    private double lastDistance;
    private static final int MAX_TICKS = 600; // 30 seconds timeout

    public PathfindAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        int x = task.getIntParameter("x", 0);
        int y = task.getIntParameter("y", 0);
        int z = task.getIntParameter("z", 0);
        
        targetPos = new BlockPos(x, y, z);
        ticksRunning = 0;
        ticksWithoutProgress = 0;
        lastDistance = Double.MAX_VALUE;
        
        steve.getNavigation().moveTo(x, y, z, 1.0);
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        
        if (steve.blockPosition().closerThan(targetPos, 2.0)) {
            result = ActionResult.success("Reached target position").build();
            return;
        }

        double distance = steve.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        if (distance >= lastDistance - 0.01) {
            ticksWithoutProgress++;
        } else {
            ticksWithoutProgress = 0;
        }
        lastDistance = distance;
        
        if (ticksRunning > MAX_TICKS) {
            steve.getNavigation().stop();
            result = ActionResult.failure(ActionResult.ERROR_PATHING, "Pathfinding timeout")
                .retryable(true).requiresReplanning(true)
                .observation("target", targetPos.toShortString()).build();
            return;
        }

        if (ticksWithoutProgress >= 40) {
            steve.getNavigation().stop();
            result = ActionResult.failure(ActionResult.ERROR_PATHING, "Pathfinding made no progress")
                .retryable(true).requiresReplanning(true)
                .observation("target", targetPos.toShortString()).build();
            return;
        }
        
        if (steve.getNavigation().isDone() && !steve.blockPosition().closerThan(targetPos, 2.0)) {
            steve.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 1.0);
        }
    }

    @Override
    protected void onCancel() {
        steve.getNavigation().stop();
    }

    @Override
    public String getDescription() {
        if (targetPos != null) {
            return "Pathfind to " + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ();
        }
        return "Pathfind to "
            + task.getIntParameter("x", 0) + ", "
            + task.getIntParameter("y", 0) + ", "
            + task.getIntParameter("z", 0);
    }
}

