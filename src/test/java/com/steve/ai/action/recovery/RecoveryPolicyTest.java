package com.steve.ai.action.recovery;

import com.steve.ai.action.ActionResult;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryPolicyTest {

    private final RecoveryPolicy policy = new RecoveryPolicy();

    @Test
    void testPathingRetryableUnderMax() {
        ActionResult result = ActionResult.builder()
            .success(false)
            .errorCode(ActionResult.ERROR_PATHING)
            .message("Stuck")
            .retryable(true)
            .build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "walk", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.RETRY_SAME, decision.action());
        assertEquals(40, decision.delayTicks());
    }

    @Test
    void testPathingOverMax() {
        ActionResult result = ActionResult.builder()
            .success(false)
            .errorCode(ActionResult.ERROR_PATHING)
            .message("Stuck")
            .retryable(true)
            .build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "walk", 3, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.SKIP_CONTINUE, decision.action());
    }

    @Test
    void testResourceRetryableWithBudget() {
        ActionResult result = ActionResult.builder()
            .success(false)
            .errorCode(ActionResult.ERROR_RESOURCE)
            .message("No wood")
            .retryable(true)
            .build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "mine", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.REPLAN, decision.action());
    }

    @Test
    void testResourceRetryableWithoutBudget() {
        ActionResult result = ActionResult.builder()
            .success(false)
            .errorCode(ActionResult.ERROR_RESOURCE)
            .message("No wood")
            .retryable(true)
            .build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "mine", 1, 3, 0);
        assertEquals(RecoveryPolicy.RecoveryAction.ABORT, decision.action());
    }

    @Test
    void testInventoryFull() {
        ActionResult result = ActionResult.failure(ActionResult.ERROR_INVENTORY_FULL, "Full").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "mine", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.SKIP_CONTINUE, decision.action());
    }

    @Test
    void testToolMissing() {
        ActionResult result = ActionResult.failure(ActionResult.ERROR_TOOL_MISSING, "Need pickaxe").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "mine", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.RETRY_MODIFIED, decision.action());
        assertEquals("equip_tool", decision.modifications().get("hint"));
    }

    @Test
    void testToolBroken() {
        ActionResult result = ActionResult.failure(ActionResult.ERROR_TOOL_BROKEN, "Pickaxe broke").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "mine", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.RETRY_MODIFIED, decision.action());
        assertEquals("replace_tool", decision.modifications().get("hint"));
    }

    @Test
    void testProtected() {
        ActionResult result = ActionResult.failure(ActionResult.ERROR_PROTECTED, "Spawn protected").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "mine", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.SKIP_CONTINUE, decision.action());
    }

    @Test
    void testEntityGone() {
        ActionResult result = ActionResult.failure(ActionResult.ERROR_ENTITY_GONE, "Pig despawned").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "attack", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.SKIP_CONTINUE, decision.action());
    }

    @Test
    void testPlayerOffline() {
        ActionResult result = ActionResult.failure(ActionResult.ERROR_PLAYER_OFFLINE, "Offline").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "follow", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.PAUSE, decision.action());
        assertEquals(200, decision.delayTicks());
    }

    @Test
    void testChunkUnloaded() {
        ActionResult result = ActionResult.failure(ActionResult.ERROR_CHUNK_UNLOADED, "Unloaded").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "walk", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.PAUSE, decision.action());
        assertEquals(100, decision.delayTicks());
    }

    @Test
    void testValidation() {
        ActionResult result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Bad plan").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "craft", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.ABORT, decision.action());
    }

    @Test
    void testLlmInvalid() {
        ActionResult result = ActionResult.failure(ActionResult.ERROR_LLM_INVALID, "Garbage").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "think", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.ABORT, decision.action());
    }

    @Test
    void testTimeoutRetryable() {
        ActionResult result = ActionResult.builder()
            .success(false)
            .errorCode(ActionResult.ERROR_TIMEOUT)
            .message("Too long")
            .retryable(true)
            .build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "walk", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.RETRY_SAME, decision.action());
        assertEquals(60, decision.delayTicks());
    }

    @Test
    void testCancelled() {
        ActionResult result = ActionResult.failure(ActionResult.ERROR_CANCELLED, "Stop").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "walk", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.ABORT, decision.action());
    }

    @Test
    void testUnknownUnderMax() {
        ActionResult result = ActionResult.failure(ActionResult.ERROR_UNKNOWN, "Wat").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "walk", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.RETRY_SAME, decision.action());
    }

    @Test
    void testUnknownOverMax() {
        ActionResult result = ActionResult.failure(ActionResult.ERROR_UNKNOWN, "Wat").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "walk", 3, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.ABORT, decision.action());
    }

    @Test
    void testBlockedRetryable() {
        ActionResult result = ActionResult.builder()
            .success(false)
            .errorCode(ActionResult.ERROR_BLOCKED)
            .message("Wall")
            .retryable(true)
            .build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "walk", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.RETRY_SAME, decision.action());
        assertEquals(40, decision.delayTicks());
    }

    @Test
    void testRequiresReplanningWithBudget() {
        ActionResult result = ActionResult.builder()
            .success(false)
            .errorCode("some_error")
            .message("Need new plan")
            .requiresReplanning(true)
            .build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "walk", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.REPLAN, decision.action());
    }

    @Test
    void testRequiresReplanningWithoutBudget() {
        ActionResult result = ActionResult.builder()
            .success(false)
            .errorCode("some_error")
            .message("Need new plan")
            .requiresReplanning(true)
            .build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "walk", 1, 3, 0);
        assertEquals(RecoveryPolicy.RecoveryAction.ABORT, decision.action());
    }

    @Test
    void testNullErrorCode() {
        ActionResult result = ActionResult.builder()
            .success(false)
            .errorCode(null)
            .message("Null code")
            .build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "walk", 1, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.RETRY_SAME, decision.action());
    }
    
    @Test
    void testAnyErrorEscalationToReplan() {
        ActionResult result = ActionResult.failure("random_error", "Msg").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "walk", 3, 3, 1);
        assertEquals(RecoveryPolicy.RecoveryAction.REPLAN, decision.action());
    }

    @Test
    void testAnyErrorEscalationToAbort() {
        ActionResult result = ActionResult.failure("random_error", "Msg").build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "walk", 3, 3, 0);
        assertEquals(RecoveryPolicy.RecoveryAction.ABORT, decision.action());
    }
    
    @Test
    void testPartialSuccess() {
        ActionResult result = ActionResult.builder()
            .success(false)
            .partialSuccess(true)
            .errorCode(ActionResult.ERROR_INVENTORY_FULL)
            .message("Got some, then full")
            .build();
        RecoveryPolicy.RecoveryDecision decision = policy.decide(result, "mine", 1, 3, 1);
        // Should just hit the inventory full rule
        assertEquals(RecoveryPolicy.RecoveryAction.SKIP_CONTINUE, decision.action());
    }
}
