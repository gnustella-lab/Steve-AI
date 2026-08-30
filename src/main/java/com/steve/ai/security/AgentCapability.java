package com.steve.ai.security;

/**
 * Server-owned capabilities for operations that intentionally exceed ordinary survival movement.
 *
 * <p>Capabilities are never task parameters and therefore cannot be granted by an LLM plan.</p>
 */
public enum AgentCapability {
    ALLOW_TELEPORT,
    ALLOW_FLIGHT,
    ALLOW_CREATIVE_BUILD
}
