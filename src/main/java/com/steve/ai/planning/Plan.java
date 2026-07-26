package com.steve.ai.planning;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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

public final class Plan {
    public enum State {
        CREATED, PLANNING, EXECUTING, PAUSED, COMPLETED, FAILED, CANCELLED
    }

    private final UUID planId;
    private final String originalCommand;
    private final UUID requestingPlayer;
    private final UUID steveUuid;
    private State state;
    private final List<Task> tasks;
    private int currentTaskIndex;
    private int attemptCount;
    private int replanCount;
    private int llmCallCount;
    private final long createdAtTick;
    private long lastProgressTick;
    private String failureReason;
    private String summary;

    private final int maxRetries;
    private final int maxReplans;
    private final int maxLLMCalls;
    private final int timeoutTicks;

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>(){}.getType();

    public Plan(String originalCommand, UUID requestingPlayer, UUID steveUuid,
                int maxRetries, int maxReplans, int maxLLMCalls, int timeoutTicks, long createdAtTick) {
        this(UUID.randomUUID(), originalCommand, requestingPlayer, steveUuid,
             maxRetries, maxReplans, maxLLMCalls, timeoutTicks, createdAtTick);
    }

    private Plan(UUID planId, String originalCommand, UUID requestingPlayer, UUID steveUuid,
                 int maxRetries, int maxReplans, int maxLLMCalls, int timeoutTicks, long createdAtTick) {
        this.planId = planId;
        this.originalCommand = originalCommand;
        this.requestingPlayer = requestingPlayer;
        this.steveUuid = steveUuid;
        this.state = State.CREATED;
        this.tasks = new ArrayList<>();
        this.currentTaskIndex = 0;
        this.attemptCount = 0;
        this.replanCount = 0;
        this.llmCallCount = 0;
        this.createdAtTick = createdAtTick;
        this.lastProgressTick = createdAtTick;
        this.maxRetries = maxRetries;
        this.maxReplans = maxReplans;
        this.maxLLMCalls = maxLLMCalls;
        this.timeoutTicks = timeoutTicks;
    }

    public UUID getPlanId() { return planId; }
    public String getOriginalCommand() { return originalCommand; }
    public UUID getRequestingPlayer() { return requestingPlayer; }
    public UUID getSteveUuid() { return steveUuid; }
    public State getState() { return state; }
    public List<Task> getTasks() { return Collections.unmodifiableList(tasks); }
    public int getCurrentTaskIndex() { return currentTaskIndex; }
    public int getAttemptCount() { return attemptCount; }
    public int getReplanCount() { return replanCount; }
    public int getLlmCallCount() { return llmCallCount; }
    public long getCreatedAtTick() { return createdAtTick; }
    public long getLastProgressTick() { return lastProgressTick; }
    public String getFailureReason() { return failureReason; }
    public String getSummary() { return summary; }

    public void loadTasks(List<Task> newTasks, String newSummary) {
        this.tasks.clear();
        this.tasks.addAll(newTasks);
        this.summary = newSummary;
        this.currentTaskIndex = 0;
        this.attemptCount = 0;
    }

    public Task getCurrentTask() {
        if (currentTaskIndex >= 0 && currentTaskIndex < tasks.size()) {
            return tasks.get(currentTaskIndex);
        }
        return null;
    }

    public void advanceToNextTask(long currentTick) {
        currentTaskIndex++;
        attemptCount = 0;
        lastProgressTick = currentTick;
        if (currentTaskIndex >= tasks.size()) {
            setState(State.COMPLETED);
        }
    }

    public void incrementAttempt() { attemptCount++; }
    public boolean canRetry() { return attemptCount < maxRetries; }

    public void incrementReplan() { replanCount++; }
    public boolean canReplan() { return replanCount < maxReplans; }

    public void incrementLLMCall() { llmCallCount++; }
    public boolean canCallLLM() { return llmCallCount < maxLLMCalls; }

    public boolean isTimedOut(long currentTick) {
        if (timeoutTicks <= 0) return false;
        return (currentTick - lastProgressTick) > timeoutTicks;
    }

    public String getProgress() {
        return currentTaskIndex + "/" + tasks.size() + " tasks";
    }

