package com.steve.ai.execution;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.action.actions.BaseAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class InterceptorChainTest {
    @Test
    void interceptorExceptionsFailClosedBeforeActionStarts() {
        InterceptorChain chain = new InterceptorChain();
        chain.addInterceptor(new ActionInterceptor() {
            @Override
            public boolean beforeAction(BaseAction action, ActionContext context) {
                throw new IllegalStateException("safety check unavailable");
            }
        });

        assertFalse(chain.executeBeforeAction(new NoopAction(), null));
    }

    private static final class NoopAction extends BaseAction {
        private NoopAction() {
            super(null, new Task("test", Map.of()));
        }

        @Override protected void onStart() { }
        @Override protected void onTick() { }
        @Override protected void onCancel() { }
        @Override public String getDescription() { return "test"; }
    }
}
