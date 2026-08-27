package com.steve.ai.planning;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A bounded planning horizon. The plan owns step progress, while ActionExecutor only runs a step.
 */
public final class Plan {
    public static final int DATA_VERSION = 2;
    public static final int MAX_STEPS = 64;
    public static final int MAX_COMPLETED_STEP_SUMMARIES = 32;
    public static final int MAX_COUNTER = 1_000_000;
    private static final int MAX_COMMAND_LENGTH = 512;
    private static final int MAX_SUMMARY_LENGTH = 256;
    private static final int MAX_REASON_LENGTH = 256;
    private static final int MAX_PARAMETERS_JSON_LENGTH = 4_096;

    public enum State {
        CREATED, PLANNING, EXECUTING, PAUSED, COMPLETED, FAILED, CANCELLED
    }

    /** Compact terminal-step diagnostic retained when a horizon is replaced. */
    public static final class CompletedStepSummary {
        private final UUID stepId;
        private final String action;
        private final PlanStep.Status status;
        private final int attempts;
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final long updatedAt;

        private CompletedStepSummary(UUID stepId, String action, PlanStep.Status status,
                int attempts, boolean success, String errorCode, String message, long updatedAt) {
            this.stepId = stepId;
            this.action = bounded(action, 128);
            this.status = status == null ? PlanStep.Status.COMPLETED : status;
            this.attempts = bounded(attempts);
            this.success = success;
            this.errorCode = bounded(errorCode, 64);
            this.message = bounded(message, MAX_SUMMARY_LENGTH);
            this.updatedAt = Math.max(0L, updatedAt);
        }

        private static CompletedStepSummary from(PlanStep step) {
            ActionResult result = step.getLastResult();
            return new CompletedStepSummary(step.getStepId(), step.getTask().getAction(),
                step.getStatus(), step.getAttempts(), result != null && result.isSuccess(),
                result == null ? null : result.getErrorCode(),
                result == null ? "" : result.getMessage(), step.getUpdatedAt());
        }

        public UUID getStepId() { return stepId; }
        public String getAction() { return action; }
        public PlanStep.Status getStatus() { return status; }
        public int getAttempts() { return attempts; }
        public boolean isSuccess() { return success; }
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public long getUpdatedAt() { return updatedAt; }
        public UUID stepId() { return stepId; }
        public String action() { return action; }
        public PlanStep.Status status() { return status; }
        public int attempts() { return attempts; }
        public boolean success() { return success; }
        public String errorCode() { return errorCode; }
        public String message() { return message; }
        public long updatedAt() { return updatedAt; }
    }

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private final UUID planId;
    private final UUID goalId;
    private final String originalCommand;
    private final UUID requestingPlayer;
    private final UUID steveUuid;
    private State state;
    private final List<PlanStep> steps;
    private final List<CompletedStepSummary> completedStepSummaries;
    private int currentTaskIndex;
    private int attemptCount;
    private int replanCount;
    private int llmCallCount;
    private int revision;
    private final long createdAtTick;
    private long lastProgressTick;
    private String failureReason;
    private String summary;
    private String replanReason;

    private final int maxRetries;
    private final int maxReplans;
    private final int maxLLMCalls;
    private final int timeoutTicks;

    public Plan(String originalCommand, UUID requestingPlayer, UUID steveUuid,
            int maxRetries, int maxReplans, int maxLLMCalls, int timeoutTicks, long createdAtTick) {
        this(UUID.randomUUID(), null, originalCommand, requestingPlayer, steveUuid,
            maxRetries, maxReplans, maxLLMCalls, timeoutTicks, createdAtTick);
    }

    public Plan(UUID goalId, String originalCommand, UUID requestingPlayer, UUID steveUuid,
            int maxRetries, int maxReplans, int maxLLMCalls, int timeoutTicks, long createdAtTick) {
        this(UUID.randomUUID(), goalId, originalCommand, requestingPlayer, steveUuid,
            maxRetries, maxReplans, maxLLMCalls, timeoutTicks, createdAtTick);
    }

