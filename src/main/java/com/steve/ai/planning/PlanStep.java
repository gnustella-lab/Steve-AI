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

/** One executable horizon step and its durable progress metadata. */
public final class PlanStep {
    public static final int DATA_VERSION = 2;
    public static final int MAX_ATTEMPT_HISTORY = 8;
    public static final int MAX_COUNTER = 1_000_000;
    private static final int MAX_ACTION_LENGTH = 128;
    private static final int MAX_PARAMETERS_JSON_LENGTH = 4_096;
    private static final int MAX_RESULT_MESSAGE_LENGTH = 512;
    private static final int MAX_ERROR_CODE_LENGTH = 64;

    public enum Status {
        PENDING,
        ACTIVE,
        COMPLETED,
        FAILED,
        SKIPPED,
        CANCELLED
    }

    /** Compact primitive/string result for one attempted step, retained for diagnostics. */
    public static final class AttemptResult {
        private final int attempt;
        private final Status status;
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final long tick;

        private AttemptResult(int attempt, Status status, boolean success, String errorCode,
                String message, long tick) {
            this.attempt = Math.max(0, Math.min(MAX_COUNTER, attempt));
            this.status = status == null ? Status.FAILED : status;
            this.success = success;
            this.errorCode = bounded(errorCode, MAX_ERROR_CODE_LENGTH);
            this.message = bounded(message, MAX_RESULT_MESSAGE_LENGTH);
            this.tick = Math.max(0L, tick);
        }

        public int getAttempt() { return attempt; }
        public Status getStatus() { return status; }
        public boolean isSuccess() { return success; }
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public long getTick() { return tick; }
    }

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private final UUID stepId;
    private final Task task;
    private Status status;
    private int attempts;
    private ActionResult lastResult;
    private long updatedAt;
    private long startedAt;
    private long lastAttemptAt;
    private final List<AttemptResult> attemptHistory;

    public PlanStep(Task task) {
        this(UUID.randomUUID(), task, Status.PENDING, 0, null, 0L, 0L, 0L, List.of());
    }

    private PlanStep(UUID stepId, Task task, Status status, int attempts,
            ActionResult lastResult, long updatedAt, long startedAt, long lastAttemptAt,
            List<AttemptResult> attemptHistory) {
        this.stepId = stepId == null ? UUID.randomUUID() : stepId;
        this.task = task == null ? new Task("", Map.of()) : task;
        this.status = status == null ? Status.PENDING : status;
        this.attempts = bounded(attempts);
        this.lastResult = lastResult;
        this.updatedAt = Math.max(0L, updatedAt);
        this.startedAt = Math.max(0L, startedAt);
        this.lastAttemptAt = Math.max(0L, lastAttemptAt);
        this.attemptHistory = new ArrayList<>();
        if (attemptHistory != null) {
            attemptHistory.stream().limit(MAX_ATTEMPT_HISTORY).forEach(this.attemptHistory::add);
        }
    }

