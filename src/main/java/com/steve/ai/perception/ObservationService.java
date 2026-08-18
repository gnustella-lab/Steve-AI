package com.steve.ai.perception;

import com.steve.ai.action.ActionResult;
import com.steve.ai.autonomy.AgentGoal;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.memory.SteveMemory;
import com.steve.ai.memory.WorldFact;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Schedules bounded perception work and returns immutable snapshots. It never runs from every entity tick.
 */
public final class ObservationService {
    private final int minimumIntervalTicks;
    private ObservationSnapshot lastSnapshot;
    private long lastCaptureTick = Long.MIN_VALUE;

    public ObservationService(int minimumIntervalTicks) {
        this.minimumIntervalTicks = Math.max(1, minimumIntervalTicks);
    }

    public ObservationSnapshot capture(SteveEntity steve, AgentGoal goal, AgentGoal subgoal,
            String currentAction, ActionResult lastResult, boolean force) {
        long tick = currentTick(steve);
        if (!force && lastSnapshot != null && tick - lastCaptureTick < minimumIntervalTicks) {
            return lastSnapshot;
        }

        ObservationSnapshot base = ObservationSnapshot.capture(steve);
        ObservationSnapshot.Builder builder = base.toBuilder();
        if (goal != null) builder.currentGoal(goal.getDescription());
        if (subgoal != null) builder.activeSubgoal(subgoal.getDescription());
        if (currentAction != null) builder.currentAction(currentAction);
        if (lastResult != null) {
            builder.lastActionResult(formatResult(lastResult));
        }
        builder.navigationState(steve.getNavigation().isInProgress() ? "in_progress" : "idle");

        SteveMemory memory = steve.getMemory();
        if (memory != null) {
            String query = goal == null ? memory.getCurrentGoal() : goal.getDescription();
            List<String> facts = memory.getRelevantFacts(query, 8).stream()
                .map(ObservationService::formatFact)
                .toList();
            builder.relevantMemory(facts);
            builder.protectedPositions(memory.getWorldFacts().stream()
                .filter(fact -> fact.kind() == WorldFact.Kind.PROTECTED)
                .limit(8)
                .map(ObservationService::formatFact)
                .toList());
        }

        lastSnapshot = builder.capturedAtTick(tick).build();
        lastCaptureTick = tick;
        return lastSnapshot;
    }

    public ObservationSnapshot getLastSnapshot() { return lastSnapshot; }

    public void clear() {
        lastSnapshot = null;
        lastCaptureTick = Long.MIN_VALUE;
    }

    private static long currentTick(SteveEntity steve) {
        if (steve != null && steve.level() instanceof ServerLevel level && level.getServer() != null) {
            return level.getServer().getTickCount();
        }
        return 0L;
    }

    private static String formatResult(ActionResult result) {
        String code = result.getErrorCode() == null ? "success" : result.getErrorCode();
        return code + ": " + result.getMessage();
    }

    private static String formatFact(WorldFact fact) {
        return fact.kind().name().toLowerCase(java.util.Locale.ROOT) + ":" + fact.key()
            + (fact.position() == null ? "" : "@" + fact.position().toShortString());
    }
}
