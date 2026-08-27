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
    private final Map<UUID, Entry> paused = new HashMap<>();
    private long sequence;
    private AgentGoal active;

    public void enqueue(AgentGoal goal) {
        enqueue(goal, 0L);
    }

    public void enqueue(AgentGoal goal, long now) {
        if (goal == null || !goal.canAutoResume()) return;
        removeQueued(goal.getId());
        if (active != null && active.getId().equals(goal.getId())) return;
        if (!goal.pause(now)) return;
        pending.offer(new Entry(goal, sequence++));
    }

    public AgentGoal pollNext() {
        return pollNext(0L);
    }

    public AgentGoal pollNext(long now) {
        Entry next;
        while ((next = pending.poll()) != null) {
            if (!next.goal.canAutoResume() || !next.goal.activate(now)) continue;
            active = next.goal;
            return active;
        }
        active = null;
        return null;
    }

    /** Restores one persisted active goal ahead of its paused prerequisite/interrupt backlog. */
    public void activate(AgentGoal goal, long now) {
        if (goal == null || !goal.canAutoResume()) return;
        removeQueued(goal.getId());
        active = goal;
        active.activate(now);
    }

    public AgentGoal getActive() { return active; }

    public void pauseActive(long now) {
        if (active == null) return;
        if (active.pause(now)) {
            paused.put(active.getId(), new Entry(active, sequence++));
        }
        active = null;
    }

    /** Pauses active and queued goals so a pause command is effective even between ticks. */
    public void pauseAll(long now) {
        pauseActive(now);
        for (Entry entry : pending) {
            if (entry.goal.canAutoResume() && entry.goal.pause(now)) {
                paused.put(entry.goal.getId(), entry);
            }
        }
        pending.clear();
    }

    /** Requeues every paused goal in its original priority order. */
    public void resumeAll(long now) {
        List<Entry> entries = new ArrayList<>(paused.values());
        entries.sort(ORDER);
        paused.clear();
        entries.forEach(entry -> {
            AgentGoal goal = entry.goal;
            if (goal.canAutoResume() && goal.activate(now)) {
                goal.pause(now);
                pending.offer(new Entry(goal, sequence++));
            }
        });
    }

    public void resume(UUID goalId, long now) {
        if (goalId == null) return;
        Entry pausedEntry = paused.remove(goalId);
        if (pausedEntry != null && pausedEntry.goal.canAutoResume()
                && pausedEntry.goal.activate(now)) {
            pausedEntry.goal.pause(now);
            pending.offer(new Entry(pausedEntry.goal, sequence++));
            return;
        }
        for (Entry entry : pending) {
            if (entry.goal.getId().equals(goalId) && entry.goal.canAutoResume()) {
                entry.goal.activate(now);
                entry.goal.pause(now);
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
        if (goalId == null) return false;
        if (active != null && active.getId().equals(goalId)) {
            active.cancel(now);
            active = null;
            return true;
        }
        Entry pausedEntry = paused.remove(goalId);
        if (pausedEntry != null) {
            pausedEntry.goal.cancel(now);
            return true;
        }
        var iterator = pending.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.goal.getId().equals(goalId)) {
                entry.goal.cancel(now);
                iterator.remove();
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
        List<Entry> entries = new ArrayList<>(paused.values());
        entries.sort(ORDER);
        return Collections.unmodifiableList(entries.stream().map(Entry::goal).toList());
    }

    public AgentGoal find(UUID goalId) {
        if (goalId == null) return null;
        if (active != null && active.getId().equals(goalId)) return active;
        Entry pausedGoal = paused.get(goalId);
        if (pausedGoal != null) return pausedGoal.goal;
        return pending.stream().map(Entry::goal)
            .filter(goal -> goal.getId().equals(goalId)).findFirst().orElse(null);
    }

    /** Absolute stop: no pending or paused goal is eligible for automatic resume. */
    public void cancelAll(long now) {
        if (active != null) active.cancel(now);
        pending.forEach(entry -> entry.goal.cancel(now));
        paused.values().forEach(entry -> entry.goal.cancel(now));
        clear();
    }

    public int size() { return pending.size(); }
    public boolean isEmpty() { return pending.isEmpty() && active == null; }
    public void clear() { pending.clear(); paused.clear(); active = null; }

    private void removeQueued(UUID goalId) {
        pending.removeIf(entry -> entry.goal.getId().equals(goalId));
        paused.remove(goalId);
    }

    private record Entry(AgentGoal goal, long sequence) {
    }
}
