package com.steve.ai.event;

import com.steve.ai.SteveMod;
import com.steve.ai.entity.SteveEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SteveMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof SteveEntity steve) {
            SteveMod.getSteveManager().registerSteve(steve);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof SteveEntity steve) {
            SteveMod.getSteveManager().unregisterSteve(steve);
        }
    }
}

