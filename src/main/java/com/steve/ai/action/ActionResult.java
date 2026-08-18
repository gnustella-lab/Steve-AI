package com.steve.ai.action;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured result returned by every action execution.
 *
 * <p>Provides rich metadata for recovery policies, replanning decisions, and diagnostics
 * beyond a simple success/failure boolean.</p>
 */
public final class ActionResult {

    public static final String ERROR_BLOCKED = "blocked";
    public static final String ERROR_PATHING = "pathing";
    public static final String ERROR_RESOURCE = "resource";
    public static final String ERROR_INVENTORY_FULL = "inventory_full";
    public static final String ERROR_TOOL_MISSING = "tool_missing";
    public static final String ERROR_TOOL_BROKEN = "tool_broken";
    public static final String ERROR_PROTECTED = "protected";
    public static final String ERROR_ENTITY_GONE = "entity_gone";
    public static final String ERROR_PLAYER_OFFLINE = "player_offline";
    public static final String ERROR_CHUNK_UNLOADED = "chunk_unloaded";
    public static final String ERROR_VALIDATION = "validation";
    public static final String ERROR_PERMISSION_DENIED = "permission_denied";
    public static final String ERROR_LLM_INVALID = "llm_invalid";
    public static final String ERROR_TIMEOUT = "timeout";
    public static final String ERROR_CANCELLED = "cancelled";
    public static final String ERROR_UNKNOWN = "unknown";

    private final boolean success;
    private final boolean partialSuccess;
    private final boolean retryable;
    private final boolean requiresReplanning;
    private final String errorCode;
    private final String message;
    private final Map<String, Object> observations;

    private ActionResult(Builder builder) {
        this.success = builder.success;
        this.partialSuccess = builder.partialSuccess;
        this.retryable = builder.retryable;
        this.requiresReplanning = builder.requiresReplanning;
        this.errorCode = builder.errorCode;
        this.message = Objects.requireNonNull(builder.message, "message");
        this.observations = Collections.unmodifiableMap(new LinkedHashMap<>(builder.observations));
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isPartialSuccess() {
        return partialSuccess;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean requiresReplanning() {
        return requiresReplanning;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getObservations() {
        return observations;
    }

    public Object getObservation(String key) {
        return observations.get(key);
    }

    public ActionResult withObservation(String key, Object value) {
        Builder b = new Builder(this);
        b.observation(key, value);
        return b.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder success(String message) {
        return new Builder()
            .success(true)
            .message(message);
    }

    public static Builder failure(String errorCode, String message) {
        return new Builder()
            .success(false)
            .errorCode(errorCode)
            .message(message);
    }

    @Deprecated(forRemoval = false)
    public static ActionResult success(String message, boolean unused) {
        return success(message).build();
    }

    @Deprecated(forRemoval = false)
    public static ActionResult failure(String message) {
        return failure(ERROR_UNKNOWN, message).build();
    }

    @Deprecated(forRemoval = false)
    public static ActionResult failure(String message, boolean requiresReplanning) {
        Builder b = failure(ERROR_UNKNOWN, message);
        b.requiresReplanning(requiresReplanning);
        return b.build();
    }

    public static class Builder {
        private boolean success = false;
        private boolean partialSuccess = false;
        private boolean retryable = false;
        private boolean requiresReplanning = false;
        private String errorCode;
        private String message = "";
        private final Map<String, Object> observations = new LinkedHashMap<>();

        private Builder() {}

        private Builder(ActionResult source) {
            this.success = source.success;
            this.partialSuccess = source.partialSuccess;
            this.retryable = source.retryable;
            this.requiresReplanning = source.requiresReplanning;
            this.errorCode = source.errorCode;
            this.message = source.message;
            this.observations.putAll(source.observations);
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder partialSuccess(boolean partialSuccess) {
            this.partialSuccess = partialSuccess;
            return this;
        }

        public Builder retryable(boolean retryable) {
            this.retryable = retryable;
            return this;
        }

        public Builder requiresReplanning(boolean requiresReplanning) {
            this.requiresReplanning = requiresReplanning;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder message(String message) {
            this.message = message != null ? message : "";
            return this;
        }

        public Builder observation(String key, Object value) {
            this.observations.put(key, value);
            return this;
        }

        public Builder observations(Map<String, Object> observations) {
            this.observations.clear();
            if (observations != null) {
                this.observations.putAll(observations);
            }
            return this;
        }

        public ActionResult build() {
            return new ActionResult(this);
        }
    }

    @Override
    public String toString() {
        return "ActionResult{success=" + success
            + ", partial=" + partialSuccess
            + ", retryable=" + retryable
            + ", replan=" + requiresReplanning
            + ", errorCode=" + errorCode
            + ", message='" + message + "'"
            + ", observations=" + observations
            + '}';
    }
}
