package com.steve.ai.security;

import com.steve.ai.SteveMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages action permissions and protected area validation.
 *
 * <p>Allows server administrators to restrict actions per Steve and define
 * protected regions where Steves cannot mine or build.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * PermissionManager pm = PermissionManager.getInstance();
 *
 * // Set maximum permission for a Steve
 * pm.setPermission("Bob", ActionPermission.BUILDING);
 *
 * // Check if Steve can execute an action
 * if (pm.canExecute("Bob", "mine")) {
 *     // Allow mining
 * }
 *
 * // Protect an area
 * pm.protectRegion(level, new BlockPos(-100, 0, -100), new BlockPos(100, 255, 100));
 * </pre>
 *
 * @since 1.1.0
 */
public class PermissionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionManager.class);

    private static final PermissionManager INSTANCE = new PermissionManager();

    /** Default maximum permission level for all Steves. */
    public static final ActionPermission DEFAULT_PERMISSION = ActionPermission.ALL;

    /** Per-Steve permission overrides. */
    private final ConcurrentHashMap<String, ActionPermission> stevePermissions;

    /** Protected regions (level → set of region keys). */
    private final ConcurrentHashMap<String, ProtectedRegion> protectedRegions;

    private PermissionManager() {
        this.stevePermissions = new ConcurrentHashMap<>();
        this.protectedRegions = new ConcurrentHashMap<>();
    }

    public static PermissionManager getInstance() {
        return INSTANCE;
    }

    // ── Steve permissions ───────────────────────────────────────────

    /**
     * Sets the maximum permission level for a Steve.
     *
     * @param steveName  Steve name
     * @param permission Maximum allowed permission level
     */
    public void setPermission(String steveName, ActionPermission permission) {
        stevePermissions.put(steveName.toLowerCase(), permission);
        LOGGER.info("Permission for Steve '{}' set to {}", steveName, permission);
    }

    /**
     * Gets the effective permission for a Steve (configured or default).
     *
     * @param steveName Steve name
     * @return Effective permission level
     */
    public ActionPermission getPermission(String steveName) {
        return stevePermissions.getOrDefault(steveName.toLowerCase(), DEFAULT_PERMISSION);
    }

    /**
     * Checks if a Steve can execute a specific action.
     *
     * @param steveName  Steve name
     * @param actionName Action name (e.g., "mine", "build")
     * @return true if the Steve has sufficient permission
     */
    public boolean canExecute(String steveName, String actionName) {
        ActionPermission userPerm = getPermission(steveName);
        ActionPermission required = ActionPermission.requiredFor(actionName);
        return userPerm.satisfies(required);
    }

    /**
     * Removes a Steve's permission override, reverting to default.
     *
     * @param steveName Steve name
     */
    public void clearPermission(String steveName) {
        stevePermissions.remove(steveName.toLowerCase());
        LOGGER.info("Permission for Steve '{}' reset to default", steveName);
    }

    // ── Protected regions ───────────────────────────────────────────

    /**
     * Protects a region from Steve modifications.
     *
     * <p>Steves will not be allowed to mine or place blocks within protected regions.</p>
     *
     * @param level The server level/dimension
     * @param min   Minimum corner of the region
     * @param max   Maximum corner of the region
     */
    public void protectRegion(ServerLevel level, BlockPos min, BlockPos max) {
        String regionKey = level.dimension().location() + ":" + min.toShortString() + "-" + max.toShortString();
        protectedRegions.put(regionKey, new ProtectedRegion(level, min, max));
        LOGGER.info("Protected region registered: {} from {} to {}", regionKey, min, max);
    }

    /**
     * Checks if a position is within any protected region.
     *
     * @param level The level to check
     * @param pos   The position to check
     * @return true if the position is protected
     */
    public boolean isProtected(ServerLevel level, BlockPos pos) {
        for (ProtectedRegion region : protectedRegions.values()) {
            if (region.contains(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the set of registered protected region keys.
     *
     * @return Protected region key set
     */
    public Set<String> getProtectedRegionKeys() {
        return protectedRegions.keySet();
    }

    /**
     * Removes all protected regions.
     */
    public void clearProtectedRegions() {
        protectedRegions.clear();
        LOGGER.info("All protected regions cleared");
    }

    // ── Inner class ─────────────────────────────────────────────────

    /**
     * Represents a protected cubic region in a specific dimension.
     */
    private static class ProtectedRegion {
        private final String dimensionKey;
        private final int minX, minY, minZ;
        private final int maxX, maxY, maxZ;

        ProtectedRegion(ServerLevel level, BlockPos min, BlockPos max) {
            this.dimensionKey = level.dimension().location().toString();
            this.minX = Math.min(min.getX(), max.getX());
            this.minY = Math.min(min.getY(), max.getY());
            this.minZ = Math.min(min.getZ(), max.getZ());
            this.maxX = Math.max(min.getX(), max.getX());
            this.maxY = Math.max(min.getY(), max.getY());
            this.maxZ = Math.max(min.getZ(), max.getZ());
        }

        boolean contains(ServerLevel level, BlockPos pos) {
            if (!dimensionKey.equals(level.dimension().location().toString())) {
                return false;
            }
            return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }
}
