package com.steve.ai.autonomy;

import net.minecraft.nbt.CompoundTag;

/**
 * Bounded primitive progress counters owned by a goal.
 *
 * <p>Progress deliberately has no arbitrary map/blob field. A restart can
 * therefore restore diagnostics without restoring a stale action continuation.</p>
 */
public final class GoalProgress {
    public static final int DATA_VERSION = 1;
    public static final int MAX_COUNTER = 1_000_000;

    private int completedUnits;
    private int targetUnits;
    private int completedSteps;
    private int totalSteps;
    private int attemptCount;
    private int replanCount;
    private int failureCount;
    private long lastProgressTick;

    public GoalProgress() {
        this(0, 0, 0, 0, 0, 0, 0, 0L);
    }

    public GoalProgress(int targetUnits) {
        this(0, targetUnits, 0, 0, 0, 0, 0, 0L);
    }

    public GoalProgress(int completedUnits, int targetUnits, int completedSteps, int totalSteps,
            int attemptCount, int replanCount, int failureCount, long lastProgressTick) {
        this.completedUnits = bounded(completedUnits);
        this.targetUnits = bounded(targetUnits);
        this.completedSteps = bounded(completedSteps);
        this.totalSteps = bounded(totalSteps);
        this.attemptCount = bounded(attemptCount);
        this.replanCount = bounded(replanCount);
        this.failureCount = bounded(failureCount);
        this.lastProgressTick = Math.max(0L, lastProgressTick);
    }

    public int getCompletedUnits() { return completedUnits; }
    public int getTargetUnits() { return targetUnits; }
    public int getCompletedSteps() { return completedSteps; }
    public int getTotalSteps() { return totalSteps; }
    public int getAttemptCount() { return attemptCount; }
    public int getReplanCount() { return replanCount; }
    public int getFailureCount() { return failureCount; }
    public long getLastProgressTick() { return lastProgressTick; }

    public int completedUnits() { return completedUnits; }
    public int targetUnits() { return targetUnits; }
    public int completedSteps() { return completedSteps; }
    public int totalSteps() { return totalSteps; }
    public int attemptCount() { return attemptCount; }
    public int replanCount() { return replanCount; }
    public int failureCount() { return failureCount; }
    public long lastProgressTick() { return lastProgressTick; }

    public void setTargetUnits(int targetUnits) {
        this.targetUnits = bounded(targetUnits);
        if (completedUnits > this.targetUnits && this.targetUnits > 0) {
            completedUnits = this.targetUnits;
        }
    }

    public void setTotalSteps(int totalSteps) {
        this.totalSteps = bounded(totalSteps);
        if (completedSteps > this.totalSteps && this.totalSteps > 0) {
            completedSteps = this.totalSteps;
        }
    }

    public void record(int completedUnits, int targetUnits, long tick) {
        this.targetUnits = bounded(targetUnits);
        this.completedUnits = bounded(completedUnits);
        if (this.targetUnits > 0) {
            this.completedUnits = Math.min(this.completedUnits, this.targetUnits);
        }
        touch(tick);
    }

    public void recordStepCompletion(int completedSteps, int totalSteps, long tick) {
        this.totalSteps = bounded(totalSteps);
        this.completedSteps = bounded(completedSteps);
        if (this.totalSteps > 0) {
            this.completedSteps = Math.min(this.completedSteps, this.totalSteps);
        }
        touch(tick);
    }

    public void recordAttempt(long tick) {
        attemptCount = increment(attemptCount);
        touch(tick);
    }

    public void recordReplan(long tick) {
        replanCount = increment(replanCount);
        touch(tick);
    }

    public void recordFailure(long tick) {
        failureCount = increment(failureCount);
        touch(tick);
    }

    /** Returns true only when an explicit unit/evidence target has been reached. */
    public boolean isComplete() {
        return isUnitTargetComplete();
    }

    public boolean isUnitTargetComplete() {
        return targetUnits > 0 && completedUnits >= targetUnits;
    }

    /** Diagnostic only: a planning horizon can be exhausted without completing the goal. */
    public boolean isStepPlanComplete() {
        return totalSteps > 0 && completedSteps >= totalSteps;
    }

    public GoalProgress copy() {
        return new GoalProgress(completedUnits, targetUnits, completedSteps, totalSteps,
            attemptCount, replanCount, failureCount, lastProgressTick);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", DATA_VERSION);
        tag.putInt("CompletedUnits", completedUnits);
        tag.putInt("TargetUnits", targetUnits);
        tag.putInt("CompletedSteps", completedSteps);
        tag.putInt("TotalSteps", totalSteps);
        tag.putInt("AttemptCount", attemptCount);
        tag.putInt("ReplanCount", replanCount);
        tag.putInt("FailureCount", failureCount);
        tag.putLong("LastProgressTick", lastProgressTick);
        return tag;
    }

    public static GoalProgress load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return new GoalProgress();
        }
        return new GoalProgress(
            tag.getInt("CompletedUnits"),
            tag.getInt("TargetUnits"),
            tag.getInt("CompletedSteps"),
            tag.getInt("TotalSteps"),
            tag.getInt("AttemptCount"),
            tag.getInt("ReplanCount"),
            tag.getInt("FailureCount"),
            tag.getLong("LastProgressTick"));
    }

    private static int bounded(int value) {
        return Math.max(0, Math.min(MAX_COUNTER, value));
    }

    private static int increment(int value) {
        return value >= MAX_COUNTER ? MAX_COUNTER : value + 1;
    }

    private void touch(long tick) {
        lastProgressTick = Math.max(lastProgressTick, Math.max(0L, tick));
    }
}
