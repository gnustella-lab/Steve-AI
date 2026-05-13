package com.steve.ai.execution;

import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Safe API bridge between LLM-generated code and Minecraft.
 * All operations are validated and queued for execution.
 *
 * This class is exposed to JavaScript code as the `steve` global object.
 */
public class SteveAPI {
    private final SteveEntity steve;
    private final Queue<Task> actionQueue;

    public SteveAPI(SteveEntity steve) {
        this.steve = steve;
        this.actionQueue = new LinkedBlockingQueue<>();
    }

    // ====================
    // ASYNC OPERATIONS (Queue Actions)
    // ====================

    /**
     * Navigate to a specific position
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public void move(double x, double y, double z) {
        Map<String, Object> params = new HashMap<>();
        params.put("x", x);
        params.put("y", y);
        params.put("z", z);
        actionQueue.add(new Task("pathfind", params));
    }

    /**
     * Build a structure at a specific location
     * @param structureType Type of structure (house, castle, tower, barn, etc.)
     * @param position Map with x, y, z coordinates
     */
    public void build(String structureType, Map<String, Double> position) {
        if (structureType == null || structureType.trim().isEmpty()) {
            throw new IllegalArgumentException("Structure type cannot be empty");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("structure", structureType.toLowerCase());

        // Add position if provided
        if (position != null && position.containsKey("x") && position.containsKey("y") && position.containsKey("z")) {
            params.put("x", position.get("x").intValue());
            params.put("y", position.get("y").intValue());
            params.put("z", position.get("z").intValue());
        }

        actionQueue.add(new Task("build", params));
    }

    /**
     * Build a structure (using default location - in front of player)
     * @param structureType Type of structure
     */
    public void build(String structureType) {
        build(structureType, null);
    }

    /**
     * Mine a specific resource
     * @param blockType Type of block/ore to mine (e.g., "iron_ore", "diamond_ore")
     * @param count Number of blocks to mine
     */
    public void mine(String blockType, int count) {
        if (blockType == null || blockType.trim().isEmpty()) {
            throw new IllegalArgumentException("Block type cannot be empty");
        }

        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("blockType", blockType.toLowerCase());
        params.put("count", count);

        actionQueue.add(new Task("mine", params));
    }

    /**
     * Attack a target entity type
     * @param entityType Type of entity to attack (e.g., "zombie", "skeleton")
     */
    public void attack(String entityType) {
        if (entityType == null || entityType.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity type cannot be empty");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("target", entityType.toLowerCase());

        actionQueue.add(new Task("attack", params));
    }

    /**
     * Craft an item
     * @param itemName Name of item to craft (e.g., "iron_pickaxe", "crafting_table")
     * @param count Number of items to craft
     */
    public void craft(String itemName, int count) {
        if (itemName == null || itemName.trim().isEmpty()) {
            throw new IllegalArgumentException("Item name cannot be empty");
        }

        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("item", itemName.toLowerCase());
        params.put("count", count);

        actionQueue.add(new Task("craft", params));
    }

    /**
     * Place a single block at a specific position
     * @param blockType Type of block to place
     * @param position Map with x, y, z coordinates
     */
    public void place(String blockType, Map<String, Double> position) {
        if (blockType == null || blockType.trim().isEmpty()) {
            throw new IllegalArgumentException("Block type cannot be empty");
        }

        if (position == null || !position.containsKey("x") || !position.containsKey("y") || !position.containsKey("z")) {
            throw new IllegalArgumentException("Position must include x, y, z coordinates");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("block", blockType.toLowerCase());
        params.put("x", position.get("x").intValue());
        params.put("y", position.get("y").intValue());
        params.put("z", position.get("z").intValue());

        actionQueue.add(new Task("place", params));
    }

    /**
     * Send a chat message
     * @param message Message to send
     */
    public void say(String message) {
        if (message != null && !message.trim().isEmpty()) {
            // TODO: Implement chat message sending
            // For now, just log it
        }
    }

    /**
     * Follow a player by name
     * @param playerName Name of player to follow
     */
    public void follow(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Player name cannot be empty");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("playerName", playerName);

        actionQueue.add(new Task("follow", params));
    }

    /**
     * Gather a resource (combines finding and collecting)
     * @param resourceType Type of resource to gather
     * @param count Number to gather
     */
    public void gather(String resourceType, int count) {
        if (resourceType == null || resourceType.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource type cannot be empty");
        }

        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("resource", resourceType.toLowerCase());
        params.put("count", count);

        actionQueue.add(new Task("gather", params));
    }

    // ====================
    // SYNC READ OPERATIONS
    // ====================

    /**
     * Get Steve's current position
     * @return Map with x, y, z coordinates
     */
    public Map<String, Double> getPosition() {
        Vec3 pos = steve.position();
        Map<String, Double> position = new HashMap<>();
        position.put("x", pos.x);
        position.put("y", pos.y);
        position.put("z", pos.z);
        return position;
    }

    /**
     * Get nearby blocks within a radius
     * @param radius Search radius (max 16 blocks)
     * @return List of block type names
     */
    public List<String> getNearbyBlocks(int radius) {
        if (radius <= 0 || radius > 16) {
            throw new IllegalArgumentException("Radius must be between 1 and 16");
        }

        Set<String> blockTypes = new HashSet<>();
        BlockPos stevePos = steve.blockPosition();

        // Sample blocks within radius (not every block to avoid performance issues)
        for (int x = -radius; x <= radius; x += 2) {
            for (int y = -radius; y <= radius; y += 2) {
                for (int z = -radius; z <= radius; z += 2) {
                    BlockPos pos = stevePos.offset(x, y, z);
                    BlockState state = steve.level().getBlockState(pos);
                    String blockName = state.getBlock().getName().getString().toLowerCase();

                    if (!blockName.contains("air")) {
                        blockTypes.add(blockName);
                    }
                }
            }
        }

        return new ArrayList<>(blockTypes);
    }

    /**
     * Get nearby entities within a radius
     * @param radius Search radius (max 32 blocks)
     * @return List of entity type names
     */
    public List<String> getNearbyEntities(int radius) {
        if (radius <= 0 || radius > 32) {
            throw new IllegalArgumentException("Radius must be between 1 and 32");
        }

        List<String> entityNames = new ArrayList<>();
        Vec3 stevePos = steve.position();
        AABB searchBox = new AABB(
            stevePos.x - radius, stevePos.y - radius, stevePos.z - radius,
            stevePos.x + radius, stevePos.y + radius, stevePos.z + radius
        );

        List<Entity> entities = steve.level().getEntities(steve, searchBox);

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity) {
                String entityName = entity.getType().getDescription().getString().toLowerCase();
                entityNames.add(entityName);
            }
        }

        return entityNames;
    }

    /**
     * Check if Steve is idle (no pending actions)
     * @return true if action queue is empty, false otherwise
     */
    public boolean isIdle() {
        return actionQueue.isEmpty();
    }

    /**
     * Get the number of pending actions
     * @return Number of actions in queue
     */
    public int getPendingActionCount() {
        return actionQueue.size();
    }

    /**
     * Wait for a duration (in milliseconds)
     * NOTE: This is implemented as a busy-wait for simplicity
     * @param milliseconds Time to wait
     */
    public void wait(int milliseconds) throws InterruptedException {
        if (milliseconds > 0 && milliseconds < 30000) {  // Max 30 seconds
            Thread.sleep(milliseconds);
        }
    }

    // ====================
    // INTERNAL METHODS
    // ====================

    /**
     * Get the action queue (for ActionExecutor to consume)
     * @return Queue of pending tasks
     */
    public Queue<Task> getActionQueue() {
        return actionQueue;
    }

    /**
     * Clear all pending actions
     */
    public void clearActions() {
        actionQueue.clear();
    }

    /**
     * Get the Steve entity (for internal use)
     */
    SteveEntity getSteveEntity() {
        return steve;
    }
}
