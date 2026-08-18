package com.steve.ai.autonomy;

public enum GoalPriority {
    USER_INTERRUPT(0),
    USER(10),
    RECOVERY(20),
    PREREQUISITE(25),
    COLLABORATION(35),
    MAINTENANCE(40),
    AUTONOMOUS(50);

    private final int rank;

    GoalPriority(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
