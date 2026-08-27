package com.steve.ai.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionExecutorCommandPathRecoveryTest {

    @Test
    void retryablePathingRetriesUntilBoundThenSkips() {
        ActionResult pathing = ActionResult.failure(ActionResult.ERROR_PATHING, "blocked")
            .retryable(true).build();

        assertEquals(ActionExecutor.CommandPathDecision.RETRY,
            ActionExecutor.decideCommandPathFailure(pathing, 0));
        assertEquals(ActionExecutor.CommandPathDecision.RETRY,
            ActionExecutor.decideCommandPathFailure(pathing, 2));
        assertEquals(ActionExecutor.CommandPathDecision.SKIP,
            ActionExecutor.decideCommandPathFailure(pathing, 3));
    }

    @Test
    void permissionDeniedAbortsAndProtectedIsSkippedWithoutRetry() {
        ActionResult denied = ActionResult.failure(ActionResult.ERROR_PERMISSION_DENIED, "denied")
            .build();
        ActionResult protectedRegion = ActionResult.failure(ActionResult.ERROR_PROTECTED, "claim")
            .retryable(true).requiresReplanning(true).build();

        assertEquals(ActionExecutor.CommandPathDecision.ABORT,
            ActionExecutor.decideCommandPathFailure(denied, 0));
        assertEquals(ActionExecutor.CommandPathDecision.SKIP,
            ActionExecutor.decideCommandPathFailure(protectedRegion, 0));
        assertEquals(ActionExecutor.CommandPathDecision.ABORT,
            ActionExecutor.decideCommandPathFailure(null, 0));
    }
}
