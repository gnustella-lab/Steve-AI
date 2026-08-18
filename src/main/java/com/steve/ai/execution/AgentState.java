package com.steve.ai.execution;

/** Explicit phases of the persistent observe, plan, act, evaluate loop. */
public enum AgentState {
    IDLE("Idle", "Waiting for a goal"),
    OBSERVING("Observing", "Refreshing the bounded world snapshot"),
    PLANNING("Planning", "Generating the next bounded horizon"),
    EXECUTING("Executing", "Running one tick-based action"),
    EVALUATING("Evaluating", "Checking action progress and goal conditions"),
    RECOVERING("Recovering", "Applying deterministic recovery or replanning"),
    PAUSED("Paused", "Execution is paused"),
    BLOCKED("Blocked", "No safe bounded strategy remains"),
    COMPLETED("Completed", "The current goal was verified"),
    FAILED("Failed", "The current goal failed");

    private final String displayName;
    private final String description;

    AgentState(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public boolean canAcceptCommands() {
        return this == IDLE || this == PAUSED || this == BLOCKED || this == COMPLETED || this == FAILED;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == BLOCKED;
    }

    public boolean isActive() {
        return this == OBSERVING || this == PLANNING || this == EXECUTING
            || this == EVALUATING || this == RECOVERING;
    }
}