    public void setState(State newState) {
        if (this.state == State.COMPLETED && newState == State.EXECUTING) {
            throw new IllegalStateException("Cannot transition from COMPLETED to EXECUTING");
        }
        if (this.state == State.FAILED && newState == State.EXECUTING) {
            throw new IllegalStateException("Cannot transition from FAILED to EXECUTING");
        }
        if (this.state == State.CANCELLED && newState == State.EXECUTING) {
            throw new IllegalStateException("Cannot transition from CANCELLED to EXECUTING");
        }
        this.state = newState;
    }

    public void setFailureReason(String reason) {
        this.failureReason = reason;
        setState(State.FAILED);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("planId", planId);
        if (originalCommand != null) tag.putString("originalCommand", originalCommand);
        if (requestingPlayer != null) tag.putUUID("requestingPlayer", requestingPlayer);
        if (steveUuid != null) tag.putUUID("steveUuid", steveUuid);
        tag.putString("state", state.name());
        
        ListTag taskList = new ListTag();
        for (Task task : tasks) {
            CompoundTag taskTag = new CompoundTag();
            taskTag.putString("action", task.getAction());
            taskTag.putString("parameters", GSON.toJson(task.getParameters()));
            taskList.add(taskTag);
        }
        tag.put("tasks", taskList);
        
        tag.putInt("currentTaskIndex", currentTaskIndex);
        tag.putInt("attemptCount", attemptCount);
        tag.putInt("replanCount", replanCount);
        tag.putInt("llmCallCount", llmCallCount);
        tag.putLong("createdAtTick", createdAtTick);
        tag.putLong("lastProgressTick", lastProgressTick);
        
        if (failureReason != null) tag.putString("failureReason", failureReason);
        if (summary != null) tag.putString("summary", summary);
        
        tag.putInt("maxRetries", maxRetries);
        tag.putInt("maxReplans", maxReplans);
        tag.putInt("maxLLMCalls", maxLLMCalls);
        tag.putInt("timeoutTicks", timeoutTicks);
        
        return tag;
    }

    public static Plan load(CompoundTag tag) {
        UUID planId = tag.hasUUID("planId") ? tag.getUUID("planId") : UUID.randomUUID();
        String originalCommand = tag.getString("originalCommand");
        UUID requestingPlayer = tag.hasUUID("requestingPlayer") ? tag.getUUID("requestingPlayer") : null;
        UUID steveUuid = tag.hasUUID("steveUuid") ? tag.getUUID("steveUuid") : null;
        
        int maxRetries = tag.getInt("maxRetries");
        int maxReplans = tag.getInt("maxReplans");
        int maxLLMCalls = tag.getInt("maxLLMCalls");
        int timeoutTicks = tag.getInt("timeoutTicks");
        long createdAtTick = tag.getLong("createdAtTick");
        
        Plan plan = new Plan(planId, originalCommand, requestingPlayer, steveUuid,
                             maxRetries, maxReplans, maxLLMCalls, timeoutTicks, createdAtTick);
        
        if (tag.contains("state")) {
            plan.state = State.valueOf(tag.getString("state"));
        }
        
        if (tag.contains("tasks", Tag.TAG_LIST)) {
            ListTag taskList = tag.getList("tasks", Tag.TAG_COMPOUND);
            for (int i = 0; i < taskList.size(); i++) {
                CompoundTag taskTag = taskList.getCompound(i);
                String action = taskTag.getString("action");
                String paramsJson = taskTag.getString("parameters");
                Map<String, Object> parameters = GSON.fromJson(paramsJson, MAP_TYPE);
                plan.tasks.add(new Task(action, parameters));
            }
        }
        
        plan.currentTaskIndex = tag.getInt("currentTaskIndex");
        plan.attemptCount = tag.getInt("attemptCount");
        plan.replanCount = tag.getInt("replanCount");
        plan.llmCallCount = tag.getInt("llmCallCount");
        plan.lastProgressTick = tag.getLong("lastProgressTick");
        
        if (tag.contains("failureReason")) {
            plan.failureReason = tag.getString("failureReason");
        }
        if (tag.contains("summary")) {
            plan.summary = tag.getString("summary");
        }
        
        return plan;
    }

    public String toSummary() {
        return String.format("Plan[%s]: %s - %s", state, getProgress(), summary != null ? summary : "No summary");
    }
}
