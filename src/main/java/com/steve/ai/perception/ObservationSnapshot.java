package com.steve.ai.perception;

import com.steve.ai.entity.SteveEntity;
import com.steve.ai.inventory.SteveInventory;
import com.steve.ai.memory.SteveMemory;
import com.steve.ai.memory.WorldKnowledge;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ObservationSnapshot {
    // Core positioning
    private final int x;
    private final int y;
    private final int z;
    private final String dimension;
    private final String biome;

    // Time and weather
    private final long dayTime;
    private final boolean isNight;
    private final boolean isRaining;
    private final boolean isThundering;

    // Entity state
    private final float health;
    private final float maxHealth;
    private final int lightLevel;

    // Inventory summary
    private final String inventorySummary;
    private final String equipmentSummary;
    private final String mainHandSummary;
    private final int inventoryUsedSlots;
    private final int inventoryCapacity;

    // Nearby world state
    private final List<String> nearbyPlayers;
    private final List<String> nearbyEntities;
    private final List<String> nearbyBlocks;
    private final List<String> nearbyThreats;
    private final List<String> nearbyContainers;
    private final List<String> nearbyDroppedItems;
    private final List<String> relevantMemory;
    private final List<String> protectedPositions;

    // Current state
    private final String currentGoal;
    private final String activeSubgoal;
    private final String currentAction;
    private final String lastActionResult;
    private final List<String> recentActions;
    private final String agentState;
    private final String navigationState;

    // Ownership
    private final String ownerName;
    private final double distanceToOwner;

    // Timestamp
    private final long capturedAtTick;

    private ObservationSnapshot(Builder builder) {
        this.x = builder.x;
        this.y = builder.y;
        this.z = builder.z;
        this.dimension = builder.dimension;
        this.biome = builder.biome;
        this.dayTime = builder.dayTime;
        this.isNight = builder.isNight;
        this.isRaining = builder.isRaining;
        this.isThundering = builder.isThundering;
        this.health = builder.health;
        this.maxHealth = builder.maxHealth;
        this.lightLevel = builder.lightLevel;
        this.inventorySummary = builder.inventorySummary;
        this.equipmentSummary = builder.equipmentSummary;
        this.mainHandSummary = builder.mainHandSummary;
        this.inventoryUsedSlots = builder.inventoryUsedSlots;
        this.inventoryCapacity = builder.inventoryCapacity;
        this.nearbyPlayers = List.copyOf(builder.nearbyPlayers);
        this.nearbyEntities = List.copyOf(builder.nearbyEntities);
        this.nearbyBlocks = List.copyOf(builder.nearbyBlocks);
        this.nearbyThreats = List.copyOf(builder.nearbyThreats);
        this.nearbyContainers = List.copyOf(builder.nearbyContainers);
        this.nearbyDroppedItems = List.copyOf(builder.nearbyDroppedItems);
        this.relevantMemory = List.copyOf(builder.relevantMemory);
        this.protectedPositions = List.copyOf(builder.protectedPositions);
        this.currentGoal = builder.currentGoal;
        this.activeSubgoal = builder.activeSubgoal;
        this.currentAction = builder.currentAction;
        this.lastActionResult = builder.lastActionResult;
        this.recentActions = List.copyOf(builder.recentActions);
        this.agentState = builder.agentState;
        this.navigationState = builder.navigationState;
        this.ownerName = builder.ownerName;
        this.distanceToOwner = builder.distanceToOwner;
        this.capturedAtTick = builder.capturedAtTick;
    }

    public static ObservationSnapshot capture(SteveEntity steve) {
        Builder builder = new Builder();
        if (steve == null) {
            return builder.build();
        }

        Level level = steve.level();
        if (level != null) {
            BlockPos pos = steve.blockPosition();
            builder.x(pos.getX()).y(pos.getY()).z(pos.getZ());
            
            ResourceKey<Level> dimKey = level.dimension();
            builder.dimension(dimKey != null ? dimKey.location().getPath() : "unknown");
            
            builder.dayTime(level.getDayTime());
            builder.isNight(level.isNight());
            builder.isRaining(level.isRaining());
            builder.isThundering(level.isThundering());
            builder.lightLevel(level.getMaxLocalRawBrightness(pos));
        }

        builder.health(steve.getHealth());
        builder.maxHealth(steve.getMaxHealth());

        SteveInventory inventory = steve.getSteveInventory();
        if (inventory != null) {
            builder.inventorySummary(inventory.summarize(20));
            builder.equipmentSummary(inventory.summarizeEquipment());
            builder.mainHandSummary(inventory.summarizeHand(EquipmentSlot.MAINHAND));
            builder.inventoryUsedSlots(inventory.getContents().size());
            builder.inventoryCapacity(inventory.getSlotCount());
        }

        SteveMemory memory = steve.getMemory();
        if (memory != null) {
            builder.currentGoal(memory.getCurrentGoal() != null ? memory.getCurrentGoal() : "");
            builder.recentActions(memory.getRecentActions(5));
            builder.relevantMemory(memory.getRelevantFacts(memory.getCurrentGoal(), 6).stream()
                .map(fact -> fact.kind().name().toLowerCase() + ":" + fact.key()
                    + (fact.position() == null ? "" : "@" + fact.position().toShortString()))
                .toList());
        }

        if (steve.getActionExecutor() != null && steve.getActionExecutor().getStateMachine() != null) {
            builder.agentState(steve.getActionExecutor().getStateMachine().getCurrentState().name());
        }

        Player owner = steve.getPreferredPlayer();
        if (owner != null) {
            builder.ownerName(owner.getName().getString());
            builder.distanceToOwner(steve.distanceTo(owner));
        } else {
            builder.distanceToOwner(-1.0);
        }

        if (level instanceof ServerLevel serverLevel) {
            builder.capturedAtTick(serverLevel.getServer().getTickCount());
            
            WorldKnowledge wk = new WorldKnowledge(steve);
            builder.biome(wk.getBiomeName() != null ? wk.getBiomeName() : "unknown");
            
            // Filter players and threats
            List<String> players = new ArrayList<>();
            List<String> threats = new ArrayList<>();
            List<String> entities = new ArrayList<>();
            List<String> droppedItems = new ArrayList<>();
            
            for (Entity e : wk.getNearbyEntities()) {
                if (e == steve) continue;
                
                int dist = (int) steve.distanceTo(e);
                String name = e.getType().getDescription().getString();
                if (e instanceof Player p) {
                    name = p.getName().getString();
                    players.add(name + " (" + dist + " blocks)");
                } else if (e instanceof Enemy) {
                    threats.add(name + " (" + dist + " blocks)");
                } else if (e instanceof ItemEntity itemEntity) {
                    droppedItems.add(itemEntity.getItem().getHoverName().getString()
                        + " x" + itemEntity.getItem().getCount() + " (" + dist + " blocks)");
                } else {
                    entities.add(name + " (" + dist + " blocks)");
                }
            }
            
            builder.nearbyPlayers(players.stream().limit(10).collect(Collectors.toList()));
            builder.nearbyThreats(threats.stream().limit(15).collect(Collectors.toList()));
            builder.nearbyEntities(entities.stream().limit(15).collect(Collectors.toList()));
            builder.nearbyDroppedItems(droppedItems.stream().limit(12).collect(Collectors.toList()));
            
            // Filter blocks
            List<String> blocks = new ArrayList<>();
            List<String> containers = new ArrayList<>();
            
            for (Map.Entry<Block, Integer> entry : wk.getNearbyBlocks().entrySet()) {
                Block b = entry.getKey();
                int count = entry.getValue();
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
                String blockName = id != null ? id.getPath() : "unknown";
                String info = blockName + " (" + count + ")";
                
                blocks.add(info);
                
                if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.FURNACE || 
                    b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER || b == Blocks.CRAFTING_TABLE || 
                    b == Blocks.BARREL || b == Blocks.SHULKER_BOX) {
                    containers.add(info);
                }
            }
            
            builder.nearbyBlocks(blocks.stream().limit(20).collect(Collectors.toList()));
            builder.nearbyContainers(containers.stream().limit(10).collect(Collectors.toList()));
        }

        return builder.build();
    }

    public String toPromptContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("Position: [").append(x).append(", ").append(y).append(", ").append(z)
          .append("] in ").append(dimension).append("\n");
        
        long day = (dayTime / 24000L) + 1;
        String timeOfDay = isNight ? "night" : "morning";
        sb.append("Time: Day ").append(day).append(", ").append(timeOfDay)
          .append(" | Biome: ").append(biome).append(" | Light: ").append(lightLevel).append("\n");
        
        sb.append("Health: ").append((int) health).append("/").append((int) maxHealth).append("\n");
        
        sb.append("Inventory:\n");
        if (inventorySummary != null && !inventorySummary.isEmpty()) {
            sb.append(inventorySummary).append("\n");
        } else {
            sb.append("- empty\n");
        }
        
        sb.append("Equipment: ").append(equipmentSummary != null ? equipmentSummary : "empty").append("\n");
        if (inventoryCapacity > 0) {
            sb.append("Capacity: ").append(inventoryUsedSlots).append('/').append(inventoryCapacity).append(" slots\n");
        }
        
        if (!nearbyPlayers.isEmpty()) {
            sb.append("Nearby players: ").append(String.join(", ", nearbyPlayers)).append("\n");
        }
        if (!nearbyThreats.isEmpty()) {
            sb.append("Nearby threats: ").append(String.join(", ", nearbyThreats)).append("\n");
        }
        if (!nearbyDroppedItems.isEmpty()) {
            sb.append("Dropped items: ").append(String.join(", ", nearbyDroppedItems)).append("\n");
        }
        
        if (currentGoal != null && !currentGoal.isEmpty()) {
            sb.append("Goal: ").append(currentGoal).append("\n");
        }
        if (activeSubgoal != null && !activeSubgoal.isEmpty()) {
            sb.append("Subgoal: ").append(activeSubgoal).append("\n");
        }
        if (currentAction != null && !currentAction.isEmpty()) {
            sb.append("Action: ").append(currentAction).append("\n");
        }
        if (lastActionResult != null && !lastActionResult.isEmpty()) {
            sb.append("Last result: ").append(lastActionResult).append("\n");
        }
        if (navigationState != null && !navigationState.isEmpty()) {
            sb.append("Navigation: ").append(navigationState).append("\n");
        }
        if (!relevantMemory.isEmpty()) {
            sb.append("Relevant memory: ").append(String.join(" | ", relevantMemory)).append("\n");
        }
        
        if (!recentActions.isEmpty()) {
            sb.append("Recent: ").append(String.join(", ", recentActions)).append("\n");
        }
        
        return sb.toString().trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObservationSnapshot that = (ObservationSnapshot) o;
        return x == that.x && y == that.y && z == that.z && 
               dayTime == that.dayTime && isNight == that.isNight && 
               isRaining == that.isRaining && isThundering == that.isThundering && 
               Float.compare(that.health, health) == 0 && 
               Float.compare(that.maxHealth, maxHealth) == 0 && 
               lightLevel == that.lightLevel && 
               Double.compare(that.distanceToOwner, distanceToOwner) == 0 && 
               capturedAtTick == that.capturedAtTick && 
               Objects.equals(dimension, that.dimension) && 
               Objects.equals(biome, that.biome) && 
               Objects.equals(inventorySummary, that.inventorySummary) && 
               Objects.equals(equipmentSummary, that.equipmentSummary) && 
               inventoryUsedSlots == that.inventoryUsedSlots && inventoryCapacity == that.inventoryCapacity &&
               Objects.equals(mainHandSummary, that.mainHandSummary) && 
               Objects.equals(nearbyPlayers, that.nearbyPlayers) && 
               Objects.equals(nearbyEntities, that.nearbyEntities) && 
               Objects.equals(nearbyBlocks, that.nearbyBlocks) && 
               Objects.equals(nearbyThreats, that.nearbyThreats) && 
               Objects.equals(nearbyContainers, that.nearbyContainers) && 
               Objects.equals(nearbyDroppedItems, that.nearbyDroppedItems) &&
               Objects.equals(relevantMemory, that.relevantMemory) &&
               Objects.equals(protectedPositions, that.protectedPositions) &&
               Objects.equals(currentGoal, that.currentGoal) && 
               Objects.equals(activeSubgoal, that.activeSubgoal) &&
               Objects.equals(currentAction, that.currentAction) &&
               Objects.equals(lastActionResult, that.lastActionResult) &&
               Objects.equals(recentActions, that.recentActions) && 
               Objects.equals(agentState, that.agentState) && 
               Objects.equals(navigationState, that.navigationState) &&
               Objects.equals(ownerName, that.ownerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z, dimension, biome, dayTime, isNight, isRaining, isThundering, 
                            health, maxHealth, lightLevel, inventorySummary, equipmentSummary, 
                            inventoryUsedSlots, inventoryCapacity,
                            mainHandSummary, nearbyPlayers, nearbyEntities, nearbyBlocks, 
                            nearbyThreats, nearbyContainers, nearbyDroppedItems, relevantMemory,
                            protectedPositions, currentGoal, activeSubgoal, currentAction,
                            lastActionResult, recentActions, agentState, navigationState,
                            ownerName, distanceToOwner, capturedAtTick);
    }

    // Getters
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getDimension() { return dimension; }
    public String getBiome() { return biome; }
    public long getDayTime() { return dayTime; }
    public boolean isNight() { return isNight; }
    public boolean isRaining() { return isRaining; }
    public boolean isThundering() { return isThundering; }
    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public int getLightLevel() { return lightLevel; }
    public String getInventorySummary() { return inventorySummary; }
    public String getEquipmentSummary() { return equipmentSummary; }
    public int getInventoryUsedSlots() { return inventoryUsedSlots; }
    public int getInventoryCapacity() { return inventoryCapacity; }
    public String getMainHandSummary() { return mainHandSummary; }
    public List<String> getNearbyPlayers() { return nearbyPlayers; }
    public List<String> getNearbyEntities() { return nearbyEntities; }
    public List<String> getNearbyBlocks() { return nearbyBlocks; }
    public List<String> getNearbyThreats() { return nearbyThreats; }
    public List<String> getNearbyContainers() { return nearbyContainers; }
    public List<String> getNearbyDroppedItems() { return nearbyDroppedItems; }
    public List<String> getRelevantMemory() { return relevantMemory; }
    public List<String> getProtectedPositions() { return protectedPositions; }
    public String getCurrentGoal() { return currentGoal; }
    public String getActiveSubgoal() { return activeSubgoal; }
    public String getCurrentAction() { return currentAction; }
    public String getLastActionResult() { return lastActionResult; }
    public List<String> getRecentActions() { return recentActions; }
    public String getAgentState() { return agentState; }
    public String getNavigationState() { return navigationState; }
    public String getOwnerName() { return ownerName; }
    public double getDistanceToOwner() { return distanceToOwner; }
    public long getCapturedAtTick() { return capturedAtTick; }

    /** Returns a mutable builder snapshot without exposing the internal lists. */
    public Builder toBuilder() {
        return new Builder()
            .x(x).y(y).z(z).dimension(dimension).biome(biome)
            .dayTime(dayTime).isNight(isNight).isRaining(isRaining).isThundering(isThundering)
            .health(health).maxHealth(maxHealth).lightLevel(lightLevel)
            .inventorySummary(inventorySummary).equipmentSummary(equipmentSummary)
            .inventoryUsedSlots(inventoryUsedSlots).inventoryCapacity(inventoryCapacity)
            .mainHandSummary(mainHandSummary).nearbyPlayers(nearbyPlayers)
            .nearbyEntities(nearbyEntities).nearbyBlocks(nearbyBlocks).nearbyThreats(nearbyThreats)
            .nearbyContainers(nearbyContainers).nearbyDroppedItems(nearbyDroppedItems)
            .relevantMemory(relevantMemory).protectedPositions(protectedPositions)
            .currentGoal(currentGoal).activeSubgoal(activeSubgoal).currentAction(currentAction)
            .lastActionResult(lastActionResult).recentActions(recentActions).agentState(agentState)
            .navigationState(navigationState).ownerName(ownerName)
            .distanceToOwner(distanceToOwner).capturedAtTick(capturedAtTick);
    }

    public static class Builder {
        private int x, y, z;
        private String dimension = "unknown";
        private String biome = "unknown";
        private long dayTime;
        private boolean isNight;
        private boolean isRaining;
        private boolean isThundering;
        private float health = 20.0f;
        private float maxHealth = 20.0f;
        private int lightLevel;
        private String inventorySummary = "";
        private String equipmentSummary = "";
        private String mainHandSummary = "";
        private int inventoryUsedSlots;
        private int inventoryCapacity;
        private List<String> nearbyPlayers = new ArrayList<>();
        private List<String> nearbyEntities = new ArrayList<>();
        private List<String> nearbyBlocks = new ArrayList<>();
        private List<String> nearbyThreats = new ArrayList<>();
        private List<String> nearbyContainers = new ArrayList<>();
        private List<String> nearbyDroppedItems = new ArrayList<>();
        private List<String> relevantMemory = new ArrayList<>();
        private List<String> protectedPositions = new ArrayList<>();
        private String currentGoal = "";
        private String activeSubgoal = "";
        private String currentAction = "";
        private String lastActionResult = "";
        private List<String> recentActions = new ArrayList<>();
        private String agentState = "";
        private String navigationState = "";
        private String ownerName;
        private double distanceToOwner = -1.0;
        private long capturedAtTick;

        public Builder x(int x) { this.x = x; return this; }
        public Builder y(int y) { this.y = y; return this; }
        public Builder z(int z) { this.z = z; return this; }
        public Builder dimension(String dimension) { this.dimension = dimension; return this; }
        public Builder biome(String biome) { this.biome = biome; return this; }
        public Builder dayTime(long dayTime) { this.dayTime = dayTime; return this; }
        public Builder isNight(boolean isNight) { this.isNight = isNight; return this; }
        public Builder isRaining(boolean isRaining) { this.isRaining = isRaining; return this; }
        public Builder isThundering(boolean isThundering) { this.isThundering = isThundering; return this; }
        public Builder health(float health) { this.health = health; return this; }
        public Builder maxHealth(float maxHealth) { this.maxHealth = maxHealth; return this; }
        public Builder lightLevel(int lightLevel) { this.lightLevel = lightLevel; return this; }
        public Builder inventorySummary(String inventorySummary) { this.inventorySummary = inventorySummary; return this; }
        public Builder equipmentSummary(String equipmentSummary) { this.equipmentSummary = equipmentSummary; return this; }
        public Builder inventoryUsedSlots(int inventoryUsedSlots) { this.inventoryUsedSlots = Math.max(0, inventoryUsedSlots); return this; }
        public Builder inventoryCapacity(int inventoryCapacity) { this.inventoryCapacity = Math.max(0, inventoryCapacity); return this; }
        public Builder mainHandSummary(String mainHandSummary) { this.mainHandSummary = mainHandSummary; return this; }
        public Builder nearbyPlayers(List<String> nearbyPlayers) { this.nearbyPlayers = nearbyPlayers; return this; }
        public Builder nearbyEntities(List<String> nearbyEntities) { this.nearbyEntities = nearbyEntities; return this; }
        public Builder nearbyBlocks(List<String> nearbyBlocks) { this.nearbyBlocks = nearbyBlocks; return this; }
        public Builder nearbyThreats(List<String> nearbyThreats) { this.nearbyThreats = nearbyThreats; return this; }
        public Builder nearbyContainers(List<String> nearbyContainers) { this.nearbyContainers = nearbyContainers; return this; }
        public Builder nearbyDroppedItems(List<String> nearbyDroppedItems) { this.nearbyDroppedItems = boundedList(nearbyDroppedItems, 12); return this; }
        public Builder relevantMemory(List<String> relevantMemory) { this.relevantMemory = boundedList(relevantMemory, 12); return this; }
        public Builder protectedPositions(List<String> protectedPositions) { this.protectedPositions = boundedList(protectedPositions, 12); return this; }
        public Builder currentGoal(String currentGoal) { this.currentGoal = currentGoal; return this; }
        public Builder activeSubgoal(String activeSubgoal) { this.activeSubgoal = activeSubgoal; return this; }
        public Builder currentAction(String currentAction) { this.currentAction = currentAction; return this; }
        public Builder lastActionResult(String lastActionResult) { this.lastActionResult = lastActionResult; return this; }
        public Builder recentActions(List<String> recentActions) { this.recentActions = recentActions; return this; }
        public Builder agentState(String agentState) { this.agentState = agentState; return this; }
        public Builder navigationState(String navigationState) { this.navigationState = navigationState; return this; }
        public Builder ownerName(String ownerName) { this.ownerName = ownerName; return this; }
        public Builder distanceToOwner(double distanceToOwner) { this.distanceToOwner = distanceToOwner; return this; }
        public Builder capturedAtTick(long capturedAtTick) { this.capturedAtTick = capturedAtTick; return this; }

        public ObservationSnapshot build() {
            return new ObservationSnapshot(this);
        }

        private static List<String> boundedList(List<String> values, int max) {
            if (values == null) return new ArrayList<>();
            return values.stream().filter(java.util.Objects::nonNull).limit(max).toList();
        }
    }
}
