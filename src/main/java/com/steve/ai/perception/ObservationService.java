package com.steve.ai.perception;

import com.steve.ai.action.ActionResult;
import com.steve.ai.autonomy.AgentGoal;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.memory.SteveMemory;
import com.steve.ai.memory.WorldFact;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Schedules bounded perception work and returns immutable snapshots. It never runs from every entity tick.
 */
public final class ObservationService {
    private static final int MAX_RELEVANT_FACTS = 8;
    private static final int MAX_PROTECTED_FACTS = 8;
    private final int minimumIntervalTicks;
    private ObservationSnapshot lastSnapshot;
    private long lastCaptureTick = Long.MIN_VALUE;

    public ObservationService(int minimumIntervalTicks) {
        this.minimumIntervalTicks = Math.max(1, minimumIntervalTicks);
    }

    public ObservationSnapshot capture(SteveEntity steve, AgentGoal goal, AgentGoal subgoal,
            String currentAction, ActionResult lastResult, boolean force) {
        if (steve == null) {
            return lastSnapshot;
        }
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
        if (steve.getNavigation() != null) {
            builder.navigationState(steve.getNavigation().isInProgress() ? "in_progress" : "idle");
        }

        SteveMemory memory = steve.getMemory();
        if (memory != null) {
            String query = goal == null ? memory.getCurrentGoal() : goal.getDescription();
            String dimension = base.getDimension();
            BlockPos origin = new BlockPos(base.getX(), base.getY(), base.getZ());
            List<WorldFact> relevant = memory.getRelevantFacts(
                query, MAX_RELEVANT_FACTS, tick, dimension, origin);
            builder.relevantMemory(relevant.stream().map(ObservationService::formatFact).toList());
            builder.protectedPositions(memory.getRelevantFacts(
                    "", MAX_PROTECTED_FACTS * 4, tick, dimension, origin).stream()
                .filter(fact -> fact.kind() == WorldFact.Kind.PROTECTED)
                .limit(MAX_PROTECTED_FACTS)
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
        if (steve != null && steve.level() instanceof ServerLevel level
                && level.getServer() != null) {
            return level.getServer().getTickCount();
        }
        return 0L;
    }

    /**
     * Selects a small, deterministic fact set without comparing the complete goal string.
     * Dimension and TTL are hard filters; token overlap, proximity, confidence and recency
     * provide stable relevance ordering.
     */
    public static List<WorldFact> selectRelevantFacts(List<WorldFact> facts, String query,
            String dimension, BlockPos origin, long now, int limit) {
        if (facts == null || limit <= 0) return List.of();
        Set<String> queryTokens = tokens(query);
        String normalizedDimension = dimension == null ? "" : dimension.toLowerCase(Locale.ROOT);
        return facts.stream()
            .filter(fact -> fact != null && !fact.isExpired(now))
            .filter(fact -> normalizedDimension.isBlank()
                || (!fact.dimension().isBlank()
                    && fact.dimension().equalsIgnoreCase(normalizedDimension)))
            .map(fact -> new ScoredFact(fact, score(fact, queryTokens, origin, normalizedDimension),
                distanceSquared(fact.position(), origin)))
            .sorted(Comparator.comparingDouble(ScoredFact::score).reversed()
                .thenComparingLong(ScoredFact::distance)
                .thenComparing(Comparator.comparingDouble((ScoredFact value) -> value.fact().confidence()).reversed())
                .thenComparing(Comparator.comparingLong((ScoredFact value) -> value.fact().lastSeenTick()).reversed())
                .thenComparing(value -> value.fact().key()))
            .limit(limit)
            .map(ScoredFact::fact)
            .toList();
    }

    private static double score(WorldFact fact, Set<String> queryTokens, BlockPos origin,
            String dimension) {
        Set<String> factTokens = new HashSet<>();
        factTokens.addAll(tokens(fact.key()));
        factTokens.addAll(tokens(fact.kind().name()));
        fact.details().forEach((key, value) -> {
            factTokens.addAll(tokens(key));
            factTokens.addAll(tokens(value));
        });
        long matches = queryTokens.stream().filter(factTokens::contains).count();
        double proximity = fact.position() == null || origin == null
            ? 0.0 : 100.0 / (1.0 + distanceSquared(fact.position(), origin));
        double sameDimension = dimension.isBlank() || fact.dimension().isBlank()
            ? 0.0 : 20.0;
        return matches * 100.0 + sameDimension + proximity + fact.confidence();
    }

    private static long distanceSquared(BlockPos left, BlockPos right) {
        if (left == null || right == null) return Long.MAX_VALUE;
        long dx = (long) left.getX() - right.getX();
        long dy = (long) left.getY() - right.getY();
        long dz = (long) left.getZ() - right.getZ();
        return saturatingAdd(saturatingAdd(dx * dx, dy * dy), dz * dz);
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    private static Set<String> tokens(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return java.util.Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
            .filter(token -> token.length() >= 2)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String formatResult(ActionResult result) {
        String code = result.getErrorCode() == null ? "success" : result.getErrorCode();
        return code + ": " + result.getMessage();
    }

    public static String formatFact(WorldFact fact) {
        return fact.kind().name().toLowerCase(Locale.ROOT) + ":" + fact.key()
            + (fact.position() == null ? "" : "@" + fact.position().toShortString());
    }

    private record ScoredFact(WorldFact fact, double score, long distance) {
    }
}
