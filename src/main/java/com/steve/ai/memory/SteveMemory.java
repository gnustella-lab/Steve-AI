package com.steve.ai.memory;

import com.steve.ai.autonomy.AgentGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Bounded structured memory. Transient BaseAction instances are deliberately not persisted.
 */
public class SteveMemory {
    public static final int DATA_VERSION = 2;
    public static final int MAX_RECENT_ACTIONS = 20;
    public static final int MAX_EPISODES = 48;
    public static final int MAX_WORLD_FACTS = 128;
    public static final int MAX_GOAL_HISTORY = 32;
    public static final int MAX_PERSISTED_GOALS = 16;

    private final com.steve.ai.entity.SteveEntity steve;
    private String currentGoal;
    private AgentGoal activeGoal;
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

    public List<EpisodicMemoryEntry> getEpisodes() {
        return List.copyOf(episodes);
    }

    public List<WorldFact> getWorldFacts() {
        return List.copyOf(worldFacts);
    }

    public List<String> getGoalHistory() {
        return List.copyOf(goalHistory);
    }

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

    public void rememberFailure(String fingerprint, String details, String dimension,
            net.minecraft.core.BlockPos position, long tick) {
        rememberWorldFact(new WorldFact(WorldFact.Kind.FAILURE, fingerprint, dimension, position,
            tick, 1.0, 48_000L, java.util.Map.of("details", bounded(details, 256))));
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
    }

    public void saveToNBT(CompoundTag tag) {
        tag.putInt("DataVersion", DATA_VERSION);
        tag.putString("CurrentGoal", bounded(currentGoal, 512));
        if (activeGoal != null && !activeGoal.isTerminal()) {
            tag.put("ActiveGoal", activeGoal.save());
        }
        ListTag pendingGoals = new ListTag();
        persistedGoals.stream().limit(MAX_PERSISTED_GOALS).forEach(goal -> pendingGoals.add(goal.save()));
        tag.put("PendingGoals", pendingGoals);

        ListTag actions = new ListTag();
        recentActions.forEach(action -> actions.add(StringTag.valueOf(bounded(action, 256))));
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

    public void loadFromNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return;
        activeGoal = tag.contains("ActiveGoal", Tag.TAG_COMPOUND)
            ? AgentGoal.load(tag.getCompound("ActiveGoal")) : null;
        currentGoal = bounded(tag.getString("CurrentGoal"), 512);
        if (activeGoal != null) currentGoal = activeGoal.getDescription();

        persistedGoals.clear();
        if (tag.contains("PendingGoals", Tag.TAG_LIST)) {
            ListTag pendingGoals = tag.getList("PendingGoals", Tag.TAG_COMPOUND);
            int start = Math.max(0, pendingGoals.size() - MAX_PERSISTED_GOALS);
            for (int i = start; i < pendingGoals.size(); i++) {
                rememberGoal(AgentGoal.load(pendingGoals.getCompound(i)));
            }
        }

        recentActions.clear();
        if (tag.contains("RecentActions", Tag.TAG_LIST)) {
            ListTag actions = tag.getList("RecentActions", Tag.TAG_STRING);
            int start = Math.max(0, actions.size() - MAX_RECENT_ACTIONS);
            for (int i = start; i < actions.size(); i++) addAction(actions.getString(i));
        }

        episodes.clear();
        if (tag.contains("Episodes", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Episodes", Tag.TAG_COMPOUND);
            int start = Math.max(0, list.size() - MAX_EPISODES);
            for (int i = start; i < list.size(); i++) addEpisode(EpisodicMemoryEntry.load(list.getCompound(i)));
        }

        worldFacts.clear();
        if (tag.contains("WorldFacts", Tag.TAG_LIST)) {
            ListTag list = tag.getList("WorldFacts", Tag.TAG_COMPOUND);
            int start = Math.max(0, list.size() - MAX_WORLD_FACTS);
            for (int i = start; i < list.size(); i++) rememberWorldFact(WorldFact.load(list.getCompound(i)));
        }

        goalHistory.clear();
        if (tag.contains("GoalHistory", Tag.TAG_LIST)) {
            ListTag list = tag.getList("GoalHistory", Tag.TAG_STRING);
            int start = Math.max(0, list.size() - MAX_GOAL_HISTORY);
            for (int i = start; i < list.size(); i++) goalHistory.add(bounded(list.getString(i), 512));
        }
    }

    private static boolean sameFact(WorldFact left, WorldFact right) {
        return left.kind() == right.kind()
            && left.key().equals(right.key())
            && left.dimension().equals(right.dimension())
            && java.util.Objects.equals(left.position(), right.position());
    }

    private static String bounded(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
