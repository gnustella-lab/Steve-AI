package com.steve.ai.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentStateMachineTest {
    @Test
    void acceptsTheAutonomousLoopAndRecoveryTransitions() {
        AgentStateMachine machine = new AgentStateMachine(null, "test");

        assertTrue(machine.transitionTo(AgentState.OBSERVING));
        assertTrue(machine.transitionTo(AgentState.PLANNING));
        assertTrue(machine.transitionTo(AgentState.EXECUTING));
        assertTrue(machine.transitionTo(AgentState.EVALUATING));
        assertTrue(machine.transitionTo(AgentState.OBSERVING));
        assertTrue(machine.transitionTo(AgentState.PLANNING));
        assertTrue(machine.transitionTo(AgentState.RECOVERING));
        assertTrue(machine.transitionTo(AgentState.EXECUTING));
        assertEquals(AgentState.EXECUTING, machine.getCurrentState());
    }

    @Test
    void rejectsSkippingFromIdleToCompleted() {
        AgentStateMachine machine = new AgentStateMachine(null, "test");
        assertFalse(machine.transitionTo(AgentState.COMPLETED));
        assertEquals(AgentState.IDLE, machine.getCurrentState());
    }
}
