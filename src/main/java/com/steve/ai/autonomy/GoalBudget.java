package com.steve.ai.autonomy;

import net.minecraft.nbt.CompoundTag;

/** Mutable per-goal counters that cap autonomous work and provider spending. */
public final class GoalBudget {
    public static final int DATA_VERSION = 1;
    public static final int DEFAULT_MAX_RETRIES_PER_STEP = 3;
    public static final int DEFAULT_MAX_REPLANS = 8;
    public static final int DEFAULT_MAX_LLM_CALLS = 12;
    public static final int DEFAULT_MAX_CONSECUTIVE_FAILURES = 5;
    public static final int DEFAULT_MAX_REPEATED_FAILURE_FINGERPRINT = 2;

    private final int maxRetriesPerStep;
    private final int maxReplans;
    private final int maxLlmCalls;
    private final int maxConsecutiveFailures;
    private final int maxRepeatedFailureFingerprint;
    private int llmCalls;
    private int consecutiveFailures;

    public GoalBudget() {
        this(DEFAULT_MAX_RETRIES_PER_STEP, DEFAULT_MAX_REPLANS, DEFAULT_MAX_LLM_CALLS,
            DEFAULT_MAX_CONSECUTIVE_FAILURES, DEFAULT_MAX_REPEATED_FAILURE_FINGERPRINT);
    }

    public GoalBudget(int maxRetriesPerStep, int maxReplans, int maxLlmCalls,
            int maxConsecutiveFailures, int maxRepeatedFailureFingerprint) {
        this.maxRetriesPerStep = bounded(maxRetriesPerStep, 0, 64);
        this.maxReplans = bounded(maxReplans, 0, 128);
        this.maxLlmCalls = bounded(maxLlmCalls, 0, 256);
        this.maxConsecutiveFailures = bounded(maxConsecutiveFailures, 1, 64);
        this.maxRepeatedFailureFingerprint = bounded(maxRepeatedFailureFingerprint, 1, 32);
    }

    public int getMaxRetriesPerStep() { return maxRetriesPerStep; }
    public int getMaxReplans() { return maxReplans; }
    public int getMaxLlmCalls() { return maxLlmCalls; }
    public int getMaxConsecutiveFailures() { return maxConsecutiveFailures; }
    public int getMaxRepeatedFailureFingerprint() { return maxRepeatedFailureFingerprint; }
    public int getLlmCalls() { return llmCalls; }
    public int getConsecutiveFailures() { return consecutiveFailures; }

    public boolean canCallLlm() { return llmCalls < maxLlmCalls; }
    public boolean canReplan(int currentReplans) { return currentReplans < maxReplans; }
    public void recordLlmCall() { llmCalls = Math.min(maxLlmCalls, llmCalls + 1); }
    public void recordFailure() { consecutiveFailures = Math.min(maxConsecutiveFailures, consecutiveFailures + 1); }
    public void recordProgress() { consecutiveFailures = 0; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", DATA_VERSION);
        tag.putInt("MaxRetriesPerStep", maxRetriesPerStep);
        tag.putInt("MaxReplans", maxReplans);
        tag.putInt("MaxLlmCalls", maxLlmCalls);
        tag.putInt("MaxConsecutiveFailures", maxConsecutiveFailures);
        tag.putInt("MaxRepeatedFailureFingerprint", maxRepeatedFailureFingerprint);
        tag.putInt("LlmCalls", llmCalls);
        tag.putInt("ConsecutiveFailures", consecutiveFailures);
        return tag;
    }

    public static GoalBudget load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return new GoalBudget();
        }
        GoalBudget budget = new GoalBudget(
            tag.contains("MaxRetriesPerStep") ? tag.getInt("MaxRetriesPerStep") : DEFAULT_MAX_RETRIES_PER_STEP,
            tag.contains("MaxReplans") ? tag.getInt("MaxReplans") : DEFAULT_MAX_REPLANS,
            tag.contains("MaxLlmCalls") ? tag.getInt("MaxLlmCalls") : DEFAULT_MAX_LLM_CALLS,
            tag.contains("MaxConsecutiveFailures") ? tag.getInt("MaxConsecutiveFailures") : DEFAULT_MAX_CONSECUTIVE_FAILURES,
            tag.contains("MaxRepeatedFailureFingerprint")
                ? tag.getInt("MaxRepeatedFailureFingerprint") : DEFAULT_MAX_REPEATED_FAILURE_FINGERPRINT);
        budget.llmCalls = Math.min(budget.maxLlmCalls, Math.max(0, tag.getInt("LlmCalls")));
        budget.consecutiveFailures = Math.min(budget.maxConsecutiveFailures,
            Math.max(0, tag.getInt("ConsecutiveFailures")));
        return budget;
    }

    private static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
