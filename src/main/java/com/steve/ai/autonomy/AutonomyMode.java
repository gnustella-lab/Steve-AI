package com.steve.ai.autonomy;

public enum AutonomyMode {
    OFF,
    GOAL_DRIVEN,
    PROACTIVE;

    public static AutonomyMode parse(String value) {
        AutonomyMode parsed = tryParse(value);
        return parsed == null ? GOAL_DRIVEN : parsed;
    }

    /**
     * Strict parse used for persisted per-agent overrides. Unknown, blank, or
     * malformed values return {@code null} so they cannot silently enable autonomy.
     */
    public static AutonomyMode tryParse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