    public UUID getStepId() { return stepId; }
    public Task getTask() { return task; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public ActionResult getLastResult() { return lastResult; }
    public long getUpdatedAt() { return updatedAt; }
    public long getStartedAt() { return startedAt; }
    public long getLastAttemptAt() { return lastAttemptAt; }
    public List<AttemptResult> getAttemptHistory() {
        return Collections.unmodifiableList(new ArrayList<>(attemptHistory));
    }
    public List<AttemptResult> getAttemptResults() { return getAttemptHistory(); }

    public void markActive() {
        markActive(updatedAt);
    }

    public void markActive(long tick) {
        if (status != Status.COMPLETED && status != Status.CANCELLED) {
            status = Status.ACTIVE;
            if (startedAt == 0L) startedAt = Math.max(0L, tick);
            updatedAt = Math.max(updatedAt, Math.max(0L, tick));
        }
    }

    public void incrementAttempt() {
        incrementAttempt(updatedAt);
    }

    public void incrementAttempt(long tick) {
        attempts = boundedIncrement(attempts);
        lastAttemptAt = Math.max(lastAttemptAt, Math.max(0L, tick));
        updatedAt = Math.max(updatedAt, Math.max(0L, tick));
        if (status == Status.PENDING) status = Status.ACTIVE;
        if (startedAt == 0L) startedAt = Math.max(0L, tick);
    }

    public void complete(ActionResult result, long tick) {
        lastResult = result;
        status = result != null && result.isSuccess() ? Status.COMPLETED : Status.FAILED;
        updatedAt = Math.max(updatedAt, Math.max(0L, tick));
        appendAttempt(status, result, tick);
    }

    public void skip(long tick, ActionResult result) {
        lastResult = result;
        status = Status.SKIPPED;
        updatedAt = Math.max(updatedAt, Math.max(0L, tick));
        appendAttempt(status, result, tick);
    }

    public void cancel(long tick, ActionResult result) {
        lastResult = result;
        status = Status.CANCELLED;
        updatedAt = Math.max(updatedAt, Math.max(0L, tick));
        appendAttempt(status, result, tick);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", DATA_VERSION);
        tag.putUUID("StepId", stepId);
        tag.putString("Action", bounded(task.getAction(), MAX_ACTION_LENGTH));
        tag.putString("Parameters", boundedJson(GSON.toJson(task.getParameters()), MAX_PARAMETERS_JSON_LENGTH));
        tag.putString("Status", status.name());
        tag.putInt("Attempts", attempts);
        tag.putLong("UpdatedAt", updatedAt);
        tag.putLong("StartedAt", startedAt);
        tag.putLong("LastAttemptAt", lastAttemptAt);
        if (lastResult != null) {
            tag.put("Result", saveResult(lastResult));
        }

        ListTag history = new ListTag();
        attemptHistory.stream().limit(MAX_ATTEMPT_HISTORY).forEach(attempt -> history.add(saveAttempt(attempt)));
        tag.put("AttemptHistory", history);
        return tag;
    }

    public static PlanStep load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return new PlanStep(new Task("", Map.of()));
        String action = bounded(tag.getString("Action"), MAX_ACTION_LENGTH);
        Map<String, Object> parameters = parseMap(tag.getString("Parameters"));
        ActionResult result = tag.contains("Result", Tag.TAG_COMPOUND)
            ? loadResult(tag.getCompound("Result")) : null;
        Status status = readStatus(tag.getString("Status"));
        List<AttemptResult> history = new ArrayList<>();
        if (tag.contains("AttemptHistory", Tag.TAG_LIST)) {
            ListTag list = tag.getList("AttemptHistory", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_ATTEMPT_HISTORY, list.size()); i++) {
                history.add(loadAttempt(list.getCompound(i)));
            }
        }
        return new PlanStep(
            tag.hasUUID("StepId") ? tag.getUUID("StepId") : UUID.randomUUID(),
            new Task(action, parameters),
            status,
            tag.getInt("Attempts"),
            result,
            tag.getLong("UpdatedAt"),
            tag.getLong("StartedAt"),
            tag.getLong("LastAttemptAt"),
            history);
    }

    private void appendAttempt(Status resultStatus, ActionResult result, long tick) {
        AttemptResult attempt = new AttemptResult(attempts, resultStatus,
            result != null && result.isSuccess(),
            result == null ? null : result.getErrorCode(),
            result == null ? "" : result.getMessage(), tick);
        if (attemptHistory.size() >= MAX_ATTEMPT_HISTORY) attemptHistory.remove(0);
        attemptHistory.add(attempt);
    }

    private static CompoundTag saveAttempt(AttemptResult attempt) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Attempt", attempt.getAttempt());
        tag.putString("Status", attempt.getStatus().name());
        tag.putBoolean("Success", attempt.isSuccess());
        if (attempt.getErrorCode() != null) tag.putString("ErrorCode", attempt.getErrorCode());
        tag.putString("Message", attempt.getMessage());
        tag.putLong("Tick", attempt.getTick());
        return tag;
    }

    private static AttemptResult loadAttempt(CompoundTag tag) {
        return new AttemptResult(tag.getInt("Attempt"), readStatus(tag.getString("Status")),
            tag.getBoolean("Success"), tag.getString("ErrorCode"), tag.getString("Message"),
            tag.getLong("Tick"));
    }

    private static CompoundTag saveResult(ActionResult result) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Success", result.isSuccess());
        tag.putBoolean("PartialSuccess", result.isPartialSuccess());
        tag.putBoolean("Retryable", result.isRetryable());
        tag.putBoolean("RequiresReplanning", result.requiresReplanning());
        if (result.getErrorCode() != null) {
            tag.putString("ErrorCode", bounded(result.getErrorCode(), MAX_ERROR_CODE_LENGTH));
        }
        tag.putString("Message", bounded(result.getMessage(), MAX_RESULT_MESSAGE_LENGTH));
        tag.putString("Observations", boundedJson(GSON.toJson(result.getObservations()), MAX_PARAMETERS_JSON_LENGTH));
        return tag;
    }

    private static ActionResult loadResult(CompoundTag tag) {
        ActionResult.Builder builder = ActionResult.builder()
            .success(tag.getBoolean("Success"))
            .partialSuccess(tag.getBoolean("PartialSuccess"))
            .retryable(tag.getBoolean("Retryable"))
            .requiresReplanning(tag.getBoolean("RequiresReplanning"))
            .message(bounded(tag.getString("Message"), MAX_RESULT_MESSAGE_LENGTH));
        if (tag.contains("ErrorCode")) {
            builder.errorCode(bounded(tag.getString("ErrorCode"), MAX_ERROR_CODE_LENGTH));
        }
        Map<String, Object> observations = parseMap(tag.getString("Observations"));
        if (!observations.isEmpty()) builder.observations(observations);
        return builder.build();
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

    private static Status readStatus(String value) {
        try {
            return value == null || value.isBlank() ? Status.PENDING : Status.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return Status.PENDING;
        }
    }

    private static int bounded(int value) {
        return Math.max(0, Math.min(MAX_COUNTER, value));
    }

    private static int boundedIncrement(int value) {
        return value >= MAX_COUNTER ? MAX_COUNTER : value + 1;
    }

    private static String boundedJson(String value, int max) {
        String normalized = value == null ? "{}" : value;
        return normalized.length() <= max ? normalized : "{}";
    }

    private static String bounded(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
