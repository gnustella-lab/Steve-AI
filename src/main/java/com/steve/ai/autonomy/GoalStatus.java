package com.steve.ai.autonomy;

public enum GoalStatus {
    PENDING,
    ACTIVE,
    PAUSED,
    BLOCKED,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
