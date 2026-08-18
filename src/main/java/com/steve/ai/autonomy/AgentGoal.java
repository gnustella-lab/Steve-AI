package com.steve.ai.autonomy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent user or agent objective. Execution state is intentionally separate from this model.
 */
public final class AgentGoal {
    public static final int DATA_VERSION = 1;
    private static final int MAX_DESCRIPTION_LENGTH = 512;
    private static final int MAX_METADATA_ENTRIES = 32;
    private static final int MAX_METADATA_VALUE_LENGTH = 256;

    private final UUID id;
    private final String description;
    private final GoalOrigin origin;
    private final GoalPriority priority;
    private final UUID parentGoalId;
    private final long createdAt;
    private long updatedAt;
    private GoalStatus status;
    private int attemptCount;
    private int replanCount;
    private GoalConstraints constraints;
    private final GoalBudget budget;
    private final Map<String, Object> metadata = new LinkedHashMap<>();

    private AgentGoal(UUID id, String description, GoalOrigin origin, GoalPriority priority,
            GoalStatus status, UUID parentGoalId, long createdAt, long updatedAt,
            int attemptCount, int replanCount, GoalConstraints constraints, GoalBudget budget) {
        this.id = Objects.requireNonNull(id, "id");
        this.description = bounded(description, MAX_DESCRIPTION_LENGTH);
        this.origin = Objects.requireNonNull(origin, "origin");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.status = Objects.requireNonNull(status, "status");
        this.parentGoalId = parentGoalId;
        this.createdAt = createdAt;
        this.updatedAt = Math.max(createdAt, updatedAt);
        this.attemptCount = Math.max(0, attemptCount);
        this.replanCount = Math.max(0, replanCount);
        this.constraints = constraints == null ? GoalConstraints.empty() : constraints;
        this.budget = budget == null ? new GoalBudget() : budget;
    }

    public static AgentGoal create(String description, GoalOrigin origin, GoalPriority priority,
            UUID parentGoalId, long now) {
        return new AgentGoal(UUID.randomUUID(), description, origin, priority,
            GoalStatus.PENDING, parentGoalId, now, now, 0, 0,
            GoalConstraints.empty(), new GoalBudget());
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public GoalOrigin getOrigin() { return origin; }
    public GoalPriority getPriority() { return priority; }
    public GoalStatus getStatus() { return status; }
    public UUID getParentGoalId() { return parentGoalId; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public int getAttemptCount() { return attemptCount; }
    public int getReplanCount() { return replanCount; }
    public GoalConstraints getConstraints() { return constraints; }
    public GoalBudget getBudget() { return budget; }
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public void setConstraints(GoalConstraints constraints) {
        this.constraints = constraints == null ? GoalConstraints.empty() : constraints;
        touch(updatedAt);
    }

    public void putMetadata(String key, Object value) {
        if (key == null || key.isBlank() || metadata.size() >= MAX_METADATA_ENTRIES && !metadata.containsKey(key)) {
            return;
        }
        String normalizedKey = bounded(key.trim(), 64);
        if (value == null) {
            metadata.remove(normalizedKey);
        } else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            metadata.put(normalizedKey, bounded(String.valueOf(value), MAX_METADATA_VALUE_LENGTH));
        }
    }

    public boolean activate(long now) {
        if (status.isTerminal()) return false;
        status = GoalStatus.ACTIVE;
        touch(now);
        return true;
    }

    public boolean pause(long now) {
        if (status.isTerminal()) return false;
        status = GoalStatus.PAUSED;
        touch(now);
        return true;
    }

    public boolean block(String reason, long now) {
        if (status.isTerminal()) return false;
        status = GoalStatus.BLOCKED;
        if (reason != null && !reason.isBlank()) putMetadata("blockedReason", reason);
        touch(now);
        return true;
    }

    public boolean complete(long now) {
        if (status.isTerminal()) return false;
        status = GoalStatus.COMPLETED;
        touch(now);
        return true;
    }

    public boolean fail(String reason, long now) {
        if (status.isTerminal()) return false;
        status = GoalStatus.FAILED;
        if (reason != null && !reason.isBlank()) putMetadata("failureReason", reason);
        touch(now);
        return true;
    }

    public boolean cancel(long now) {
        if (status.isTerminal()) return false;
        status = GoalStatus.CANCELLED;
        touch(now);
        return true;
    }

    public void incrementAttempt() {
        attemptCount = Math.min(Integer.MAX_VALUE, attemptCount + 1);
        touch(updatedAt);
    }

    public void incrementReplan() {
        replanCount = Math.min(Integer.MAX_VALUE, replanCount + 1);
        touch(updatedAt);
    }

    public boolean isTerminal() { return status.isTerminal(); }

    private void touch(long now) {
        updatedAt = Math.max(updatedAt, now);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", DATA_VERSION);
        tag.putUUID("Id", id);
        tag.putString("Description", description);
        tag.putString("Origin", origin.name());
        tag.putString("Priority", priority.name());
        tag.putString("Status", status.name());
        if (parentGoalId != null) tag.putUUID("ParentGoalId", parentGoalId);
        tag.putLong("CreatedAt", createdAt);
        tag.putLong("UpdatedAt", updatedAt);
        tag.putInt("AttemptCount", attemptCount);
        tag.putInt("ReplanCount", replanCount);
        tag.put("Constraints", constraints.save());
        tag.put("Budget", budget.save());

        ListTag metadataTag = new ListTag();
        metadata.entrySet().stream().limit(MAX_METADATA_ENTRIES).forEach(entry -> {
            CompoundTag item = new CompoundTag();
            item.putString("Key", bounded(entry.getKey(), 64));
            item.putString("Value", bounded(String.valueOf(entry.getValue()), MAX_METADATA_VALUE_LENGTH));
            metadataTag.add(item);
        });
        tag.put("Metadata", metadataTag);
        return tag;
    }

    public static AgentGoal load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return create("Restored goal", GoalOrigin.USER, GoalPriority.USER, null, 0L);
        }
        AgentGoal goal = new AgentGoal(
            tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(),
            tag.getString("Description"),
            readEnum(tag.getString("Origin"), GoalOrigin.USER),
            readEnum(tag.getString("Priority"), GoalPriority.USER),
            readEnum(tag.getString("Status"), GoalStatus.PAUSED),
            tag.hasUUID("ParentGoalId") ? tag.getUUID("ParentGoalId") : null,
            tag.getLong("CreatedAt"),
            tag.contains("UpdatedAt") ? tag.getLong("UpdatedAt") : tag.getLong("CreatedAt"),
            tag.getInt("AttemptCount"),
            tag.getInt("ReplanCount"),
            tag.contains("Constraints", Tag.TAG_COMPOUND)
                ? GoalConstraints.load(tag.getCompound("Constraints")) : GoalConstraints.empty(),
            tag.contains("Budget", Tag.TAG_COMPOUND)
                ? GoalBudget.load(tag.getCompound("Budget")) : new GoalBudget());

        if (tag.contains("Metadata", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Metadata", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(list.size(), MAX_METADATA_ENTRIES); i++) {
                CompoundTag item = list.getCompound(i);
                goal.putMetadata(item.getString("Key"), item.getString("Value"));
            }
        }
        return goal;
    }

    private static <E extends Enum<E>> E readEnum(String value, E fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Enum.valueOf(fallback.getDeclaringClass(), value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String bounded(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
