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
    public enum State {
        CREATED, PLANNING, EXECUTING, PAUSED, COMPLETED, FAILED, CANCELLED
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

    private Plan(UUID planId, UUID goalId, String originalCommand, UUID requestingPlayer, UUID steveUuid,
            int maxRetries, int maxReplans, int maxLLMCalls, int timeoutTicks, long createdAtTick) {
        this.planId = planId;
        this.goalId = goalId;
        this.originalCommand = bounded(originalCommand, 512);
        this.requestingPlayer = requestingPlayer;
        this.steveUuid = steveUuid;
        this.state = State.CREATED;
        this.steps = new ArrayList<>();
        this.currentTaskIndex = 0;
        this.attemptCount = 0;
        this.replanCount = 0;
        this.llmCallCount = 0;
        this.revision = 0;
        this.createdAtTick = createdAtTick;
        this.lastProgressTick = createdAtTick;
        this.maxRetries = Math.max(0, maxRetries);
        this.maxReplans = Math.max(0, maxReplans);
        this.maxLLMCalls = Math.max(0, maxLLMCalls);
        this.timeoutTicks = Math.max(0, timeoutTicks);
    }

    public UUID getPlanId() { return planId; }
    public UUID getGoalId() { return goalId; }
    public String getOriginalCommand() { return originalCommand; }
    public UUID getRequestingPlayer() { return requestingPlayer; }
    public UUID getSteveUuid() { return steveUuid; }
    public State getState() { return state; }
    public List<Task> getTasks() {
        return steps.stream().map(PlanStep::getTask).toList();
    }
    public List<PlanStep> getSteps() { return Collections.unmodifiableList(steps); }
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
        steps.clear();
        if (newTasks != null) newTasks.stream().limit(64).map(PlanStep::new).forEach(steps::add);
        summary = bounded(newSummary, 256);
        currentTaskIndex = 0;
        attemptCount = 0;
        revision++;
    }

    public void loadHorizon(List<Task> newTasks, String newSummary, String reason, long tick) {
        loadTasks(newTasks, newSummary);
        replanReason = bounded(reason, 256);
        lastProgressTick = Math.max(lastProgressTick, tick);
        state = State.EXECUTING;
    }

    public Task getCurrentTask() {
        if (currentTaskIndex >= 0 && currentTaskIndex < steps.size()) {
            return steps.get(currentTaskIndex).getTask();
        }
        return null;
    }

    public PlanStep getCurrentStep() {
        return currentTaskIndex >= 0 && currentTaskIndex < steps.size()
            ? steps.get(currentTaskIndex) : null;
    }

    public void markCurrentStepActive() {
        PlanStep step = getCurrentStep();
        if (step != null) {
            step.markActive();
            step.incrementAttempt();
            attemptCount = step.getAttempts();
        }
    }

    public void recordCurrentStepResult(ActionResult result, long currentTick) {
        PlanStep step = getCurrentStep();
        if (step != null) {
            step.complete(result, currentTick);
            attemptCount = step.getAttempts();
        }
    }

    public void advanceToNextTask(long currentTick) {
        PlanStep step = getCurrentStep();
        if (step != null && step.getStatus() != PlanStep.Status.COMPLETED) {
            step.complete(ActionResult.success("step completed").build(), currentTick);
        }
        currentTaskIndex++;
        attemptCount = 0;
        lastProgressTick = currentTick;
        if (currentTaskIndex >= steps.size()) setState(State.COMPLETED);
    }

    public void incrementAttempt() { attemptCount++; }
    public boolean canRetry() { return attemptCount < maxRetries; }
    public void incrementReplan() { replanCount++; revision++; }
    public boolean canReplan() { return replanCount < maxReplans; }
    public void incrementLLMCall() { llmCallCount++; }
    public boolean canCallLLM() { return llmCallCount < maxLLMCalls; }

    public boolean isTimedOut(long currentTick) {
        return timeoutTicks > 0 && currentTick - lastProgressTick > timeoutTicks;
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
        failureReason = bounded(reason, 256);
        setState(State.FAILED);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("PlanId", planId);
        if (goalId != null) tag.putUUID("GoalId", goalId);
        tag.putString("OriginalCommand", originalCommand);
        if (requestingPlayer != null) tag.putUUID("RequestingPlayer", requestingPlayer);
        if (steveUuid != null) tag.putUUID("SteveUuid", steveUuid);
        tag.putString("State", state.name());
        tag.putInt("CurrentTaskIndex", currentTaskIndex);
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
        steps.stream().limit(64).forEach(step -> stepList.add(step.save()));
        tag.put("Steps", stepList);

        // Keep a simple task list for saves written by the previous Plan implementation.
        ListTag taskList = new ListTag();
        for (PlanStep step : steps) {
            CompoundTag taskTag = new CompoundTag();
            taskTag.putString("Action", step.getTask().getAction());
            taskTag.putString("Parameters", GSON.toJson(step.getTask().getParameters()));
            taskList.add(taskTag);
        }
        tag.put("Tasks", taskList);
        return tag;
    }

    public static Plan load(CompoundTag tag) {
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
        plan.currentTaskIndex = Math.max(0, readInt(tag, "CurrentTaskIndex", "currentTaskIndex", 0));
        plan.attemptCount = Math.max(0, readInt(tag, "AttemptCount", "attemptCount", 0));
        plan.replanCount = Math.max(0, readInt(tag, "ReplanCount", "replanCount", 0));
        plan.llmCallCount = Math.max(0, readInt(tag, "LlmCallCount", "llmCallCount", 0));
        plan.revision = Math.max(0, tag.getInt("Revision"));
        plan.lastProgressTick = readLong(tag, "LastProgressTick", "lastProgressTick", plan.createdAtTick);
        plan.failureReason = tag.contains("FailureReason") ? tag.getString("FailureReason") : tag.getString("failureReason");
        plan.summary = tag.contains("Summary") ? tag.getString("Summary") : tag.getString("summary");
        plan.replanReason = tag.contains("ReplanReason") ? tag.getString("ReplanReason") : null;

        if (tag.contains("Steps", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Steps", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(64, list.size()); i++) plan.steps.add(PlanStep.load(list.getCompound(i)));
        } else if (tag.contains("tasks", Tag.TAG_LIST)) {
            ListTag list = tag.getList("tasks", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(64, list.size()); i++) {
                CompoundTag item = list.getCompound(i);
                Map<String, Object> params = parseMap(item.getString("parameters"));
                plan.steps.add(new PlanStep(new Task(item.getString("action"), params)));
            }
        } else if (tag.contains("Tasks", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Tasks", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(64, list.size()); i++) {
                CompoundTag item = list.getCompound(i);
                plan.steps.add(new PlanStep(new Task(item.getString("Action"), parseMap(item.getString("Parameters")))));
            }
        }
        plan.currentTaskIndex = Math.min(plan.currentTaskIndex, plan.steps.size());
        return plan;
    }

    public String toSummary() {
        return String.format("Plan[%s]: %s - %s", state, getProgress(), summary != null ? summary : "No summary");
    }

    private static Map<String, Object> parseMap(String json) {
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

    private static int readInt(CompoundTag tag, String current, String legacy, int fallback) {
        if (tag.contains(current)) return tag.getInt(current);
        return tag.contains(legacy) ? tag.getInt(legacy) : fallback;
    }

    private static long readLong(CompoundTag tag, String current, String legacy, long fallback) {
        if (tag.contains(current)) return tag.getLong(current);
        return tag.contains(legacy) ? tag.getLong(legacy) : fallback;
    }

    private static String bounded(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
