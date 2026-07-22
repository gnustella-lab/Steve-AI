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
}
