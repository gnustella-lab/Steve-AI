package com.steve.ai.memory;

import com.steve.ai.autonomy.AgentGoal;
import com.steve.ai.autonomy.AutonomyMode;
import com.steve.ai.planning.Plan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded structured memory. Transient BaseAction instances are deliberately not persisted.
 */
public class SteveMemory {
    public static final int DATA_VERSION = 3;
    public static final int MAX_RECENT_ACTIONS = 20;
    public static final int MAX_EPISODES = 48;
    public static final int MAX_WORLD_FACTS = 128;
    public static final int MAX_GOAL_HISTORY = 32;
    public static final int MAX_PERSISTED_GOALS = 16;

    private final com.steve.ai.entity.SteveEntity steve;
    private String currentGoal;
    private AgentGoal activeGoal;
    private Plan activePlan;
    private AutonomyMode autonomyModeOverride;
    private final LinkedList<String> recentActions;
    private final LinkedList<EpisodicMemoryEntry> episodes;
    private final LinkedList<WorldFact> worldFacts;
    private final LinkedList<String> goalHistory;
    private final LinkedList<AgentGoal> persistedGoals;

    public SteveMemory(com.steve.ai.entity.SteveEntity steve) {
        this.steve = steve;
        this.currentGoal = "";
        this.recentActions = new LinkedList<>();
        this.episodes = new LinkedList<>();
        this.worldFacts = new LinkedList<>();
        this.goalHistory = new LinkedList<>();
        this.persistedGoals = new LinkedList<>();
    }

    public String getCurrentGoal() {
        return activeGoal != null ? activeGoal.getDescription() : currentGoal;
    }

    public void setCurrentGoal(String goal) {
        this.currentGoal = bounded(goal, 512);
    }

    public AgentGoal getActiveGoal() { return activeGoal; }

    public void setActiveGoal(AgentGoal goal) {
        this.activeGoal = goal;
        this.currentGoal = goal == null ? "" : goal.getDescription();
    }

    public void clearActiveGoal() {
        this.activeGoal = null;
        this.currentGoal = "";
    }

    /** A bounded diagnostic checkpoint; it never contains or resumes a BaseAction. */
    public Plan getActivePlan() { return activePlan; }

    public void setActivePlan(Plan plan) { this.activePlan = plan; }

    public void clearActivePlan() { this.activePlan = null; }

    public AutonomyMode getAutonomyModeOverride() { return autonomyModeOverride; }
    public AutonomyMode getModeOverride() { return autonomyModeOverride; }

    /** Null clears the per-agent override and returns mode selection to configuration. */
    public void setAutonomyModeOverride(AutonomyMode mode) { this.autonomyModeOverride = mode; }
    public void setModeOverride(AutonomyMode mode) { setAutonomyModeOverride(mode); }
    public void clearAutonomyModeOverride() { autonomyModeOverride = null; }

    public void rememberGoal(AgentGoal goal) {
        if (goal == null || goal.isTerminal()) return;
        persistedGoals.removeIf(existing -> existing.getId().equals(goal.getId()));
        persistedGoals.addLast(goal);
        while (persistedGoals.size() > MAX_PERSISTED_GOALS) persistedGoals.removeFirst();
    }

    public void removeGoal(UUID goalId) {
        if (goalId != null) persistedGoals.removeIf(goal -> goal.getId().equals(goalId));
    }

    public List<AgentGoal> getPersistedGoals() {
        return List.copyOf(persistedGoals);
    }

    public void clearPersistedGoals() {
        persistedGoals.clear();
    }

    public void addAction(String action) {
        if (action == null || action.isBlank()) return;
        recentActions.addLast(bounded(action, 256));
        while (recentActions.size() > MAX_RECENT_ACTIONS) recentActions.removeFirst();
    }

    public List<String> getRecentActions(int count) {
        if (count <= 0) return List.of();
        int startIndex = Math.max(0, recentActions.size() - count);
        return List.copyOf(recentActions.subList(startIndex, recentActions.size()));
    }

    public List<EpisodicMemoryEntry> getEpisodes() { return List.copyOf(episodes); }
    public List<WorldFact> getWorldFacts() { return List.copyOf(worldFacts); }
    public List<String> getGoalHistory() { return List.copyOf(goalHistory); }

    public void addEpisode(EpisodicMemoryEntry entry) {
        if (entry == null) return;
        episodes.addLast(entry);
        while (episodes.size() > MAX_EPISODES) episodes.removeFirst();
    }

    public void rememberWorldFact(WorldFact fact) {
        if (fact == null) return;
        worldFacts.removeIf(existing -> sameFact(existing, fact));
        worldFacts.addLast(fact);
        while (worldFacts.size() > MAX_WORLD_FACTS) worldFacts.removeFirst();
    }

