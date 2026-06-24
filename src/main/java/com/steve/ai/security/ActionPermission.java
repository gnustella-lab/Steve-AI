package com.steve.ai.security;

/**
 * Permission categories for Steve AI actions.
 *
 * <p>Each action type maps to a permission category, allowing server administrators
 * to control which actions Steves can perform.</p>
 *
 * <p><b>Permission levels (increasing privilege):</b></p>
 * <ul>
 *   <li>{@code NONE} - No actions allowed</li>
 *   <li>{@code MOVEMENT} - Pathfinding, following players</li>
 *   <li>{@code INTERACTION} - Chat, information gathering</li>
 *   <li>{@code GATHERING} - Mining, resource collection</li>
 *   <li>{@code BUILDING} - Placing blocks, building structures</li>
 *   <li>{@code COMBAT} - Attacking entities</li>
 *   <li>{@code ALL} - Everything (including destructive actions)</li>
 * </ul>
 *
 * @since 1.1.0
 */
public enum ActionPermission {

    /** No actions allowed. */
    NONE(0),

    /** Movement and following only. */
    MOVEMENT(10),

    /** Chat and information actions. */
    INTERACTION(20),

    /** Mining and resource gathering. */
    GATHERING(30),

    /** Block placement and structure building. */
    BUILDING(40),

    /** Combat actions. */
    COMBAT(50),

    /** All actions including destructive operations. */
    ALL(100);

    private final int level;

    ActionPermission(int level) {
        this.level = level;
    }

    /**
     * Returns the numeric permission level (higher = more privilege).
     */
    public int getLevel() {
        return level;
    }

    /**
     * Checks if this permission level satisfies the required level.
     *
     * @param required The minimum permission level needed
     * @return true if this permission is sufficient
     */
    public boolean satisfies(ActionPermission required) {
        return this.level >= required.level;
    }

    /**
     * Maps an action name to the permission level required to execute it.
     *
     * @param actionName The action name (e.g., "mine", "build", "attack")
     * @return The minimum permission required
     */
    public static ActionPermission requiredFor(String actionName) {
        if (actionName == null) return NONE;

        return switch (actionName.toLowerCase()) {
            case "pathfind", "follow" -> MOVEMENT;
            case "mine", "gather" -> GATHERING;
            case "place", "build" -> BUILDING;
            case "craft" -> INTERACTION;
            case "attack" -> COMBAT;
            default -> ALL; // Unknown actions require max permission
        };
    }
}
