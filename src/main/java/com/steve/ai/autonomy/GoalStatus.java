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

    /**
     * Whether a queue may resume this state without an explicit new command.
     * BLOCKED is intentionally not terminal so diagnostics and a later explicit
     * recovery can retain it, but it is never eligible for automatic resume.
     */
    public boolean isAutoResumable() {
        return this == PENDING || this == ACTIVE || this == PAUSED;
    }

    public boolean isNonResumable() {
        return !isAutoResumable();
    }
}
