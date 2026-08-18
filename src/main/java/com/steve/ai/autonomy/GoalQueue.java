package com.steve.ai.autonomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

/** Priority queue for goal lifecycles. Paused goals are retained separately until resumed. */
public final class GoalQueue {
    private static final Comparator<Entry> ORDER = Comparator
        .comparingInt((Entry entry) -> entry.goal.getPriority().getRank())
        .thenComparingLong(entry -> entry.sequence);

    private final PriorityQueue<Entry> pending = new PriorityQueue<>(ORDER);
    private final Map<UUID, AgentGoal> paused = new HashMap<>();
    private long sequence;
    private AgentGoal active;

    public void enqueue(AgentGoal goal) {
        if (goal == null || goal.isTerminal()) return;
        goal.pause(0L);
        pending.offer(new Entry(goal, sequence++));
    }

    public AgentGoal pollNext() {
        Entry next;
        while ((next = pending.poll()) != null) {
            if (next.goal.isTerminal()) continue;
            active = next.goal;
            active.activate(0L);
            return active;
        }
        active = null;
        return null;
    }

    /** Restores one persisted active goal ahead of its paused prerequisite/interrupt backlog. */
    public void activate(AgentGoal goal, long now) {
        if (goal == null || goal.isTerminal()) return;
        pending.removeIf(entry -> entry.goal().getId().equals(goal.getId()));
        paused.remove(goal.getId());
        active = goal;
        active.activate(now);
    }

    public AgentGoal getActive() { return active; }

    public void pauseActive(long now) {
        if (active == null) return;
        if (active.pause(now)) {
            paused.put(active.getId(), active);
        }
        active = null;
    }

    public void resume(UUID goalId, long now) {
        AgentGoal goal = paused.remove(goalId);
        if (goal != null && goal.activate(now)) {
            pending.offer(new Entry(goal, sequence++));
            goal.pause(now);
            return;
        }
        for (Entry entry : pending) {
            if (entry.goal().getId().equals(goalId)) {
                entry.goal().activate(now);
                entry.goal().pause(now);
                return;
            }
        }
    }

    public void cancelActive(long now) {
        if (active != null) {
            active.cancel(now);
            active = null;
        }
    }

    public boolean cancel(UUID goalId, long now) {
        if (active != null && active.getId().equals(goalId)) {
            active.cancel(now);
            active = null;
            return true;
        }
        AgentGoal pausedGoal = paused.remove(goalId);
        if (pausedGoal != null) {
            pausedGoal.cancel(now);
            return true;
        }
        for (Entry entry : pending) {
            if (entry.goal.getId().equals(goalId)) {
                entry.goal.cancel(now);
                pending.remove(entry);
                return true;
            }
        }
        return false;
    }

    public List<AgentGoal> getPendingGoals() {
        List<Entry> entries = new ArrayList<>(pending);
        entries.sort(ORDER);
        return entries.stream().map(Entry::goal).toList();
    }

    public List<AgentGoal> getPausedGoals() {
        return Collections.unmodifiableList(new ArrayList<>(paused.values()));
    }

    public AgentGoal find(UUID goalId) {
        if (goalId == null) return null;
        if (active != null && active.getId().equals(goalId)) return active;
        AgentGoal pausedGoal = paused.get(goalId);
        if (pausedGoal != null) return pausedGoal;
        return pending.stream().map(Entry::goal)
            .filter(goal -> goal.getId().equals(goalId)).findFirst().orElse(null);
    }

    /** Absolute stop: no pending or paused goal is eligible for automatic resume. */
    public void cancelAll(long now) {
        if (active != null) active.cancel(now);
        pending.forEach(entry -> entry.goal.cancel(now));
        paused.values().forEach(goal -> goal.cancel(now));
        clear();
    }

    public int size() { return pending.size(); }
    public boolean isEmpty() { return pending.isEmpty() && active == null; }
    public void clear() { pending.clear(); paused.clear(); active = null; }

    private record Entry(AgentGoal goal, long sequence) {
    }
}