    /**
     * Legacy, context-free recall retained only for compatibility. Autonomous planning must use
     * the contextual overload so TTL, dimension and position cannot be bypassed accidentally.
     */
    @Deprecated(forRemoval = false)
    public List<WorldFact> getRelevantFacts(String query, int limit) {
        if (limit <= 0) return List.of();
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return worldFacts.stream()
            .filter(fact -> normalized.isBlank()
                || fact.key().toLowerCase(Locale.ROOT).contains(normalized)
                || fact.kind().name().toLowerCase(Locale.ROOT).contains(normalized)
                || fact.details().values().stream().anyMatch(value ->
                    value.toLowerCase(Locale.ROOT).contains(normalized)))
            .sorted(Comparator.comparingDouble(WorldFact::confidence).reversed()
                .thenComparing(Comparator.comparingLong(WorldFact::lastSeenTick).reversed()))
            .limit(limit)
            .toList();
    }

    /**
     * Context-aware recall filters expired and cross-dimension facts, matches query tokens,
     * and ranks closer facts ahead of equally relevant distant facts.
     */
    public List<WorldFact> getRelevantFacts(String query, int limit, long now,
            String dimension, BlockPos position) {
        return getRelevantFacts(query, limit, now, dimension, position, Double.POSITIVE_INFINITY);
    }

    /** Same as contextual recall, with an optional proximity radius in blocks. */
    public List<WorldFact> getRelevantFacts(String query, int limit, long now,
            String dimension, BlockPos position, double maxDistance) {
        if (limit <= 0) return List.of();
        String requestedDimension = dimension == null ? ""
            : dimension.trim().toLowerCase(Locale.ROOT);
        List<String> queryTokens = tokens(query);
        return worldFacts.stream()
            .filter(fact -> !fact.isExpired(now))
            .filter(fact -> requestedDimension.isBlank()
                || requestedDimension.equals(fact.dimension()))
            .map(fact -> new ScoredFact(fact, tokenScore(fact, queryTokens), distanceSquared(fact, position)))
            .filter(scored -> queryTokens.isEmpty()
                || scored.fact().kind() == WorldFact.Kind.PROTECTED
                || scored.score() > 0)
            .filter(scored -> maxDistance < 0.0 || scored.distanceSquared() == Double.POSITIVE_INFINITY
                || scored.distanceSquared() <= maxDistance * maxDistance)
            .sorted(Comparator.comparingInt((ScoredFact value) ->
                    value.score() + kindWeight(value.fact().kind())).reversed()
                .thenComparingDouble(ScoredFact::distanceSquared)
                .thenComparing(Comparator.comparingDouble((ScoredFact value) -> value.fact().confidence()).reversed())
                .thenComparing(Comparator.comparingLong((ScoredFact value) -> value.fact().lastSeenTick()).reversed()))
            .limit(limit)
            .map(ScoredFact::fact)
            .toList();
    }

    public void rememberFailure(String fingerprint, String details, String dimension,
            BlockPos position, long tick) {
        rememberWorldFact(new WorldFact(WorldFact.Kind.FAILURE, fingerprint, dimension, position,
            tick, 1.0, 48_000L, Map.of("details", bounded(details, 256))));
    }

    public void recordGoalOutcome(AgentGoal goal, String result, long tick) {
        if (goal == null) return;
        String summary = bounded(goal.getDescription() + " => " + result, 512);
        goalHistory.remove(summary);
        goalHistory.addLast(summary);
        while (goalHistory.size() > MAX_GOAL_HISTORY) goalHistory.removeFirst();
    }

    public void clearTaskQueue() {
        // Retained for command compatibility. The goal queue is owned by AutonomyController.
        clearActiveGoal();
        clearActivePlan();
    }

    public void saveToNBT(CompoundTag tag) {
        if (tag == null) return;
        tag.putInt("DataVersion", DATA_VERSION);
        tag.putString("CurrentGoal", bounded(currentGoal, 512));
        if (activeGoal != null && !activeGoal.isTerminal()) {
            tag.put("ActiveGoal", activeGoal.save());
        }
        if (activePlan != null) {
            tag.put("ActivePlan", activePlan.save());
        }
        if (autonomyModeOverride != null) {
            tag.putString("AutonomyModeOverride", autonomyModeOverride.name());
        }

        ListTag pendingGoals = new ListTag();
        persistedGoals.stream().limit(MAX_PERSISTED_GOALS).forEach(goal -> pendingGoals.add(goal.save()));
        tag.put("PendingGoals", pendingGoals);

        ListTag actions = new ListTag();
        recentActions.stream().limit(MAX_RECENT_ACTIONS)
            .forEach(action -> actions.add(StringTag.valueOf(bounded(action, 256))));
        tag.put("RecentActions", actions);

        ListTag episodeList = new ListTag();
        episodes.stream().limit(MAX_EPISODES).forEach(entry -> episodeList.add(entry.save()));
        tag.put("Episodes", episodeList);

        ListTag factList = new ListTag();
        worldFacts.stream().limit(MAX_WORLD_FACTS).forEach(fact -> factList.add(fact.save()));
        tag.put("WorldFacts", factList);

        ListTag historyList = new ListTag();
        goalHistory.stream().limit(MAX_GOAL_HISTORY)
            .forEach(value -> historyList.add(StringTag.valueOf(bounded(value, 512))));
        tag.put("GoalHistory", historyList);
    }

