package com.steve.ai.planning;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;

/** One executable horizon step and its durable progress metadata. */
public final class PlanStep {
    public enum Status {
        PENDING,
        ACTIVE,
        COMPLETED,
        FAILED,
        SKIPPED,
        CANCELLED
    }

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private final UUID stepId;
    private final Task task;
    private Status status;
    private int attempts;
    private ActionResult lastResult;
    private long updatedAt;

    public PlanStep(Task task) {
        this(UUID.randomUUID(), task, Status.PENDING, 0, null, 0L);
    }

    private PlanStep(UUID stepId, Task task, Status status, int attempts,
            ActionResult lastResult, long updatedAt) {
        this.stepId = stepId;
        this.task = task;
        this.status = status;
        this.attempts = Math.max(0, attempts);
        this.lastResult = lastResult;
        this.updatedAt = updatedAt;
    }

    public UUID getStepId() { return stepId; }
    public Task getTask() { return task; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public ActionResult getLastResult() { return lastResult; }
    public long getUpdatedAt() { return updatedAt; }

    public void markActive() {
        if (status != Status.COMPLETED && status != Status.CANCELLED) {
            status = Status.ACTIVE;
        }
    }

    public void incrementAttempt() {
        attempts = Math.min(Integer.MAX_VALUE, attempts + 1);
    }

    public void complete(ActionResult result, long tick) {
        lastResult = result;
        status = result != null && result.isSuccess() ? Status.COMPLETED : Status.FAILED;
        updatedAt = Math.max(updatedAt, tick);
    }

    public void skip(long tick, ActionResult result) {
        lastResult = result;
        status = Status.SKIPPED;
        updatedAt = Math.max(updatedAt, tick);
    }

    public void cancel(long tick, ActionResult result) {
        lastResult = result;
        status = Status.CANCELLED;
        updatedAt = Math.max(updatedAt, tick);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("StepId", stepId);
        tag.putString("Action", task.getAction());
        tag.putString("Parameters", GSON.toJson(task.getParameters()));
        tag.putString("Status", status.name());
        tag.putInt("Attempts", attempts);
        tag.putLong("UpdatedAt", updatedAt);
        if (lastResult != null) {
            tag.put("Result", saveResult(lastResult));
        }
        return tag;
    }

    public static PlanStep load(CompoundTag tag) {
        String action = tag.getString("Action");
        Map<String, Object> parameters;
        try {
            Map<String, Object> parsed = GSON.fromJson(tag.getString("Parameters"), MAP_TYPE);
            parameters = parsed == null ? Map.of() : parsed;
        } catch (RuntimeException ignored) {
            parameters = Map.of();
        }
        ActionResult result = tag.contains("Result", CompoundTag.TAG_COMPOUND)
            ? loadResult(tag.getCompound("Result")) : null;
        Status status = readStatus(tag.getString("Status"));
        return new PlanStep(
            tag.hasUUID("StepId") ? tag.getUUID("StepId") : UUID.randomUUID(),
            new Task(action, parameters),
            status,
            tag.getInt("Attempts"),
            result,
            tag.getLong("UpdatedAt"));
    }

    private static CompoundTag saveResult(ActionResult result) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Success", result.isSuccess());
        tag.putBoolean("PartialSuccess", result.isPartialSuccess());
        tag.putBoolean("Retryable", result.isRetryable());
        tag.putBoolean("RequiresReplanning", result.requiresReplanning());
        if (result.getErrorCode() != null) tag.putString("ErrorCode", result.getErrorCode());
        tag.putString("Message", result.getMessage());
        tag.putString("Observations", GSON.toJson(result.getObservations()));
        return tag;
    }

    private static ActionResult loadResult(CompoundTag tag) {
        ActionResult.Builder builder = ActionResult.builder()
            .success(tag.getBoolean("Success"))
            .partialSuccess(tag.getBoolean("PartialSuccess"))
            .retryable(tag.getBoolean("Retryable"))
            .requiresReplanning(tag.getBoolean("RequiresReplanning"))
            .message(tag.getString("Message"));
        if (tag.contains("ErrorCode")) builder.errorCode(tag.getString("ErrorCode"));
        try {
            Map<String, Object> observations = GSON.fromJson(tag.getString("Observations"), MAP_TYPE);
            if (observations != null) builder.observations(observations);
        } catch (RuntimeException ignored) {
            // Keep the result metadata bounded even when an old save contains malformed JSON.
        }
        return builder.build();
    }

    private static Status readStatus(String value) {
        try {
            return value == null || value.isBlank() ? Status.PENDING : Status.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return Status.PENDING;
        }
    }
}
