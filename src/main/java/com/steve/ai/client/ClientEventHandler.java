package com.steve.ai.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Handles client-side key presses and GUI animation. */
@Mod.EventBusSubscriber(modid = "steve", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onChatMessage(ClientChatReceivedEvent event) {
        String message = event.getMessage().getString();
        int nameEnd = message.indexOf("> ");
        if (!message.startsWith("<") || nameEnd <= 1) {
            return;
        }

        String sender = message.substring(1, nameEnd);
        if (SteveGUI.isKnownSteve(sender)) {
            SteveGUI.addSteveMessage(sender, message.substring(nameEnd + 2));
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        SteveGUI.tick();

        while (KeyBindings.TOGGLE_GUI != null && KeyBindings.TOGGLE_GUI.consumeClick()) {
            SteveGUI.toggle();
        }
    }
}
