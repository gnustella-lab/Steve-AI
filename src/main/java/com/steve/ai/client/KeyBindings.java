package com.steve.ai.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.steve.ai.SteveMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SteveMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeyBindings {

    public static final String KEY_CATEGORY = "key.categories.steve";

    public static KeyMapping TOGGLE_GUI;

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(SteveMod.MODID, "panel"));
        TOGGLE_GUI = new KeyMapping(
            "key.steve.toggle_gui",
            InputConstants.KEY_GRAVE, // SDL scancode of the grave/backtick key
            category
        );

        event.register(TOGGLE_GUI);
    }
}
