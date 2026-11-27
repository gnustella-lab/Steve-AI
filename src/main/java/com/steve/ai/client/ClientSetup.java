package com.steve.ai.client;

import com.steve.ai.SteveMod;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-side setup for entity renderers and other client-only initialization
 */
@Mod.EventBusSubscriber(modid = SteveMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    private static final Identifier STEVE_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(SteveGUI::registerOverlay);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SteveMod.STEVE_ENTITY.get(), context ->
            new HumanoidMobRenderer<SteveEntity, PlayerModel<SteveEntity>>(
                context,
                new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false),
                0.5F
            ) {
                @Override
                public Identifier getTextureLocation(SteveEntity entity) {
                    return STEVE_TEXTURE;
                }
            }
        );
    }
}

