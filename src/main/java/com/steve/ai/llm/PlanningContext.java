package com.steve.ai.llm;

import com.steve.ai.autonomy.AgentGoal;
import com.steve.ai.perception.ObservationSnapshot;

import java.util.List;

/** Immutable, bounded input to one autonomous planning decision. */
public final class PlanningContext {
    private final AgentGoal primaryGoal;
    private final AgentGoal activeSubgoal;
    private final ObservationSnapshot observation;
    private final List<String> relevantMemory;
    private final List<String> recentCompletedSteps;
    private final String lastActionResult;
    private final List<String> failedApproaches;
    private final int maxPlanHorizon;
    private final int remainingLlmCalls;
    private final int remainingReplans;

    public PlanningContext(AgentGoal primaryGoal, AgentGoal activeSubgoal,
            ObservationSnapshot observation, List<String> relevantMemory,
            List<String> recentCompletedSteps, String lastActionResult,
            List<String> failedApproaches, int maxPlanHorizon,
            int remainingLlmCalls, int remainingReplans) {
        this.primaryGoal = primaryGoal;
        this.activeSubgoal = activeSubgoal;
        this.observation = observation;
        this.relevantMemory = boundedList(relevantMemory, 12);
        this.recentCompletedSteps = boundedList(recentCompletedSteps, 12);
        this.lastActionResult = bounded(lastActionResult, 512);
        this.failedApproaches = boundedList(failedApproaches, 12);
        this.maxPlanHorizon = Math.max(1, Math.min(maxPlanHorizon, 16));
        this.remainingLlmCalls = Math.max(0, remainingLlmCalls);
        this.remainingReplans = Math.max(0, remainingReplans);
    }

    public AgentGoal getPrimaryGoal() { return primaryGoal; }
    public AgentGoal getActiveSubgoal() { return activeSubgoal; }
    public ObservationSnapshot getObservation() { return observation; }
    public List<String> getRelevantMemory() { return relevantMemory; }
    public List<String> getRecentCompletedSteps() { return recentCompletedSteps; }
    public String getLastActionResult() { return lastActionResult; }
    public List<String> getFailedApproaches() { return failedApproaches; }
    public int getMaxPlanHorizon() { return maxPlanHorizon; }
    public int getRemainingLlmCalls() { return remainingLlmCalls; }
    public int getRemainingReplans() { return remainingReplans; }

    private static List<String> boundedList(List<String> values, int max) {
        if (values == null) return List.of();
        return values.stream().filter(java.util.Objects::nonNull).map(value -> bounded(value, 512))
            .limit(max).toList();
    }

    private static String bounded(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
