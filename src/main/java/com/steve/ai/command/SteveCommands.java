package com.steve.ai.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.steve.ai.SteveMod;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.entity.SteveManager;
import com.steve.ai.plugin.PluginManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class SteveCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("steve")
            .then(Commands.literal("spawn")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(SteveCommands::spawnSteve)))
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(SteveCommands::removeSteve)))
            .then(Commands.literal("list")
                .executes(SteveCommands::listSteves))
            .then(Commands.literal("stop")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(SteveCommands::stopSteve)))
            .then(Commands.literal("tell")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("command", StringArgumentType.greedyString())
                        .executes(SteveCommands::tellSteve))))
            .then(Commands.literal("status")
                .executes(SteveCommands::showStatus))
        );
    }

    /**
     * /steve status - Displays system diagnostics.
     */
    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // Provider
        String provider = SteveConfig.AI_PROVIDER.get();
        source.sendSuccess(() -> Component.literal("§6=== Steve AI Status ==="), false);

        // LLM provider config
        source.sendSuccess(() -> Component.literal(
            "§eProvider: §f" + provider + "  §emaxTokens: §f" + SteveConfig.MAX_TOKENS.get() +
            "  §etemperature: §f" + SteveConfig.TEMPERATURE.get()), false);

        // API key status per provider
        boolean openaiKey = !SteveConfig.getOpenAIApiKey().isBlank();
        boolean groqKey = !SteveConfig.getGroqApiKey().isBlank();
        boolean geminiKey = !SteveConfig.getGeminiApiKey().isBlank();
        source.sendSuccess(() -> Component.literal(
            "§eKeys: §fopenai=" + (openaiKey ? "§aconfigured" : "§cmissing") +
            "§f  groq=" + (groqKey ? "§aconfigured" : "§cmissing") +
            "§f  gemini=" + (geminiKey ? "§aconfigured" : "§cmissing")), false);

        // Provider health check via shared LLM cache
        var cache = SteveMod.getSharedLLMCache();
        if (cache != null) {
            var stats = cache.getStats();
            source.sendSuccess(() -> Component.literal(
                "§eCache: §f" + cache.size() + " entries, " +
                String.format("%.0f%% hit rate", stats.hitRate() * 100) +
                " (" + stats.hitCount() + " hits / " + stats.missCount() + " misses)"), false);
        }

        // Active Steves
        SteveManager manager = SteveMod.getSteveManager();
        source.sendSuccess(() -> Component.literal(
            "§eActive Steves: §f" + manager.getActiveCount() + " / " + SteveConfig.MAX_ACTIVE_STEVES.get()), false);

        // Plugin info
        PluginManager pluginManager = PluginManager.getInstance();
        if (pluginManager.isInitialized()) {
            source.sendSuccess(() -> Component.literal(
                "§ePlugins: §f" + pluginManager.getPluginCount() + " loaded (" +
                String.join(", ", pluginManager.getLoadedPluginIds()) + ")"), false);
        }

        return 1;
    }

    private static int spawnSteve(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        
        ServerLevel serverLevel = source.getLevel();
        if (serverLevel == null) {
            source.sendFailure(Component.literal("Command must be run on server"));
            return 0;
        }

        SteveManager manager = SteveMod.getSteveManager();
        
        Vec3 sourcePos = source.getPosition();
        if (source.getEntity() != null) {
            Vec3 lookVec = source.getEntity().getLookAngle();
            sourcePos = sourcePos.add(lookVec.x * 3, 0, lookVec.z * 3);
        } else {
            sourcePos = sourcePos.add(3, 0, 0);
        }
        Vec3 spawnPos = sourcePos;
        
        SteveEntity steve = manager.spawnSteve(serverLevel, spawnPos, name);
        if (steve != null) {
            source.sendSuccess(() -> Component.literal("Spawned Steve: " + name), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to spawn Steve. Name may already exist or max limit reached."));
            return 0;
        }
    }

    private static int removeSteve(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        
        SteveManager manager = SteveMod.getSteveManager();
        if (manager.removeSteve(name)) {
            source.sendSuccess(() -> Component.literal("Removed Steve: " + name), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Steve not found: " + name));
            return 0;
        }
    }

    private static int listSteves(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        SteveManager manager = SteveMod.getSteveManager();
        
        var names = manager.getSteveNames();
        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active Steves"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Active Steves (" + names.size() + "): " + String.join(", ", names)), false);
        }
        return 1;
    }

    private static int stopSteve(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        
        SteveManager manager = SteveMod.getSteveManager();
        SteveEntity steve = manager.getSteve(name);
        
        if (steve != null) {
            steve.getActionExecutor().stopCurrentAction();
            steve.getMemory().clearTaskQueue();
            source.sendSuccess(() -> Component.literal("Stopped Steve: " + name), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Steve not found: " + name));
            return 0;
        }
    }

    private static int tellSteve(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        String command = StringArgumentType.getString(context, "command");
        CommandSourceStack source = context.getSource();
        
        SteveManager manager = SteveMod.getSteveManager();
        SteveEntity steve = manager.getSteve(name);
        
        if (steve != null) {
            // Disabled command feedback message
            // source.sendSuccess(() -> Component.literal("Instructing " + name + ": " + command), true);
            
            new Thread(() -> {
                steve.getActionExecutor().processNaturalLanguageCommand(command);
            }).start();
            
            return 1;
        } else {
            source.sendFailure(Component.literal("Steve not found: " + name));
            return 0;
        }
    }
}

