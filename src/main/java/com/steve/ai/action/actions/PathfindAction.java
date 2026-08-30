package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.memory.WorldFact;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

public class PathfindAction extends BaseAction {
    private BlockPos targetPos;
    private int ticksRunning;
    private int ticksWithoutProgress;
    private double lastDistance;
    private double closestDistance;
    private double initialDistance;
    private int attempts;
    private BlockPos routeTarget;
    private static final int MAX_TICKS = 600; // 30 seconds timeout
    private static final int STUCK_TICKS = 40;
    private static final int MAX_ATTEMPTS = 6;
    private static final double MAX_DISTANCE = 256.0;
    private static final BlockPos[] APPROACH_OFFSETS = {
        BlockPos.ZERO,
        new BlockPos(1, 0, 0),
        new BlockPos(-1, 0, 0),
        new BlockPos(0, 0, 1),
        new BlockPos(0, 0, -1),
        new BlockPos(1, 0, 1),
        new BlockPos(-1, 0, -1),
        new BlockPos(1, 1, 0)
    };

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
        attempts = 0;
        initialDistance = Math.sqrt(steve.distanceToSqr(x + 0.5, y, z + 0.5));
        closestDistance = initialDistance;
        lastDistance = initialDistance;

        if (initialDistance > MAX_DISTANCE) {
            result = pathFailure(ActionResult.ERROR_PATHING,
                "Pathfinding target exceeds the bounded movement distance");
            return;
        }
        if (steve.level() instanceof ServerLevel level && !level.isLoaded(targetPos)) {
            result = pathFailure(ActionResult.ERROR_PATHING,
                "Pathfinding target is in an unloaded chunk");
            return;
        }
        startNextRoute();
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        
        if (steve.blockPosition().closerThan(targetPos, 2.0)) {
            result = ActionResult.success("Reached target position").build();
            return;
        }

        double distance = Math.sqrt(steve.distanceToSqr(
            targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5));
        closestDistance = Math.min(closestDistance, distance);
        if (distance >= lastDistance - 0.01) {
            ticksWithoutProgress++;
        } else {
            ticksWithoutProgress = 0;
        }
        lastDistance = distance;
        
        if (ticksRunning > MAX_TICKS) {
            steve.getNavigation().stop();
            rememberRouteFailure("timeout");
            result = pathFailure(ActionResult.ERROR_TIMEOUT, "Pathfinding timeout");
            return;
        }

        if (ticksWithoutProgress >= STUCK_TICKS) {
            steve.getNavigation().stop();
            rememberRouteFailure("stuck");
            if (attempts < MAX_ATTEMPTS) {
                startNextRoute();
                return;
            }
            result = pathFailure(ActionResult.ERROR_PATHING, "Pathfinding made no progress");
            return;
        }
        
        if (steve.getNavigation().isDone() && !steve.blockPosition().closerThan(targetPos, 2.0)) {
            if (attempts < MAX_ATTEMPTS) {
                rememberRouteFailure("route_exhausted");
                startNextRoute();
            } else {
                result = pathFailure(ActionResult.ERROR_PATHING,
                    "No bounded path reached the target");
            }
        }
    }

    @Override
    protected void onCancel() {
        steve.getNavigation().stop();
    }

    @Override
    protected void onFinish() {
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

    private void startNextRoute() {
        while (attempts < MAX_ATTEMPTS) {
            BlockPos offset = APPROACH_OFFSETS[Math.min(attempts, APPROACH_OFFSETS.length - 1)];
            BlockPos candidate = targetPos.offset(offset);
            attempts++;
            if (isPenalizedApproach(candidate) && attempts < MAX_ATTEMPTS) {
                continue;
            }
            routeTarget = candidate;
            ticksWithoutProgress = 0;
            lastDistance = Math.sqrt(steve.distanceToSqr(
                targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5));
            steve.getNavigation().moveTo(
                routeTarget.getX() + 0.5, routeTarget.getY(), routeTarget.getZ() + 0.5, 1.0);
            return;
        }
        result = pathFailure(ActionResult.ERROR_PATHING, "No bounded path reached the target");
    }

    private boolean isPenalizedApproach(BlockPos candidate) {
        if (candidate == null || steve.getMemory() == null) {
            return false;
        }
        long now = 0L;
        String dimension = "unknown";
        if (steve.level() instanceof ServerLevel level && level.getServer() != null) {
            now = level.getServer().getTickCount();
            dimension = level.dimension().location().toString();
        }
        return steve.getMemory().getRelevantFacts("path", 8, now, dimension, candidate, 2.5)
            .stream()
            .anyMatch(fact -> fact.kind() == WorldFact.Kind.FAILURE
                && fact.key() != null && fact.key().startsWith("path:"));
    }

    private ActionResult pathFailure(String errorCode, String message) {
        return ActionResult.failure(errorCode, message)
            .retryable(true).requiresReplanning(true)
            .observation("target", targetPos == null ? "unknown" : targetPos.toShortString())
            .observation("blockedAt", steve.blockPosition().toShortString())
            .observation("attempts", attempts)
            .observation("pathLength", initialDistance)
            .observation("closestDistance", closestDistance)
            .build();
    }

    private void rememberRouteFailure(String reason) {
        if (!(steve.level() instanceof ServerLevel level) || level.getServer() == null) return;
        steve.getMemory().rememberWorldFact(new WorldFact(
            WorldFact.Kind.FAILURE,
            "path:" + (targetPos == null ? "unknown" : targetPos.toShortString()),
            level.dimension().location().toString(),
            steve.blockPosition(),
            level.getServer().getTickCount(),
            0.9,
            1_200L,
            Map.of("reason", reason, "target",
                targetPos == null ? "unknown" : targetPos.toShortString())));
    }
}
