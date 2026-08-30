package com.steve.ai.security;

import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Fail-closed server policy for flight and teleportation. */
public final class MobilityPolicy {
    private MobilityPolicy() {
    }

    public static boolean canFly(SteveEntity steve) {
        return steve != null
            && SteveConfig.ALLOW_FLIGHT.get()
            && steve.getAccessProfile().hasCapability(AgentCapability.ALLOW_FLIGHT);
    }

    public static boolean canTeleport(SteveEntity steve) {
        return steve != null
            && SteveConfig.ALLOW_TELEPORT.get()
            && steve.getAccessProfile().hasCapability(AgentCapability.ALLOW_TELEPORT);
    }

    /**
     * Validates a teleport destination without loading chunks or bypassing protected regions.
     */
    public static boolean isSafeTeleportDestination(SteveEntity steve, BlockPos destination) {
        return canTeleport(steve) && isValidTeleportDestination(steve, destination);
    }

    /**
     * Validates only the physical/security destination. Authorization is deliberately separate
     * so server policy can be tested without mutating live Forge configuration.
     */
    public static boolean isValidTeleportDestination(SteveEntity steve, BlockPos destination) {
        if (steve == null || destination == null
                || !(steve.level() instanceof ServerLevel level)
                || level.getServer() == null
                || !level.getServer().isSameThread()
                || destination.getY() <= level.getMinBuildHeight()
                || destination.getY() + 1 >= level.getMaxBuildHeight()
                || !level.isLoaded(destination)
                || PermissionManager.getInstance().isProtected(level, destination)) {
            return false;
        }

        BlockState feet = level.getBlockState(destination);
        BlockState head = level.getBlockState(destination.above());
        BlockState below = level.getBlockState(destination.below());
        if (!feet.getCollisionShape(level, destination).isEmpty()
                || !head.getCollisionShape(level, destination.above()).isEmpty()) {
            return false;
        }
        if (feet.is(Blocks.LAVA) || head.is(Blocks.LAVA) || below.is(Blocks.LAVA)) {
            return false;
        }
        return !below.isAir() && !below.getCollisionShape(level, destination.below()).isEmpty();
    }
}
