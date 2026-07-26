package com.steve.ai.action.recovery;

import com.steve.ai.action.ActionResult;
import java.util.Map;

/**
 * Evaluates action results and determines the appropriate recovery strategy
 * without requiring additional LLM calls for common failures.
 */
public final class RecoveryPolicy {
    
    public enum RecoveryAction {
        RETRY_SAME,        // Retry the same action after a brief delay
        RETRY_MODIFIED,    // Retry with modifications (e.g., equip different tool)
        SKIP_CONTINUE,     // Skip this task, continue with next
        REPLAN,            // Call LLM to create a new plan
        PAUSE,             // Pause and wait (e.g., chunk unloaded)
        ABORT,             // Give up on entire plan
        ASK_PLAYER         // Ask player for guidance
    }

    public record RecoveryDecision(
        RecoveryAction action,
        String reason,
        int delayTicks,
        Map<String, Object> modifications
    ) {}

    /**
     * Decides the recovery action based on the result of an executed action.
     *
     * @param result       The result of the action
     * @param actionType   The type of action that was executed
     * @param attemptCount The number of times this action has been attempted
     * @param maxRetries   The maximum number of retries allowed for this action
     * @param maxReplans   The maximum number of replans allowed (budget remaining)
     * @return The decision for recovery
     */
    public RecoveryDecision decide(
        ActionResult result,
        String actionType,
        int attemptCount,
        int maxRetries,
        int maxReplans
    ) {
        if (result == null || result.isSuccess()) {
            return new RecoveryDecision(RecoveryAction.SKIP_CONTINUE, "Success", 0, Map.of());
        }

        String code = result.getErrorCode();
        if (code == null) {
            code = ActionResult.ERROR_UNKNOWN;
        }

        // 1. Explicit replan requirement from the action itself
        if (result.requiresReplanning()) {
            if (maxReplans > 0) {
                return new RecoveryDecision(RecoveryAction.REPLAN, "Action explicitly requires replanning", 0, Map.of());
            } else {
                return new RecoveryDecision(RecoveryAction.ABORT, "Requires replanning but budget exhausted", 0, Map.of());
            }
        }

        // 2. Specific error code handling
        switch (code) {
            case ActionResult.ERROR_PATHING:
                if (attemptCount >= maxRetries) {
                    return new RecoveryDecision(RecoveryAction.SKIP_CONTINUE, "Pathing failed, max retries reached", 0, Map.of());
                }
                if (result.isRetryable()) {
                    return new RecoveryDecision(RecoveryAction.RETRY_SAME, "Retrying pathing", 40, Map.of());
                }
                break;

            case ActionResult.ERROR_RESOURCE:
                if (result.isRetryable()) {
                    if (maxReplans > 0) {
                        return new RecoveryDecision(RecoveryAction.REPLAN, "Resource error, replanning", 0, Map.of());
                    } else {
                        return new RecoveryDecision(RecoveryAction.ABORT, "Resource error, replan budget exhausted", 0, Map.of());
                    }
                }
                break;

            case ActionResult.ERROR_INVENTORY_FULL:
                return new RecoveryDecision(RecoveryAction.SKIP_CONTINUE, "Inventory full", 0, Map.of());

            case ActionResult.ERROR_TOOL_MISSING:
                return new RecoveryDecision(RecoveryAction.RETRY_MODIFIED, "Equip missing tool", 0, Map.of("hint", "equip_tool"));

            case ActionResult.ERROR_TOOL_BROKEN:
                return new RecoveryDecision(RecoveryAction.RETRY_MODIFIED, "Replace broken tool", 0, Map.of("hint", "replace_tool"));

            case ActionResult.ERROR_PROTECTED:
                return new RecoveryDecision(RecoveryAction.SKIP_CONTINUE, "Protected region, skipping", 0, Map.of());

            case ActionResult.ERROR_ENTITY_GONE:
                return new RecoveryDecision(RecoveryAction.SKIP_CONTINUE, "Entity gone, skipping", 0, Map.of());

            case ActionResult.ERROR_PLAYER_OFFLINE:
                return new RecoveryDecision(RecoveryAction.PAUSE, "Player offline", 200, Map.of());

            case ActionResult.ERROR_CHUNK_UNLOADED:
                return new RecoveryDecision(RecoveryAction.PAUSE, "Chunk unloaded", 100, Map.of());

            case ActionResult.ERROR_VALIDATION:
                return new RecoveryDecision(RecoveryAction.ABORT, "Validation failed", 0, Map.of());

            case ActionResult.ERROR_LLM_INVALID:
                return new RecoveryDecision(RecoveryAction.ABORT, "Invalid LLM response", 0, Map.of());

            case ActionResult.ERROR_TIMEOUT:
                if (result.isRetryable()) {
                    if (attemptCount < maxRetries) {
                        return new RecoveryDecision(RecoveryAction.RETRY_SAME, "Timeout, retrying", 60, Map.of());
                    }
                }
                break;

            case ActionResult.ERROR_CANCELLED:
                return new RecoveryDecision(RecoveryAction.ABORT, "Action cancelled", 0, Map.of());

            case ActionResult.ERROR_BLOCKED:
                if (result.isRetryable()) {
                    if (attemptCount < maxRetries) {
                        return new RecoveryDecision(RecoveryAction.RETRY_SAME, "Blocked, retrying", 40, Map.of());
                    }
                }
                break;

            case ActionResult.ERROR_UNKNOWN:
                if (attemptCount >= maxRetries) {
                    return new RecoveryDecision(RecoveryAction.ABORT, "Unknown error, max retries reached", 0, Map.of());
                } else {
                    return new RecoveryDecision(RecoveryAction.RETRY_SAME, "Unknown error, retrying", 20, Map.of());
                }
        }

        // 3. Fallback for exhausted retries across any other errors not explicitly returning above
        if (attemptCount >= maxRetries) {
            if (maxReplans > 0) {
                return new RecoveryDecision(RecoveryAction.REPLAN, "Max retries reached, escalating to replan", 0, Map.of());
            } else {
                return new RecoveryDecision(RecoveryAction.ABORT, "Max retries reached, replan budget exhausted", 0, Map.of());
            }
        }

        // 4. Ultimate default fallback
        return new RecoveryDecision(RecoveryAction.ABORT, "Unhandled error condition", 0, Map.of());
    }
}
