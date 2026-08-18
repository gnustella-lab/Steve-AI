package com.steve.ai.autonomy;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import net.minecraft.core.BlockPos;

import java.util.Locale;
import java.util.Map;

/** Deterministic recovery policy. It never bypasses permissions or invents world mutations. */
public final class RecoveryEngine {
    public RecoveryDecision decide(AgentGoal goal, Task task, ActionResult result,
            FailureTracker tracker, BlockPos position) {
        if (result == null || result.isSuccess()) {
            return RecoveryDecision.replan("no failure result", Map.of());
        }

        int count = tracker.record(goal, task, result, position);
        if (tracker.repeated(goal, task, result, position)
                && !ActionResult.ERROR_PROTECTED.equals(result.getErrorCode())) {
            return RecoveryDecision.blocked("Repeated failure fingerprint reached its limit");
        }

        String code = result.getErrorCode() == null ? ActionResult.ERROR_UNKNOWN : result.getErrorCode();
        if (result.requiresReplanning()) {
            return RecoveryDecision.replan("Action requested replanning: " + code, Map.of("failureCount", count));
        }

        return switch (code) {
            case ActionResult.ERROR_PATHING, ActionResult.ERROR_BLOCKED, ActionResult.ERROR_TIMEOUT ->
                result.isRetryable() && count <= goal.getBudget().getMaxRetriesPerStep()
                    ? RecoveryDecision.retry("Trying the action again after a bounded delay", task)
                    : RecoveryDecision.replan("Route or action progress is invalid", Map.of("failureCount", count));
            case ActionResult.ERROR_RESOURCE, ActionResult.ERROR_TOOL_MISSING, ActionResult.ERROR_TOOL_BROKEN ->
                RecoveryDecision.prerequisite(resolvePrerequisite(task, result),
                    Map.of("errorCode", code, "missingItem", result.getObservation("missing_item")));
            case ActionResult.ERROR_INVENTORY_FULL ->
                RecoveryDecision.replan("Inventory capacity changed; find an authorized container", Map.of("inventoryFull", true));
            case ActionResult.ERROR_PROTECTED ->
                RecoveryDecision.replan("Protected location remembered; choose another route or resource", Map.of("protected", true));
            case ActionResult.ERROR_ENTITY_GONE ->
                RecoveryDecision.replan("Target disappeared; observe for another compatible target", Map.of("entityGone", true));
            case ActionResult.ERROR_PLAYER_OFFLINE -> RecoveryDecision.pause("Controlling player is offline");
            case ActionResult.ERROR_CHUNK_UNLOADED ->
                RecoveryDecision.replan("Chunk is unavailable; navigate naturally or choose another approach", Map.of("chunkUnloaded", true));
            case ActionResult.ERROR_VALIDATION, ActionResult.ERROR_PERMISSION_DENIED,
                ActionResult.ERROR_LLM_INVALID, ActionResult.ERROR_CANCELLED ->
                RecoveryDecision.blocked("Unsafe or cancelled operation cannot be repeated automatically");
            default -> result.isRetryable() && count <= goal.getBudget().getMaxRetriesPerStep()
                ? RecoveryDecision.retry("Retrying one bounded time", task)
                : RecoveryDecision.replan("Unknown failure requires a fresh observation", Map.of("failureCount", count));
        };
    }

    private static String resolvePrerequisite(Task task, ActionResult result) {
        Object missing = result.getObservation("missing_item");
        if (missing != null && !String.valueOf(missing).isBlank()) {
            Object quantity = result.getObservation("missing_quantity");
            return "Gather " + (quantity == null ? 1 : quantity) + " " + missing;
        }
        Object requiredTool = result.getObservation("required_tool");
        if (requiredTool != null) return "Craft " + requiredTool;
        String action = task == null ? "operation" : task.getAction().toLowerCase(Locale.ROOT);
        return switch (action) {
            case "craft" -> "Gather the missing ingredients for crafting";
            case "smelt" -> "Obtain a furnace, smelting input, and fuel";
            case "mine", "gather" -> "Obtain a suitable mining tool and search for the resource";
            default -> "Satisfy the prerequisite for " + action;
        };
    }
}
