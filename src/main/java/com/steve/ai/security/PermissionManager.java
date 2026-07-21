package com.steve.ai.security;

import com.steve.ai.plugin.ActionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.Locale;
import java.util.UUID;
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
 * pm.setPermission(steveUuid, ActionPermission.BUILDING);
 *
 * // Check if Steve can execute an action
 * if (pm.canExecute(steveUuid, "mine")) {
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
    private final ConcurrentHashMap<UUID, ActionPermission> stevePermissions;

    /** Compatibility state for plugins still using names during the migration window. */
    private final ConcurrentHashMap<String, ActionPermission> legacyNamePermissions;

    /** Protected regions (level → set of region keys). */
    private final ConcurrentHashMap<String, ProtectedRegion> protectedRegions;

    private PermissionManager() {
        this.stevePermissions = new ConcurrentHashMap<>();
        this.legacyNamePermissions = new ConcurrentHashMap<>();
        this.protectedRegions = new ConcurrentHashMap<>();
    }

    public static PermissionManager getInstance() {
        return INSTANCE;
    }

    // ── Steve permissions ───────────────────────────────────────────

    /**
     * Sets the maximum permission level for a Steve.
     *
     * @param steveUuid  Steve entity UUID
     * @param permission Maximum allowed permission level
     */
    public void setPermission(UUID steveUuid, ActionPermission permission) {
        if (steveUuid == null || permission == null) {
            throw new IllegalArgumentException("Steve UUID and permission are required");
        }
        stevePermissions.put(steveUuid, permission);
        LOGGER.info("Permission for Steve UUID '{}' set to {}", steveUuid, permission);
    }

    /** @deprecated Migrate plugin state to the Steve entity UUID. */
    @Deprecated(forRemoval = false)
    public void setPermission(String steveName, ActionPermission permission) {
        legacyNamePermissions.put(normalizeName(steveName), permission);
        LOGGER.info("Permission for Steve '{}' set to {}", steveName, permission);
    }

    /**
     * Gets the effective permission for a Steve (configured or default).
     *
     * @param steveUuid Steve entity UUID
     * @return Effective permission level
     */
    public ActionPermission getPermission(UUID steveUuid) {
        return stevePermissions.getOrDefault(steveUuid, DEFAULT_PERMISSION);
    }

    /** @deprecated Migrate plugin state to the Steve entity UUID. */
    @Deprecated(forRemoval = false)
    public ActionPermission getPermission(String steveName) {
        return legacyNamePermissions.getOrDefault(normalizeName(steveName), DEFAULT_PERMISSION);
    }

    /**
     * Checks if a Steve can execute a specific action.
     *
     * @param steveUuid  Steve entity UUID
     * @param actionName Action name (e.g., "mine", "build")
     * @return true if the Steve has sufficient permission
     */
    public boolean canExecute(UUID steveUuid, String actionName) {
        return canExecute(steveUuid, null, actionName);
    }

    /**
     * Checks an action while honoring name-based overrides during the compatibility window.
     * UUID overrides always take precedence when both forms exist.
     *
     * @param steveUuid Steve entity UUID
     * @param steveName Current Steve name, used only for a legacy override fallback
     * @param actionName Action name
     * @return true if the effective permission satisfies the action descriptor
     */
    public boolean canExecute(UUID steveUuid, String steveName, String actionName) {
        if (steveUuid == null) {
            return false;
        }

        ActionPermission effectivePermission = stevePermissions.get(steveUuid);
        if (effectivePermission == null && steveName != null) {
            effectivePermission = legacyNamePermissions.get(normalizeName(steveName));
        }
        if (effectivePermission == null) {
            effectivePermission = DEFAULT_PERMISSION;
        }

        ActionPermission finalPermission = effectivePermission;
        return requiredPermission(actionName)
            .map(finalPermission::satisfies)
            .orElse(false);
    }

    /** @deprecated Migrate plugin state to the Steve entity UUID. */
    @Deprecated(forRemoval = false)
    public boolean canExecute(String steveName, String actionName) {
        return requiredPermission(actionName)
            .map(required -> getPermission(steveName).satisfies(required))
            .orElse(false);
    }

    /**
     * Removes a Steve's permission override, reverting to default.
     *
     * @param steveUuid Steve entity UUID
     */
    public void clearPermission(UUID steveUuid) {
        stevePermissions.remove(steveUuid);
        LOGGER.info("Permission for Steve UUID '{}' reset to default", steveUuid);
    }

    /** @deprecated Migrate plugin state to the Steve entity UUID. */
    @Deprecated(forRemoval = false)
    public void clearPermission(String steveName) {
        legacyNamePermissions.remove(normalizeName(steveName));
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

    /** Clears world-scoped permission state when the server stops. */
    public void clear() {
        stevePermissions.clear();
        legacyNamePermissions.clear();
        protectedRegions.clear();
        LOGGER.info("Steve permissions and protected regions cleared");
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

    private static java.util.Optional<ActionPermission> requiredPermission(String actionName) {
        return ActionRegistry.getInstance().getDescriptor(actionName)
            .map(com.steve.ai.plugin.ActionDescriptor::requiredPermission);
    }

    private static String normalizeName(String steveName) {
        if (steveName == null || steveName.isBlank()) {
            throw new IllegalArgumentException("Steve name cannot be blank");
        }
        return steveName.trim().toLowerCase(Locale.ROOT);
    }
}
