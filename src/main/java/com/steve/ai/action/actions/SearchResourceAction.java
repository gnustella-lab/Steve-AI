package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.memory.WorldFact;
import com.steve.ai.security.PermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bounded, pathfinding-aware resource search. It observes candidates and never teleports the agent.
 */
public final class SearchResourceAction extends BaseAction {
    private String resource;
    private int maxDistance;
    private int ticksRunning;
    private int candidateIndex;
    private List<BlockPos> candidates;
    private BlockPos foundPosition;
    private Block targetBlock;
    private static final int MAX_TICKS = 1_200;
    private static final int MAX_CANDIDATES = 512;

    public SearchResourceAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        resource = task.getStringParameter("resource");
        maxDistance = Math.max(4, Math.min(64, task.getIntParameter("maxDistance", 32)));
        ticksRunning = 0;
        candidateIndex = 0;
        candidates = new ArrayList<>();

        if (resource == null || resource.isBlank() || !(steve.level() instanceof ServerLevel serverLevel)) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                "A server-side resource and search radius are required").build();
            return;
        }
        targetBlock = parseBlock(resource);
        if (targetBlock == Blocks.AIR) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                "Unknown searchable resource: " + resource).build();
            return;
        }
        buildCandidateBudget();
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        if (ticksRunning > MAX_TICKS) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                "Could not find " + resource + " within the bounded search radius")
                .retryable(true).requiresReplanning(true)
                .observation("searched", maxDistance).build();
            return;
        }

        if (foundPosition != null) {
            if (!steve.blockPosition().closerThan(foundPosition, 2.0)) {
                steve.getNavigation().moveTo(foundPosition.getX() + 0.5, foundPosition.getY(),
                    foundPosition.getZ() + 0.5, 1.0);
                return;
            }
            steve.getMemory().rememberWorldFact(new WorldFact(WorldFact.Kind.RESOURCE,
                resource, dimension(), foundPosition, currentTick(), 1.0, 24_000L, java.util.Map.of()));
            result = ActionResult.success("Found " + resource + " at " + foundPosition)
                .observation("resource", resource)
                .observation("x", foundPosition.getX())
                .observation("y", foundPosition.getY())
                .observation("z", foundPosition.getZ())
                .build();
            return;
        }

        if (candidateIndex >= candidates.size()) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                "No " + resource + " candidate was observed in range")
                .retryable(true).requiresReplanning(true)
                .observation("searched", maxDistance).build();
            return;
        }

        BlockPos candidate = candidates.get(candidateIndex++);
        if (!(steve.level() instanceof ServerLevel serverLevel)
                || !serverLevel.isLoaded(candidate)
                || PermissionManager.getInstance().isProtected(serverLevel, candidate)) {
            return;
        }
        if (serverLevel.getBlockState(candidate).getBlock() == targetBlock) {
            foundPosition = candidate;
        }
    }

    @Override
    protected void onCancel() {
        steve.getNavigation().stop();
    }

    @Override
    public String getDescription() {
        return "Search for " + (resource == null ? task.getStringParameter("resource") : resource)
            + " within " + maxDistance + " blocks";
    }

    private void buildCandidateBudget() {
        BlockPos origin = steve.blockPosition();
        for (int radius = 0; radius <= maxDistance && candidates.size() < MAX_CANDIDATES; radius += 2) {
            for (int x = -radius; x <= radius && candidates.size() < MAX_CANDIDATES; x += 2) {
                for (int z = -radius; z <= radius && candidates.size() < MAX_CANDIDATES; z += 2) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius && radius > 0) continue;
                    for (int y = -8; y <= 8 && candidates.size() < MAX_CANDIDATES; y += 2) {
                        candidates.add(origin.offset(x, y, z));
                    }
                }
            }
        }
    }

    private Block parseBlock(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace(' ', '_');
        normalized = switch (normalized) {
            case "iron" -> "iron_ore";
            case "coal" -> "coal_ore";
            case "gold" -> "gold_ore";
            case "diamond" -> "diamond_ore";
            case "copper" -> "copper_ore";
            default -> normalized;
        };
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
        ResourceLocation id = ResourceLocation.tryParse(normalized);
        return id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
    }

    private long currentTick() {
        return steve.level() instanceof ServerLevel level && level.getServer() != null
            ? level.getServer().getTickCount() : 0L;
    }

    private String dimension() {
        return steve.level() instanceof ServerLevel level
            ? level.dimension().location().toString() : "unknown";
    }
}
