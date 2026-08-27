package com.steve.ai.autonomy;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Deterministic recovery policy. It never bypasses permissions or invents world mutations. */
public final class RecoveryEngine {
    private static final int DEFAULT_MAX_RETRIES = 3;

    public RecoveryDecision decide(AgentGoal goal, Task task, ActionResult result,
            FailureTracker tracker, BlockPos position) {
        if (result == null || result.isSuccess()) {
            return RecoveryDecision.blocked("no failure result");
        }
        if (tracker == null) {
            return RecoveryDecision.blocked("missing failure tracker");
        }

        int count = tracker.record(goal, task, result, position);
        if (tracker.repeated(goal, task, result, position)) {
            return RecoveryDecision.blocked("Repeated failure fingerprint reached its limit");
        }

        String code = result.getErrorCode() == null ? ActionResult.ERROR_UNKNOWN : result.getErrorCode();
        return switch (code) {
            case ActionResult.ERROR_PATHING, ActionResult.ERROR_BLOCKED, ActionResult.ERROR_TIMEOUT ->
                result.requiresReplanning()
                    ? replanIfAllowed(goal, "Route or action progress is invalid",
                        metadata(result, task, position, "failureCount", count))
                    : result.isRetryable() && count <= maxRetries(goal)
                        ? RecoveryDecision.retry("Trying the action again after a bounded delay", task)
                        : replanIfAllowed(goal, "Route or action progress is invalid",
                            metadata(result, task, position, "failureCount", count));
            case ActionResult.ERROR_RESOURCE, ActionResult.ERROR_TOOL_MISSING, ActionResult.ERROR_TOOL_BROKEN ->
                RecoveryDecision.prerequisite(resolvePrerequisite(task, result),
                    metadata(result, task, position, "errorCode", code, "failureCount", count));
            case ActionResult.ERROR_INVENTORY_FULL ->
                RecoveryDecision.prerequisite("Deposit excess items into an authorized container",
                    metadata(result, task, position, "inventoryFull", true, "failureCount", count));
            case ActionResult.ERROR_PROTECTED ->
                replanIfAllowed(goal, "protected location remembered at "
                        + targetDescription(result, task, position),
                    metadata(result, task, position, "protected", true, "failureCount", count));
            case ActionResult.ERROR_ENTITY_GONE ->
                replanIfAllowed(goal, "Target disappeared; observe for another compatible target",
                    metadata(result, task, position, "entityGone", true, "failureCount", count));
            case ActionResult.ERROR_PLAYER_OFFLINE -> RecoveryDecision.pause("Controlling player is offline");
            case ActionResult.ERROR_CHUNK_UNLOADED ->
                replanIfAllowed(goal, "Chunk is unavailable; navigate naturally or choose another approach",
                    metadata(result, task, position, "chunkUnloaded", true, "failureCount", count));
            case ActionResult.ERROR_VALIDATION, ActionResult.ERROR_PERMISSION_DENIED,
                ActionResult.ERROR_LLM_INVALID, ActionResult.ERROR_CANCELLED ->
                RecoveryDecision.blocked("Unsafe or cancelled operation cannot be repeated automatically");
            default -> result.requiresReplanning()
                ? replanIfAllowed(goal, "Action requested replanning: " + code,
                    metadata(result, task, position, "failureCount", count))
                : result.isRetryable() && count <= maxRetries(goal)
                ? RecoveryDecision.retry("Retrying one bounded time", task)
                : replanIfAllowed(goal, "Unknown failure requires a fresh observation",
                    metadata(result, task, position, "failureCount", count));
        };
    }

    private static int maxRetries(AgentGoal goal) {
        return goal == null || goal.getBudget() == null
            ? DEFAULT_MAX_RETRIES : goal.getBudget().getMaxRetriesPerStep();
    }

    private static RecoveryDecision replanIfAllowed(AgentGoal goal, String reason,
            Map<String, Object> metadata) {
        if (goal != null && goal.getBudget() != null
                && !goal.getBudget().canReplan(goal.getReplanCount())) {
            return RecoveryDecision.blocked("Recovery replan budget exhausted");
        }
        return RecoveryDecision.replan(reason, metadata);
    }

    private static Map<String, Object> metadata(ActionResult result, Task task, BlockPos position,
            Object... values) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            putIfPresent(metadata, String.valueOf(values[index]), values[index + 1]);
        }
        if (result != null) {
            putIfPresent(metadata, "missingItem", result.getObservation("missing_item"));
            putIfPresent(metadata, "missingQuantity", result.getObservation("missing_quantity"));
            putIfPresent(metadata, "requiredTool", result.getObservation("required_tool"));
            putCoordinate(metadata, "x", result.getObservation("x"));
            putCoordinate(metadata, "y", result.getObservation("y"));
            putCoordinate(metadata, "z", result.getObservation("z"));
        }
        if (task != null) {
            putCoordinateIfMissing(metadata, "x", task.getParameter("x"));
            putCoordinateIfMissing(metadata, "y", task.getParameter("y"));
            putCoordinateIfMissing(metadata, "z", task.getParameter("z"));
        }
        if (position != null) {
            putIfPresent(metadata, "failure_x", position.getX());
            putIfPresent(metadata, "failure_y", position.getY());
            putIfPresent(metadata, "failure_z", position.getZ());
        }
        return metadata;
    }

    private static void putCoordinateIfMissing(Map<String, Object> metadata, String key, Object value) {
        if (!metadata.containsKey(key)) {
            putCoordinate(metadata, key, value);
        }
    }

    private static void putCoordinate(Map<String, Object> metadata, String key, Object value) {
        if (value instanceof Number number) {
            metadata.put(key, number.intValue());
        }
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private static String targetDescription(ActionResult result, Task task, BlockPos position) {
        Object x = result == null ? null : result.getObservation("x");
        Object y = result == null ? null : result.getObservation("y");
        Object z = result == null ? null : result.getObservation("z");
        if (!(x instanceof Number) && task != null) x = task.getParameter("x");
        if (!(y instanceof Number) && task != null) y = task.getParameter("y");
        if (!(z instanceof Number) && task != null) z = task.getParameter("z");
        if (x instanceof Number && y instanceof Number && z instanceof Number) {
            return "[" + ((Number) x).intValue() + "," + ((Number) y).intValue() + ","
                + ((Number) z).intValue() + "]";
        }
        return position == null ? "unknown coordinates" : position.toShortString();
    }

    private static String resolvePrerequisite(Task task, ActionResult result) {
        Object missing = result.getObservation("missing_item");
        if (missing != null && !String.valueOf(missing).isBlank()) {
            Object quantity = result.getObservation("missing_quantity");
            return "Gather " + safeQuantity(quantity) + " " + missing;
        }
        Object requiredTool = result.getObservation("required_tool");
        if (requiredTool != null && !String.valueOf(requiredTool).isBlank()) {
            return "Craft " + requiredTool;
        }
        String action = task == null ? "operation" : task.getAction().toLowerCase(Locale.ROOT);
        return switch (action) {
            case "craft" -> "Gather the missing ingredients for crafting";
            case "smelt" -> "Obtain a furnace, smelting input, and fuel";
            case "mine", "gather" -> "Obtain a suitable mining tool and search for the resource";
            default -> "Satisfy the prerequisite for " + action;
        };
    }

    private static int safeQuantity(Object quantity) {
        if (quantity instanceof Number number) {
            return Math.max(1, Math.min(2_048, number.intValue()));
        }
        return 1;
    }
}
