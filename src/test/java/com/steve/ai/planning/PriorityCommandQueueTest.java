package com.steve.ai.planning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PriorityCommandQueueTest {

    private PriorityCommandQueue queue;

    @BeforeEach
    void setUp() {
        queue = new PriorityCommandQueue();
    }

    @Test
    void testPriorityOrdering() {
        Plan p1 = new Plan("low", UUID.randomUUID(), UUID.randomUUID(), 3, 3, 3, 100, 0);
        Plan p2 = new Plan("high", UUID.randomUUID(), UUID.randomUUID(), 3, 3, 3, 100, 0);
        Plan p3 = new Plan("normal", UUID.randomUUID(), UUID.randomUUID(), 3, 3, 3, 100, 0);

        queue.enqueue(p1, CommandPriority.LOW);
        queue.enqueue(p2, CommandPriority.HIGH);
        queue.enqueue(p3, CommandPriority.NORMAL);

        assertEquals(p2, queue.dequeue());
        assertEquals(p3, queue.dequeue());
        assertEquals(p1, queue.dequeue());
        assertNull(queue.dequeue());
    }

    @Test
    void testEmergencyInterruption() {
        Plan p1 = new Plan("normal", UUID.randomUUID(), UUID.randomUUID(), 3, 3, 3, 100, 0);
        queue.enqueue(p1, CommandPriority.NORMAL);
        Plan current = queue.dequeue();
        current.setState(Plan.State.EXECUTING);

        Plan emergency = new Plan("emergency", UUID.randomUUID(), UUID.randomUUID(), 3, 3, 3, 100, 0);
        queue.enqueue(emergency, CommandPriority.EMERGENCY);

        assertEquals(Plan.State.PAUSED, p1.getState());
        
        assertEquals(emergency, queue.dequeue());
    }

    @Test
    void testPauseResume() {
        Plan p1 = new Plan("normal", UUID.randomUUID(), UUID.randomUUID(), 3, 3, 3, 100, 0);
        queue.enqueue(p1, CommandPriority.NORMAL);
        
        queue.pause(p1.getPlanId());
        assertEquals(Plan.State.PAUSED, p1.getState());
        
        queue.resume(p1.getPlanId());
        assertNotEquals(Plan.State.PAUSED, p1.getState());
    }

    @Test
    void testCancel() {
        Plan p1 = new Plan("normal", UUID.randomUUID(), UUID.randomUUID(), 3, 3, 3, 100, 0);
        queue.enqueue(p1, CommandPriority.NORMAL);
        queue.dequeue();
        
        assertTrue(queue.cancel(p1.getPlanId()));
        assertEquals(Plan.State.CANCELLED, p1.getState());
        assertNull(queue.getCurrentPlan());
    }

    @Test
    void testEmptyQueueBehavior() {
        assertTrue(queue.isEmpty());
        assertNull(queue.dequeue());
        assertNull(queue.getCurrentPlan());
        assertEquals(0, queue.size());
    }
}
