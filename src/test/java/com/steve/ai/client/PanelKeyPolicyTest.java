package com.steve.ai.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PanelKeyPolicyTest {
    @Test void reboundKeyNeverClosesWhileTyping() {
        assertFalse(PanelKeyPolicy.shouldClose(false, true, true, 0));
        assertFalse(PanelKeyPolicy.shouldClose(false, false, true, 0));
    }
    @Test void escapeAlwaysClosesAndOnlyConfiguredKeyClosesWhenUnfocused() {
        assertTrue(PanelKeyPolicy.shouldClose(true, false, true, 0));
        assertTrue(PanelKeyPolicy.shouldClose(false, true, false, 0));
        assertFalse(PanelKeyPolicy.shouldClose(false, false, false, 0));
    }
    @Test void clipboardModifiersNeverTriggerToggle() {
        assertFalse(PanelKeyPolicy.shouldClose(false, true, true, 2));
        assertFalse(PanelKeyPolicy.shouldClose(false, true, false, 2));
    }
    @Test void queuedBindingDoesNotOpenOverChatOrOtherScreens() {
        assertTrue(PanelKeyPolicy.canOpen(true, false));
        assertFalse(PanelKeyPolicy.canOpen(true, true));
        assertFalse(PanelKeyPolicy.canOpen(false, false));
    }
}
