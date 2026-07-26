package com.steve.ai.planning;

public enum CommandPriority {
    EMERGENCY(0),    // "Stop now", "Defend me"
    HIGH(1),         // Combat, safety
    NORMAL(2),       // Regular commands
    LOW(3),          // Background tasks
    IDLE(4);         // Default behavior when nothing else
    
    private final int rank;
    
    CommandPriority(int rank) {
        this.rank = rank;
    }
    
    public int getRank() {
        return rank;
    }
}
