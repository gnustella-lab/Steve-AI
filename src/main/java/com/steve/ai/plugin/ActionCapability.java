package com.steve.ai.plugin;

/** Capabilities advertised by an action for planning, security and diagnostics. */
public enum ActionCapability {
    MOVEMENT,
    WORLD_READ,
    WORLD_WRITE,
    INVENTORY_READ,
    INVENTORY_WRITE,
    CRAFTING,
    COMBAT
}
