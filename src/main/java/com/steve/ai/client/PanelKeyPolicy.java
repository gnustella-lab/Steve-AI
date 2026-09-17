package com.steve.ai.client;

/** Input precedence independent of Minecraft/GLFW, so typing is regression-testable. */
final class PanelKeyPolicy {
    private PanelKeyPolicy() {}

    static boolean shouldClose(boolean escape, boolean bindingMatches, boolean editing, int modifiers) {
        return escape || (bindingMatches && !editing && modifiers == 0);
    }

    static boolean canOpen(boolean inWorld, boolean screenOpen) {
        return inWorld && !screenOpen;
    }
}
