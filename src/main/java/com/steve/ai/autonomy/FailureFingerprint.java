package com.steve.ai.autonomy;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Stable compact key for one failed strategy at one goal/location. */
public record FailureFingerprint(UUID goalId, String action, String parameters,
        String errorCode, String location) {
    public static FailureFingerprint from(AgentGoal goal, Task task, ActionResult result, BlockPos position) {
        String normalizedParameters = task == null || task.getParameters() == null ? "" : task.getParameters().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + String.valueOf(entry.getValue()))
            .collect(Collectors.joining(";"));
        if (normalizedParameters.length() > 512) normalizedParameters = normalizedParameters.substring(0, 512);
        return new FailureFingerprint(
            goal == null ? null : goal.getId(),
            task == null ? "unknown" : task.getAction(),
            normalizedParameters,
            result == null || result.getErrorCode() == null ? ActionResult.ERROR_UNKNOWN : result.getErrorCode(),
            position == null ? "unknown" : position.toShortString());
    }

    public String compact() {
        return String.valueOf(goalId) + '|' + action + '|' + parameters + '|' + errorCode + '|' + location;
    }
}