    /** Full replacement load: fields absent from the incoming tag cannot remain stale. */
    public void loadFromNBT(CompoundTag tag) {
        clearAllState();
        if (tag == null || tag.isEmpty()) return;

        activeGoal = tag.contains("ActiveGoal", Tag.TAG_COMPOUND)
            ? AgentGoal.load(tag.getCompound("ActiveGoal")) : null;
        currentGoal = bounded(tag.contains("CurrentGoal") ? tag.getString("CurrentGoal") : "", 512);
        if (activeGoal != null) currentGoal = activeGoal.getDescription();

        if (tag.contains("ActivePlan", Tag.TAG_COMPOUND)) {
            activePlan = Plan.load(tag.getCompound("ActivePlan"));
        }
        if (tag.contains("AutonomyModeOverride")) {
            autonomyModeOverride = AutonomyMode.tryParse(tag.getString("AutonomyModeOverride"));
        }

        if (tag.contains("PendingGoals", Tag.TAG_LIST)) {
            ListTag pendingGoals = tag.getList("PendingGoals", Tag.TAG_COMPOUND);
            int start = Math.max(0, pendingGoals.size() - MAX_PERSISTED_GOALS);
            for (int i = start; i < pendingGoals.size(); i++) {
                rememberGoal(AgentGoal.load(pendingGoals.getCompound(i)));
            }
        }

        if (tag.contains("RecentActions", Tag.TAG_LIST)) {
            ListTag actions = tag.getList("RecentActions", Tag.TAG_STRING);
            int start = Math.max(0, actions.size() - MAX_RECENT_ACTIONS);
            for (int i = start; i < actions.size(); i++) addAction(actions.getString(i));
        }

        if (tag.contains("Episodes", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Episodes", Tag.TAG_COMPOUND);
            int start = Math.max(0, list.size() - MAX_EPISODES);
            for (int i = start; i < list.size(); i++) addEpisode(EpisodicMemoryEntry.load(list.getCompound(i)));
        }

        if (tag.contains("WorldFacts", Tag.TAG_LIST)) {
            ListTag list = tag.getList("WorldFacts", Tag.TAG_COMPOUND);
            int start = Math.max(0, list.size() - MAX_WORLD_FACTS);
            for (int i = start; i < list.size(); i++) rememberWorldFact(WorldFact.load(list.getCompound(i)));
        }

        if (tag.contains("GoalHistory", Tag.TAG_LIST)) {
            ListTag list = tag.getList("GoalHistory", Tag.TAG_STRING);
            int start = Math.max(0, list.size() - MAX_GOAL_HISTORY);
            for (int i = start; i < list.size(); i++) goalHistory.add(bounded(list.getString(i), 512));
        }
    }

    private void clearAllState() {
        currentGoal = "";
        activeGoal = null;
        activePlan = null;
        autonomyModeOverride = null;
        recentActions.clear();
        episodes.clear();
        worldFacts.clear();
        goalHistory.clear();
        persistedGoals.clear();
    }

    private static int tokenScore(WorldFact fact, List<String> queryTokens) {
        if (queryTokens.isEmpty()) return 0;
        List<String> factTokens = new ArrayList<>();
        factTokens.addAll(tokens(fact.key()));
        factTokens.addAll(tokens(fact.kind().name()));
        fact.details().values().forEach(value -> factTokens.addAll(tokens(value)));
        int score = 0;
        for (String queryToken : queryTokens) {
            if (factTokens.stream().anyMatch(value -> value.equals(queryToken) || value.contains(queryToken))) {
                score++;
            }
        }
        return score;
    }

    private static int kindWeight(WorldFact.Kind kind) {
        if (kind == null) return 0;
        return switch (kind) {
            case PROTECTED -> 4;
            case FAILURE -> 2;
            case RESOURCE, CRAFTING_STATION -> 1;
            default -> 0;
        };
    }

    private static List<String> tokens(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9:_-]+", " ")
                .trim()
                .split("\\s+"));
    }

    private static double distanceSquared(WorldFact fact, BlockPos position) {
        if (position == null || fact.position() == null) return Double.POSITIVE_INFINITY;
        return position.distSqr(fact.position());
    }

    private static boolean sameFact(WorldFact left, WorldFact right) {
        return left.kind() == right.kind()
            && left.key().equals(right.key())
            && left.dimension().equals(right.dimension())
            && Objects.equals(left.position(), right.position());
    }

    private static String bounded(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private record ScoredFact(WorldFact fact, int score, double distanceSquared) { }
}
