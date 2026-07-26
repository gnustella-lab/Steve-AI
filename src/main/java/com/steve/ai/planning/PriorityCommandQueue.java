package com.steve.ai.planning;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;

public final class PriorityCommandQueue {
    
    private static class QueuedPlan implements Comparable<QueuedPlan> {
        final Plan plan;
        final CommandPriority priority;
        final long enqueueTime;
        
        QueuedPlan(Plan plan, CommandPriority priority, long enqueueTime) {
            this.plan = plan;
            this.priority = priority;
            this.enqueueTime = enqueueTime;
        }

        @Override
        public int compareTo(QueuedPlan other) {
            int rankDiff = Integer.compare(this.priority.getRank(), other.priority.getRank());
            if (rankDiff != 0) {
                return rankDiff;
            }
            return Long.compare(this.enqueueTime, other.enqueueTime);
        }
    }

    private final PriorityQueue<QueuedPlan> queue;
    private Plan currentPlan;
    private long counter;

    public PriorityCommandQueue() {
        this.queue = new PriorityQueue<>();
        this.counter = 0;
    }

    public void enqueue(Plan plan, CommandPriority priority) {
        if (priority == CommandPriority.EMERGENCY) {
            if (currentPlan != null) {
                if (currentPlan.getState() == Plan.State.EXECUTING) {
                    currentPlan.setState(Plan.State.PAUSED);
                }
                queue.add(new QueuedPlan(currentPlan, CommandPriority.NORMAL, counter++));
            }
        }
        queue.add(new QueuedPlan(plan, priority, counter++));
    }

    public Plan dequeue() {
        QueuedPlan qp = queue.poll();
        if (qp != null) {
            currentPlan = qp.plan;
            if (currentPlan.getState() == Plan.State.PAUSED) {
                currentPlan.setState(Plan.State.EXECUTING);
            }
            return currentPlan;
        }
        currentPlan = null;
        return null;
    }

    public boolean pause(UUID planId) {
        if (currentPlan != null && currentPlan.getPlanId().equals(planId)) {
            currentPlan.setState(Plan.State.PAUSED);
            return true;
        }
        for (QueuedPlan qp : queue) {
            if (qp.plan.getPlanId().equals(planId)) {
                qp.plan.setState(Plan.State.PAUSED);
                return true;
            }
        }
        return false;
    }

    public boolean resume(UUID planId) {
        if (currentPlan != null && currentPlan.getPlanId().equals(planId)) {
            currentPlan.setState(Plan.State.EXECUTING);
            return true;
        }
        for (QueuedPlan qp : queue) {
            if (qp.plan.getPlanId().equals(planId)) {
                qp.plan.setState(Plan.State.PLANNING);
                return true;
            }
        }
        return false;
    }

    public boolean cancel(UUID planId) {
        if (currentPlan != null && currentPlan.getPlanId().equals(planId)) {
            currentPlan.setState(Plan.State.CANCELLED);
            currentPlan = null;
            return true;
        }
        Iterator<QueuedPlan> it = queue.iterator();
        while (it.hasNext()) {
            QueuedPlan qp = it.next();
            if (qp.plan.getPlanId().equals(planId)) {
                qp.plan.setState(Plan.State.CANCELLED);
                it.remove();
                return true;
            }
        }
        return false;
    }

    public boolean cancelCurrent() {
        if (currentPlan != null) {
            currentPlan.setState(Plan.State.CANCELLED);
            currentPlan = null;
            return true;
        }
        return false;
    }

    public Plan getCurrentPlan() {
        return currentPlan;
    }

    public List<Plan> getQueuedPlans() {
        List<Plan> list = new ArrayList<>();
        PriorityQueue<QueuedPlan> temp = new PriorityQueue<>(queue);
        while (!temp.isEmpty()) {
            list.add(temp.poll().plan);
        }
        return list;
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
        currentPlan = null;
    }
}