    /** Rehydrates or continues a known plan identity without creating a second authority. */
    public Plan(UUID planId, UUID goalId, String originalCommand, UUID requestingPlayer,
            UUID steveUuid, int maxRetries, int maxReplans, int maxLLMCalls,
            int timeoutTicks, long createdAtTick) {
        this(planId, goalId, originalCommand, requestingPlayer, steveUuid,
            maxRetries, maxReplans, maxLLMCalls, timeoutTicks, createdAtTick, true);
    }

    private Plan(UUID planId, UUID goalId, String originalCommand, UUID requestingPlayer, UUID steveUuid,
            int maxRetries, int maxReplans, int maxLLMCalls, int timeoutTicks, long createdAtTick,
            boolean ignored) {
        this.planId = planId == null ? UUID.randomUUID() : planId;
        this.goalId = goalId;
        this.originalCommand = bounded(originalCommand, MAX_COMMAND_LENGTH);
        this.requestingPlayer = requestingPlayer;
        this.steveUuid = steveUuid;
        this.state = State.CREATED;
        this.steps = new ArrayList<>();
        this.completedStepSummaries = new ArrayList<>();
        this.currentTaskIndex = 0;
        this.attemptCount = 0;
        this.replanCount = 0;
        this.llmCallCount = 0;
        this.revision = 0;
        this.createdAtTick = Math.max(0L, createdAtTick);
        this.lastProgressTick = this.createdAtTick;
        this.maxRetries = bounded(maxRetries, 0, 256);
        this.maxReplans = bounded(maxReplans, 0, 256);
        this.maxLLMCalls = bounded(maxLLMCalls, 0, 512);
        this.timeoutTicks = bounded(timeoutTicks, 0, 7_200_000);
    }

    public UUID getPlanId() { return planId; }
    public UUID getGoalId() { return goalId; }
    public String getOriginalCommand() { return originalCommand; }
    public UUID getRequestingPlayer() { return requestingPlayer; }
    public UUID getSteveUuid() { return steveUuid; }
    public State getState() { return state; }
    public List<Task> getTasks() { return steps.stream().map(PlanStep::getTask).toList(); }
    public List<PlanStep> getSteps() { return Collections.unmodifiableList(steps); }
    public List<CompletedStepSummary> getCompletedStepSummaries() {
        return Collections.unmodifiableList(new ArrayList<>(completedStepSummaries));
    }
    public List<CompletedStepSummary> getArchivedCompletedSteps() { return getCompletedStepSummaries(); }
    public List<CompletedStepSummary> getArchivedStepSummaries() { return getCompletedStepSummaries(); }
    public int getCurrentTaskIndex() { return currentTaskIndex; }
    public int getAttemptCount() { return attemptCount; }
    public int getReplanCount() { return replanCount; }
    public int getLlmCallCount() { return llmCallCount; }
    public int getRevision() { return revision; }
    public long getCreatedAtTick() { return createdAtTick; }
    public long getLastProgressTick() { return lastProgressTick; }
    public String getFailureReason() { return failureReason; }
    public String getSummary() { return summary; }
    public String getReplanReason() { return replanReason; }

    public void loadTasks(List<Task> newTasks, String newSummary) {
        archiveCompletedSteps();
        steps.clear();
        if (newTasks != null) {
            newTasks.stream().limit(MAX_STEPS).map(PlanStep::new).forEach(steps::add);
        }
        summary = bounded(newSummary, MAX_SUMMARY_LENGTH);
        currentTaskIndex = 0;
        attemptCount = 0;
        revision = increment(revision);
    }

    public void loadHorizon(List<Task> newTasks, String newSummary, String reason, long tick) {
        loadTasks(newTasks, newSummary);
        replanReason = bounded(reason, MAX_REASON_LENGTH);
        lastProgressTick = Math.max(lastProgressTick, Math.max(0L, tick));
        state = State.EXECUTING;
    }

    public Task getCurrentTask() {
        PlanStep step = getCurrentStep();
        return step == null ? null : step.getTask();
    }

