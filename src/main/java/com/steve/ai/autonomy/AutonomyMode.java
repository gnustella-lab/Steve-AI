package com.steve.ai.autonomy;

public enum AutonomyMode {
    OFF,
    GOAL_DRIVEN,
    PROACTIVE;

    public static AutonomyMode parse(String value) {
        if (value == null) {
            return GOAL_DRIVEN;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GOAL_DRIVEN;
        }
    }
}
