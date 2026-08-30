package com.steve.ai.action;

import com.steve.ai.action.actions.BaseAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseActionLifecycleTest {

    @Test
    void invokesFinishExactlyOnceAfterSynchronousCompletion() {
        TestAction action = new TestAction(true);

        action.start();
        action.tick();
        action.cancel();

        assertEquals(1, action.finishCount);
    }

    @Test
    void invokesFinishExactlyOnceAfterRepeatedCancellation() {
        TestAction action = new TestAction(false);

        action.start();
        action.cancel();
        action.cancel();

        assertEquals(1, action.finishCount);
    }

    @Test
    void runtimeExceptionBecomesStructuredFailureAndStillCleansUpExactlyOnce() {
        ThrowingAction action = new ThrowingAction();

        action.start();
        action.tick();

        assertEquals(ActionResult.ERROR_UNKNOWN, action.getResult().getErrorCode());
        action.tick();
        assertEquals(1, action.finishCount);
        assertEquals(1, action.cancelCleanupCount);
    }

    private static final class TestAction extends BaseAction {
        private final boolean completeOnStart;
        private int finishCount;

        private TestAction(boolean completeOnStart) {
            super(null, new Task("test", Map.of()));
            this.completeOnStart = completeOnStart;
        }

        @Override
        protected void onStart() {
            if (completeOnStart) {
                result = ActionResult.success("done").build();
            }
        }

        @Override
        protected void onTick() {
            result = ActionResult.success("done").build();
        }

        @Override
        protected void onCancel() {
        }

        @Override
        protected void onFinish() {
            finishCount++;
        }

        @Override
        public String getDescription() {
            return "test";
        }
    }

    private static final class ThrowingAction extends BaseAction {
        private int finishCount;
        private int cancelCleanupCount;

        private ThrowingAction() {
            super(null, new Task("explode", Map.of()));
        }

        @Override protected void onStart() { }
        @Override protected void onTick() { throw new IllegalStateException("boom"); }
        @Override protected void onCancel() { cancelCleanupCount++; }
        @Override protected void onFinish() { finishCount++; }
        @Override public String getDescription() { return "explode"; }
    }
}
