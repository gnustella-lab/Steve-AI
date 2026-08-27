package com.steve.ai.autonomy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalQueueTest {
    @Test
    void ordersInterruptRecoveryPrerequisiteUserThenBackgroundWork() {
        GoalQueue queue = new GoalQueue();
        AgentGoal maintenance = AgentGoal.create("maintain", GoalOrigin.MAINTENANCE,
            GoalPriority.MAINTENANCE, null, 1L);
        AgentGoal user = AgentGoal.create("user goal", GoalOrigin.USER,
            GoalPriority.USER, null, 2L);
        AgentGoal prerequisite = AgentGoal.create("prerequisite", GoalOrigin.PREREQUISITE,
            GoalPriority.PREREQUISITE, user.getId(), 3L);
        AgentGoal recovery = AgentGoal.create("recover", GoalOrigin.RECOVERY,
            GoalPriority.RECOVERY, user.getId(), 4L);
        AgentGoal interrupt = AgentGoal.create("interrupt", GoalOrigin.USER,
            GoalPriority.USER_INTERRUPT, null, 5L);

        queue.enqueue(maintenance);
        queue.enqueue(user);
        queue.enqueue(prerequisite);
        queue.enqueue(recovery);
        queue.enqueue(interrupt);

        assertEquals(interrupt, queue.pollNext());
        queue.cancelActive(10L);
        assertEquals(recovery, queue.pollNext());
        queue.cancelActive(11L);
        queue.pauseActive(10L);
        assertEquals(prerequisite, queue.pollNext());
        queue.cancelActive(11L);
        assertEquals(user, queue.pollNext());
        queue.cancelActive(12L);
        assertEquals(maintenance, queue.pollNext());
        assertNull(queue.pollNext());
    }

    @Test
    void blockedFailedCompletedAndCancelledGoalsNeverAutoResume() {
        GoalQueue queue = new GoalQueue();
        AgentGoal blocked = AgentGoal.create("blocked", GoalOrigin.USER, GoalPriority.USER, null, 1L);
        AgentGoal failed = AgentGoal.create("failed", GoalOrigin.USER, GoalPriority.USER, null, 1L);
        AgentGoal completed = AgentGoal.create("completed", GoalOrigin.USER, GoalPriority.USER, null, 1L);
        AgentGoal cancelled = AgentGoal.create("cancelled", GoalOrigin.USER, GoalPriority.USER, null, 1L);
        blocked.block("missing prerequisite", 2L);
        failed.fail("no route", 2L);
        completed.complete(2L);
        cancelled.cancel(2L);

        queue.enqueue(blocked);
        queue.enqueue(failed);
        queue.enqueue(completed);
        queue.enqueue(cancelled);

        assertNull(queue.pollNext());
        assertEquals(GoalStatus.BLOCKED, blocked.getStatus());
        assertEquals(GoalStatus.FAILED, failed.getStatus());
        assertEquals(GoalStatus.COMPLETED, completed.getStatus());
        assertEquals(GoalStatus.CANCELLED, cancelled.getStatus());
        assertFalse(AutonomyController.canRestoreAsActive(blocked));
        assertFalse(AutonomyController.canRestoreAsActive(failed));
        AgentGoal paused = AgentGoal.create("paused", GoalOrigin.USER, GoalPriority.USER, null, 1L);
        paused.pause(2L);
        assertTrue(AutonomyController.canRestoreAsActive(paused));
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

    @Test
    void pauseAllRetainsPendingGoalsUntilResumeAll() {
        GoalQueue queue = new GoalQueue();
        AgentGoal first = AgentGoal.create("first", GoalOrigin.USER, GoalPriority.USER, null, 1L);
        AgentGoal second = AgentGoal.create("second", GoalOrigin.MAINTENANCE,
            GoalPriority.MAINTENANCE, null, 2L);
        queue.enqueue(first);
        queue.enqueue(second);

        queue.pauseAll(10L);
        assertEquals(0, queue.size());
        assertEquals(2, queue.getPausedGoals().size());

        queue.resumeAll(11L);
        assertEquals(first, queue.pollNext());
    }
}
