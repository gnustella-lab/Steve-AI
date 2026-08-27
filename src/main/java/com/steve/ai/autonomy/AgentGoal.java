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
    public static final int DATA_VERSION = 2;
    private static final int MAX_DESCRIPTION_LENGTH = GoalIntent.MAX_DESCRIPTION_LENGTH;
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
    private GoalConstraints constraints;
    private final GoalBudget budget;
    private GoalIntent intent;
    private GoalProgress progress;
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
        this.createdAt = Math.max(0L, createdAt);
        this.updatedAt = Math.max(this.createdAt, updatedAt);
        this.constraints = constraints == null ? GoalConstraints.empty() : constraints;
        this.budget = budget == null ? new GoalBudget() : budget.copy();
        this.intent = GoalIntent.of(this.description);
        this.progress = new GoalProgress(0, 0, 0, 0,
            attemptCount, replanCount, 0, this.updatedAt);
        if (this.constraints.targetQuantity() > 0) {
            this.progress.setTargetUnits(this.constraints.targetQuantity());
        }
    }

    public static AgentGoal create(String description, GoalOrigin origin, GoalPriority priority,
            UUID parentGoalId, long now) {
        return create(description, origin, priority, parentGoalId, now, new GoalBudget());
    }

    /** Creates a goal with a detached snapshot of the executive's configured limits. */
    public static AgentGoal create(String description, GoalOrigin origin, GoalPriority priority,
            UUID parentGoalId, long now, GoalBudget configuredBudget) {
        return new AgentGoal(UUID.randomUUID(), description, origin, priority,
            GoalStatus.PENDING, parentGoalId, now, now, 0, 0,
            GoalConstraints.empty(), configuredBudget);
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public GoalOrigin getOrigin() { return origin; }
    public GoalPriority getPriority() { return priority; }
    public GoalStatus getStatus() { return status; }
    public UUID getParentGoalId() { return parentGoalId; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public int getAttemptCount() { return progress.getAttemptCount(); }
    public int getReplanCount() { return progress.getReplanCount(); }
    public GoalConstraints getConstraints() { return constraints; }
    public GoalBudget getBudget() { return budget; }
    public GoalIntent getIntent() { return intent; }
    public GoalProgress getProgress() { return progress; }

    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public void setIntent(GoalIntent intent) {
        this.intent = intent == null ? GoalIntent.of(description) : intent;
        touch(updatedAt);
    }

    public void setProgress(GoalProgress progress) {
        this.progress = progress == null ? new GoalProgress() : progress.copy();
        touch(this.progress.getLastProgressTick());
    }

    public void setConstraints(GoalConstraints constraints) {
        this.constraints = constraints == null ? GoalConstraints.empty() : constraints;
        if (this.constraints.targetQuantity() > 0) {
            this.progress.setTargetUnits(this.constraints.targetQuantity());
        }
        touch(updatedAt);
    }

    public void putMetadata(String key, Object value) {
        if (key == null || key.isBlank()) {
            return;
        }
        String normalizedKey = bounded(key.trim(), 64);
        if (metadata.size() >= MAX_METADATA_ENTRIES && !metadata.containsKey(normalizedKey)) {
            return;
        }
        if (value == null) {
            metadata.remove(normalizedKey);
        } else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            metadata.put(normalizedKey, bounded(String.valueOf(value), MAX_METADATA_VALUE_LENGTH));
        }
    }

    public boolean activate(long now) {
        if (!status.isAutoResumable()) return false;
        status = GoalStatus.ACTIVE;
        touch(now);
        return true;
    }

    public boolean pause(long now) {
        if (!status.isAutoResumable()) return false;
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

    public void recordProgress(int completedUnits, int targetUnits, long now) {
        progress.record(completedUnits, targetUnits, now);
        touch(now);
    }

    public void recordStepProgress(int completedSteps, int totalSteps, long now) {
        progress.recordStepCompletion(completedSteps, totalSteps, now);
        touch(now);
    }

    public void incrementAttempt() {
        progress.recordAttempt(updatedAt);
        touch(updatedAt);
    }

    public void incrementReplan() {
        progress.recordReplan(updatedAt);
        touch(updatedAt);
    }

    public boolean isTerminal() { return status.isTerminal(); }

    public boolean canAutoResume() { return status.isAutoResumable(); }

    private void touch(long now) {
        updatedAt = Math.max(updatedAt, Math.max(0L, now));
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
        tag.putInt("AttemptCount", progress.getAttemptCount());
        tag.putInt("ReplanCount", progress.getReplanCount());
        tag.put("Intent", intent.save());
        tag.put("Progress", progress.save());
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

        if (tag.contains("Intent", Tag.TAG_COMPOUND)) {
            goal.intent = GoalIntent.load(tag.getCompound("Intent"));
        }
        if (tag.contains("Progress", Tag.TAG_COMPOUND)) {
            goal.progress = GoalProgress.load(tag.getCompound("Progress"));
        }
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