    public PlanStep getCurrentStep() {
        return currentTaskIndex >= 0 && currentTaskIndex < steps.size()
            ? steps.get(currentTaskIndex) : null;
    }

    public void markCurrentStepActive() {
        markCurrentStepActive(lastProgressTick);
    }

    public void markCurrentStepActive(long tick) {
        PlanStep step = getCurrentStep();
        if (step != null) {
            step.markActive(tick);
            step.incrementAttempt(tick);
            attemptCount = step.getAttempts();
            lastProgressTick = Math.max(lastProgressTick, Math.max(0L, tick));
        }
    }

    public void recordCurrentStepResult(ActionResult result, long currentTick) {
        PlanStep step = getCurrentStep();
        if (step != null) {
            step.complete(result, currentTick);
            attemptCount = step.getAttempts();
            lastProgressTick = Math.max(lastProgressTick, Math.max(0L, currentTick));
        }
    }

    public void advanceToNextTask(long currentTick) {
        PlanStep step = getCurrentStep();
        if (step != null && (step.getStatus() == PlanStep.Status.PENDING
                || step.getStatus() == PlanStep.Status.ACTIVE)) {
            step.complete(ActionResult.success("step completed").build(), currentTick);
        }
        currentTaskIndex = Math.min(MAX_STEPS, currentTaskIndex + 1);
        attemptCount = 0;
        lastProgressTick = Math.max(lastProgressTick, Math.max(0L, currentTick));
        if (currentTaskIndex >= steps.size()) setState(State.COMPLETED);
    }

    public void incrementAttempt() {
        attemptCount = increment(attemptCount);
    }

    public boolean canRetry() { return attemptCount < maxRetries; }

    public void incrementReplan() {
        replanCount = increment(replanCount);
        revision = increment(revision);
    }

    public boolean canReplan() { return replanCount < maxReplans; }

    public void incrementLLMCall() { llmCallCount = increment(llmCallCount); }
    public boolean canCallLLM() { return llmCallCount < maxLLMCalls; }

    public boolean isTimedOut(long currentTick) {
        return timeoutTicks > 0 && currentTick >= lastProgressTick
            && currentTick - lastProgressTick > timeoutTicks;
    }

    public String getProgress() { return currentTaskIndex + "/" + steps.size() + " tasks"; }

    public void setState(State newState) {
        if (newState == null) throw new IllegalArgumentException("Plan state cannot be null");
        if (state == State.COMPLETED && newState == State.EXECUTING
                || state == State.FAILED && newState == State.EXECUTING
                || state == State.CANCELLED && newState == State.EXECUTING) {
            throw new IllegalStateException("Cannot resume a terminal plan");
        }
        state = newState;
    }

    public void setFailureReason(String reason) {
        failureReason = bounded(reason, MAX_REASON_LENGTH);
        setState(State.FAILED);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", DATA_VERSION);
        tag.putUUID("PlanId", planId);
        if (goalId != null) tag.putUUID("GoalId", goalId);
        tag.putString("OriginalCommand", originalCommand);
        if (requestingPlayer != null) tag.putUUID("RequestingPlayer", requestingPlayer);
        if (steveUuid != null) tag.putUUID("SteveUuid", steveUuid);
        tag.putString("State", state.name());
        tag.putInt("CurrentTaskIndex", Math.max(0, Math.min(currentTaskIndex, steps.size())));
        tag.putInt("AttemptCount", attemptCount);
        tag.putInt("ReplanCount", replanCount);
        tag.putInt("LlmCallCount", llmCallCount);
        tag.putInt("Revision", revision);
        tag.putLong("CreatedAtTick", createdAtTick);
        tag.putLong("LastProgressTick", lastProgressTick);
        tag.putInt("MaxRetries", maxRetries);
        tag.putInt("MaxReplans", maxReplans);
        tag.putInt("MaxLlmCalls", maxLLMCalls);
        tag.putInt("TimeoutTicks", timeoutTicks);
        if (failureReason != null) tag.putString("FailureReason", failureReason);
        if (summary != null) tag.putString("Summary", summary);
        if (replanReason != null) tag.putString("ReplanReason", replanReason);

        ListTag stepList = new ListTag();
        steps.stream().limit(MAX_STEPS).forEach(step -> stepList.add(step.save()));
        tag.put("Steps", stepList);

        ListTag archive = new ListTag();
        completedStepSummaries.stream().limit(MAX_COMPLETED_STEP_SUMMARIES)
            .forEach(summary -> archive.add(saveSummary(summary)));
        tag.put("CompletedStepSummaries", archive);

        // Keep a simple task list for saves written by the previous Plan implementation.
        ListTag taskList = new ListTag();
        for (PlanStep step : steps.stream().limit(MAX_STEPS).toList()) {
            CompoundTag taskTag = new CompoundTag();
            taskTag.putString("Action", bounded(step.getTask().getAction(), 128));
            taskTag.putString("Parameters", boundedJson(GSON.toJson(step.getTask().getParameters())));
            taskList.add(taskTag);
        }
        tag.put("Tasks", taskList);
        return tag;
    }

