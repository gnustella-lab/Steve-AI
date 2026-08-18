package com.steve.ai.autonomy;

import com.steve.ai.action.Task;

import java.util.Map;

/** Result of deterministic recovery, before any LLM replan is attempted. */
public record RecoveryDecision(
    Kind kind,
    String reason,
    String prerequisiteDescription,
    Task retryTask,
    Map<String, Object> metadata
) {
    public enum Kind {
        RETRY,
        PREREQUISITE,
        REPLAN,
        PAUSE,
        BLOCKED
    }

    public RecoveryDecision {
        reason = reason == null ? "" : reason;
        prerequisiteDescription = prerequisiteDescription == null ? "" : prerequisiteDescription;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static RecoveryDecision retry(String reason, Task task) {
        return new RecoveryDecision(Kind.RETRY, reason, "", task, Map.of());
    }

    public static RecoveryDecision replan(String reason, Map<String, Object> metadata) {
        return new RecoveryDecision(Kind.REPLAN, reason, "", null, metadata);
    }

    public static RecoveryDecision prerequisite(String description, Map<String, Object> metadata) {
        return new RecoveryDecision(Kind.PREREQUISITE, "missing prerequisite", description, null, metadata);
    }

    public static RecoveryDecision pause(String reason) {
        return new RecoveryDecision(Kind.PAUSE, reason, "", null, Map.of());
    }

    public static RecoveryDecision blocked(String reason) {
        return new RecoveryDecision(Kind.BLOCKED, reason, "", null, Map.of());
    }
}
