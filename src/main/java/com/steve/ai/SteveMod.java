package com.steve.ai;

import com.mojang.logging.LogUtils;
import com.steve.ai.action.CollaborativeBuildManager;
import com.steve.ai.action.ResourceReservationBoard;
import com.steve.ai.command.SteveCommands;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.di.SimpleServiceContainer;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.entity.SteveManager;
import com.steve.ai.llm.async.LLMCache;
import com.steve.ai.llm.resilience.LLMFallbackHandler;
import com.steve.ai.memory.StructureRegistry;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.plugin.PluginManager;
import com.steve.ai.security.PermissionManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(SteveMod.MODID)
public class SteveMod {
    public static final String MODID = "steve";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final RegistryObject<EntityType<SteveEntity>> STEVE_ENTITY = ENTITIES.register("steve",
        () -> EntityType.Builder.of(SteveEntity::new, MobCategory.CREATURE)
            .sized(0.6F, 1.8F)
            .clientTrackingRange(10)
            .build(ENTITIES.key("steve")));

    private static SteveManager steveManager;

    /** Shared LLM cache across all Steves (thread-safe). */
    private static final LLMCache sharedLLMCache = new LLMCache();

    /** Shared fallback handler across all Steves (thread-safe). */
    private static final LLMFallbackHandler sharedFallbackHandler = new LLMFallbackHandler();

    /** Shared dependency container used by plugins and every action context. */
    private static volatile SimpleServiceContainer serviceContainer;

    public SteveMod(FMLJavaModLoadingContext context) {
        BusGroup modBusGroup = context.getModBusGroup();

        ENTITIES.register(modBusGroup);

        context.registerConfig(ModConfig.Type.COMMON, SteveConfig.SPEC);

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);
        EntityAttributeCreationEvent.BUS.addListener(this::entityAttributes);
        RegisterCommandsEvent.BUS.addListener(this::onCommandRegister);
        ServerStoppedEvent.BUS.addListener(this::onServerStopped);

        steveManager = new SteveManager();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SimpleServiceContainer services = new SimpleServiceContainer();
            services.register(LLMCache.class, sharedLLMCache);
            services.register(LLMFallbackHandler.class, sharedFallbackHandler);
            serviceContainer = services;
            PluginManager.getInstance().loadPlugins(ActionRegistry.getInstance(), services);
        });
    }

    private void entityAttributes(EntityAttributeCreationEvent event) {
        event.put(STEVE_ENTITY.get(), SteveEntity.createAttributes().build());
    }

    private void onCommandRegister(RegisterCommandsEvent event) {
        SteveCommands.register(event.getDispatcher());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        // Os mundos já foram descarregados, portanto limpamos somente estado global em memória.
        steveManager.clearTracking();
        StructureRegistry.clear();
        CollaborativeBuildManager.clearAllBuilds();
        ResourceReservationBoard.getInstance().clear();
        PermissionManager.getInstance().clear();
        PluginManager.getInstance().unloadPlugins();
        ActionRegistry.getInstance().clear();
    }

    public static SteveManager getSteveManager() {
        return steveManager;
    }

    /** Returns the shared LLM cache used by all TaskPlanners. */
    public static LLMCache getSharedLLMCache() {
        return sharedLLMCache;
    }

    /** Returns the shared fallback handler used by all TaskPlanners. */
    public static LLMFallbackHandler getSharedFallbackHandler() {
        return sharedFallbackHandler;
    }

    public static SimpleServiceContainer getServiceContainer() {
        return serviceContainer;
    }
}