    public static Plan load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return new Plan("Restored plan", null, null, 3, 8, 12, 0, 0L);
        }
        UUID planId = tag.hasUUID("PlanId") ? tag.getUUID("PlanId")
            : tag.hasUUID("planId") ? tag.getUUID("planId") : UUID.randomUUID();
        UUID goalId = tag.hasUUID("GoalId") ? tag.getUUID("GoalId") : null;
        String command = tag.contains("OriginalCommand") ? tag.getString("OriginalCommand")
            : tag.getString("originalCommand");
        UUID requestingPlayer = tag.hasUUID("RequestingPlayer") ? tag.getUUID("RequestingPlayer")
            : tag.hasUUID("requestingPlayer") ? tag.getUUID("requestingPlayer") : null;
        UUID steveUuid = tag.hasUUID("SteveUuid") ? tag.getUUID("SteveUuid")
            : tag.hasUUID("steveUuid") ? tag.getUUID("steveUuid") : null;
        Plan plan = new Plan(planId, goalId, command, requestingPlayer, steveUuid,
            readInt(tag, "MaxRetries", "maxRetries", 3),
            readInt(tag, "MaxReplans", "maxReplans", 8),
            readInt(tag, "MaxLlmCalls", "maxLLMCalls", 12),
            readInt(tag, "TimeoutTicks", "timeoutTicks", 0),
            readLong(tag, "CreatedAtTick", "createdAtTick", 0L));

        plan.state = readState(tag.contains("State") ? tag.getString("State") : tag.getString("state"));
        plan.currentTaskIndex = bounded(readInt(tag, "CurrentTaskIndex", "currentTaskIndex", 0));
        plan.attemptCount = bounded(readInt(tag, "AttemptCount", "attemptCount", 0));
        plan.replanCount = bounded(readInt(tag, "ReplanCount", "replanCount", 0));
        plan.llmCallCount = bounded(readInt(tag, "LlmCallCount", "llmCallCount", 0));
        plan.revision = bounded(tag.getInt("Revision"));
        plan.lastProgressTick = Math.max(plan.createdAtTick,
            readLong(tag, "LastProgressTick", "lastProgressTick", plan.createdAtTick));
        plan.failureReason = tag.contains("FailureReason")
            ? bounded(tag.getString("FailureReason"), MAX_REASON_LENGTH) : null;
        plan.summary = tag.contains("Summary")
            ? bounded(tag.getString("Summary"), MAX_SUMMARY_LENGTH) : null;
        plan.replanReason = tag.contains("ReplanReason")
            ? bounded(tag.getString("ReplanReason"), MAX_REASON_LENGTH) : null;

        if (tag.contains("CompletedStepSummaries", Tag.TAG_LIST)) {
            ListTag list = tag.getList("CompletedStepSummaries", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_COMPLETED_STEP_SUMMARIES, list.size()); i++) {
                plan.completedStepSummaries.add(loadSummary(list.getCompound(i)));
            }
        }
        if (tag.contains("Steps", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Steps", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_STEPS, list.size()); i++) {
                plan.steps.add(PlanStep.load(list.getCompound(i)));
            }
        } else if (tag.contains("tasks", Tag.TAG_LIST)) {
            ListTag list = tag.getList("tasks", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_STEPS, list.size()); i++) {
                CompoundTag item = list.getCompound(i);
                plan.steps.add(new PlanStep(new Task(bounded(item.getString("action"), 128),
                    parseMap(item.getString("parameters")))));
            }
        } else if (tag.contains("Tasks", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Tasks", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_STEPS, list.size()); i++) {
                CompoundTag item = list.getCompound(i);
                plan.steps.add(new PlanStep(new Task(bounded(item.getString("Action"), 128),
                    parseMap(item.getString("Parameters")))));
            }
        }
        plan.currentTaskIndex = Math.min(plan.currentTaskIndex, plan.steps.size());
        return plan;
    }

    public String toSummary() {
        return String.format("Plan[%s]: %s - %s", state, getProgress(), summary != null ? summary : "No summary");
    }

    private void archiveCompletedSteps() {
        for (PlanStep step : steps) {
            if (step.getStatus() == PlanStep.Status.COMPLETED) {
                if (completedStepSummaries.size() >= MAX_COMPLETED_STEP_SUMMARIES) {
                    completedStepSummaries.remove(0);
                }
                completedStepSummaries.add(CompletedStepSummary.from(step));
            }
        }
    }

    private static CompoundTag saveSummary(CompletedStepSummary summary) {
        CompoundTag tag = new CompoundTag();
        if (summary.getStepId() != null) tag.putUUID("StepId", summary.getStepId());
        tag.putString("Action", summary.getAction());
        tag.putString("Status", summary.getStatus().name());
        tag.putInt("Attempts", summary.getAttempts());
        tag.putBoolean("Success", summary.isSuccess());
        if (summary.getErrorCode() != null) tag.putString("ErrorCode", summary.getErrorCode());
        tag.putString("Message", summary.getMessage());
        tag.putLong("UpdatedAt", summary.getUpdatedAt());
        return tag;
    }

    private static CompletedStepSummary loadSummary(CompoundTag tag) {
        return new CompletedStepSummary(
            tag.hasUUID("StepId") ? tag.getUUID("StepId") : null,
            tag.getString("Action"),
            readStepStatus(tag.getString("Status")),
            tag.getInt("Attempts"),
            tag.getBoolean("Success"),
            tag.contains("ErrorCode") ? tag.getString("ErrorCode") : null,
            tag.getString("Message"),
            tag.getLong("UpdatedAt"));
    }

    private static Map<String, Object> parseMap(String json) {
        if (json == null || json.length() > MAX_PARAMETERS_JSON_LENGTH) return Map.of();
        try {
            Map<String, Object> parsed = GSON.fromJson(json, MAP_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static State readState(String value) {
        try {
            return value == null || value.isBlank() ? State.CREATED : State.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return State.CREATED;
        }
    }

    private static PlanStep.Status readStepStatus(String value) {
        try {
            return value == null || value.isBlank() ? PlanStep.Status.COMPLETED : PlanStep.Status.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return PlanStep.Status.COMPLETED;
        }
    }

    private static int readInt(CompoundTag tag, String current, String legacy, int fallback) {
        if (tag.contains(current)) return tag.getInt(current);
        return tag.contains(legacy) ? tag.getInt(legacy) : fallback;
    }

    private static long readLong(CompoundTag tag, String current, String legacy, long fallback) {
        if (tag.contains(current)) return tag.getLong(current);
        return tag.contains(legacy) ? tag.getLong(legacy) : fallback;
    }

    private static int increment(int value) {
        return value >= MAX_COUNTER ? MAX_COUNTER : Math.max(0, value) + 1;
    }

    private static int bounded(int value) {
        return Math.max(0, Math.min(MAX_COUNTER, value));
    }

    private static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String boundedJson(String value) {
        return value != null && value.length() <= MAX_PARAMETERS_JSON_LENGTH ? value : "{}";
    }

    private static String bounded(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
