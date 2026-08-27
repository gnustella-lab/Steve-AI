package com.steve.ai.autonomy;

public enum GoalPriority {
    USER_INTERRUPT(0),
    RECOVERY(10),
    PREREQUISITE(15),
    USER(20),
    COLLABORATION(30),
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
