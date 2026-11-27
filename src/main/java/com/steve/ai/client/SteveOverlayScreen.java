package com.steve.ai.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Invisible overlay screen that captures input for the Steve GUI.
 * This prevents game controls from activating while typing.
 */
public class SteveOverlayScreen extends Screen {

    public SteveOverlayScreen() {
        super(Component.literal("Steve AI"));
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause the game
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Don't render anything - the SteveGUI renders via the HUD overlay layer.
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return SteveGUI.handleKeyPress(event.key(), event.keycode(), event.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        // SDL3 input requires the character codepoint.
        return SteveGUI.handleCharTyped((char) event.codepoint(), 0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        SteveGUI.handleMouseClick(event.x(), event.y(), event.button());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        SteveGUI.handleMouseScroll(scrollY);
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        // Required by the SDL3 text-input backend so typing works in this screen.
        Minecraft.getInstance().onTextInputFocusChange(this, focused);
    }

    @Override
    public void removed() {
        // Clean up when screen is closed
        if (SteveGUI.isOpen()) {
            SteveGUI.toggle();
        }
    }
}
