package com.steve.ai.planning;

import com.steve.ai.action.Task;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlanTest {

    @Test
    void keepsPlanIdentityAndArchivesCompletedStepsAcrossHorizonRevisions() {
        Plan plan = new Plan("collect", UUID.randomUUID(), UUID.randomUUID(), 3, 3, 3, 100, 0);
        UUID planId = plan.getPlanId();
        plan.loadHorizon(List.of(new Task("mine", Map.of("block", "stone"))),
            "first", "initial", 10L);
        plan.markCurrentStepActive(11L);
        plan.recordCurrentStepResult(com.steve.ai.action.ActionResult.success("mined").build(), 12L);
        plan.advanceToNextTask(13L);
        int firstRevision = plan.getRevision();

        plan.loadHorizon(List.of(new Task("place", Map.of("block", "stone"))),
            "second", "replan", 14L);

        assertEquals(planId, plan.getPlanId());
        assertTrue(plan.getRevision() > firstRevision);
        assertEquals(1, plan.getCompletedStepSummaries().size());
        assertEquals("mine", plan.getCompletedStepSummaries().get(0).getAction());
    }

    @Test
    void boundsCompletedStepArchiveWhenHorizonsAreReplacedRepeatedly() {
        Plan plan = new Plan("repeat", null, null, 3, 3, 3, 0, 0);
        for (int i = 0; i < Plan.MAX_COMPLETED_STEP_SUMMARIES + 8; i++) {
            plan.loadHorizon(List.of(new Task("step_" + i, Map.of())), "summary", "replan", i);
            plan.markCurrentStepActive(i);
            plan.recordCurrentStepResult(com.steve.ai.action.ActionResult.success("done").build(), i);
            plan.advanceToNextTask(i);
        }

        assertTrue(plan.getCompletedStepSummaries().size() <= Plan.MAX_COMPLETED_STEP_SUMMARIES);
    }

    @Test
    void testStateTransitions() {
        Plan plan = new Plan("test", UUID.randomUUID(), UUID.randomUUID(), 3, 3, 3, 100, 0);
        assertEquals(Plan.State.CREATED, plan.getState());
        
        plan.setState(Plan.State.PLANNING);
        assertEquals(Plan.State.PLANNING, plan.getState());
        
        plan.setState(Plan.State.COMPLETED);
        
        assertThrows(IllegalStateException.class, () -> plan.setState(Plan.State.EXECUTING));
    }

    @Test
    void testAttemptAndReplanCounting() {
        Plan plan = new Plan("test", UUID.randomUUID(), UUID.randomUUID(), 3, 2, 5, 100, 0);
        
        assertTrue(plan.canRetry());
        plan.incrementAttempt();
        plan.incrementAttempt();
        plan.incrementAttempt();
        assertFalse(plan.canRetry());
        
        assertTrue(plan.canReplan());
        plan.incrementReplan();
        plan.incrementReplan();
        assertFalse(plan.canReplan());
    }

    @Test
    void testTimeoutDetection() {
        Plan plan = new Plan("test", UUID.randomUUID(), UUID.randomUUID(), 3, 3, 3, 100, 0);
        
        assertFalse(plan.isTimedOut(50));
        assertTrue(plan.isTimedOut(150));
    }

    @Test
    void testProgressTracking() {
        Plan plan = new Plan("test", UUID.randomUUID(), UUID.randomUUID(), 3, 3, 3, 100, 0);
        Map<String, Object> params = new HashMap<>();
        List<Task> tasks = Arrays.asList(new Task("action1", params), new Task("action2", params));
        
        plan.loadTasks(tasks, "Doing things");
        assertEquals("0/2 tasks", plan.getProgress());
        
        plan.advanceToNextTask(10);
        assertEquals("1/2 tasks", plan.getProgress());
    }

    @Test
    void testNbtSaveLoadRoundtrip() {
        UUID player = UUID.randomUUID();
        UUID steve = UUID.randomUUID();
        Plan plan = new Plan("build house", player, steve, 3, 2, 5, 200, 1000);
        
        Map<String, Object> params = new HashMap<>();
        params.put("block", "stone");
        plan.loadTasks(Arrays.asList(new Task("place", params)), "Building");
        plan.setState(Plan.State.EXECUTING);
        
        CompoundTag tag = plan.save();
        Plan loaded = Plan.load(tag);
        
        assertEquals(plan.getPlanId(), loaded.getPlanId());
        assertEquals(plan.getOriginalCommand(), loaded.getOriginalCommand());
        assertEquals(plan.getRequestingPlayer(), loaded.getRequestingPlayer());
        assertEquals(plan.getSteveUuid(), loaded.getSteveUuid());
        assertEquals(plan.getState(), loaded.getState());
        assertEquals(plan.getSummary(), loaded.getSummary());
        assertEquals(plan.getCreatedAtTick(), loaded.getCreatedAtTick());
        
        assertEquals(1, loaded.getTasks().size());
        assertEquals("place", loaded.getTasks().get(0).getAction());
    }
}
