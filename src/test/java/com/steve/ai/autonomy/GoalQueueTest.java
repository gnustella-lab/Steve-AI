package com.steve.ai.autonomy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GoalQueueTest {
    @Test
    void returnsHigherPriorityGoalsWithoutDroppingPausedWork() {
        GoalQueue queue = new GoalQueue();
        AgentGoal maintenance = AgentGoal.create("maintain", GoalOrigin.MAINTENANCE,
            GoalPriority.MAINTENANCE, null, 1L);
        AgentGoal user = AgentGoal.create("user goal", GoalOrigin.USER,
            GoalPriority.USER, null, 2L);
        AgentGoal prerequisite = AgentGoal.create("prerequisite", GoalOrigin.PREREQUISITE,
            GoalPriority.PREREQUISITE, user.getId(), 3L);

        queue.enqueue(maintenance);
        queue.enqueue(user);
        queue.enqueue(prerequisite);

        assertEquals(user, queue.pollNext());
        queue.pauseActive(10L);
        assertEquals(prerequisite, queue.pollNext());
        queue.cancelActive(11L);
        assertEquals(maintenance, queue.pollNext());
        assertNull(queue.pollNext());
    }

    @Test
    void stopCancelsActiveGoalAndDoesNotRequeueIt() {
        GoalQueue queue = new GoalQueue();
        AgentGoal goal = AgentGoal.create("long job", GoalOrigin.USER,
            GoalPriority.USER, null, 1L);
        queue.enqueue(goal);
        assertEquals(goal, queue.pollNext());

        queue.cancelActive(20L);

        assertEquals(GoalStatus.CANCELLED, goal.getStatus());
        assertNull(queue.pollNext());
        assertNull(queue.getActive());
    }
}
