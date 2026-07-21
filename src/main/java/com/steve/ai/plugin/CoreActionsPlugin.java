package com.steve.ai.plugin;

import com.steve.ai.action.actions.*;
import com.steve.ai.di.ServiceContainer;
import com.steve.ai.security.ActionPermission;
import com.steve.ai.plugin.JsonSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Core plugin that registers all built-in Steve AI actions.
 *
 * <p>This plugin is loaded first (priority 1000) and provides the fundamental
 * actions that Steve can perform: mining, building, combat, pathfinding, etc.</p>
 *
 * <p><b>Registered Actions:</b></p>
 * <ul>
 *   <li><b>pathfind</b>: Navigate to coordinates (x, y, z)</li>
 *   <li><b>mine</b>: Mine blocks (block type, quantity)</li>
 *   <li><b>place</b>: Place blocks at coordinates</li>
 *   <li><b>attack</b>: Attack entities (target)</li>
 *   <li><b>follow</b>: Follow a player</li>
 *   <li><b>gather</b>: Gather resources (resource, quantity)</li>
 *   <li><b>build</b>: Build structures (structure type, blocks, dimensions)</li>
 * </ul>
 *
 * @since 1.1.0
 * @see ActionPlugin
 */
public class CoreActionsPlugin implements ActionPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreActionsPlugin.class);

    private static final String PLUGIN_ID = "core-actions";
    private static final String VERSION = "1.5.1";

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void onLoad(ActionRegistry registry, ServiceContainer container) {
        LOGGER.info("Loading CoreActionsPlugin v{}", VERSION);

        int priority = getPriority();

        registry.register(descriptor(
                "pathfind",
                "Navigate to server coordinates",
                ActionPermission.MOVEMENT,
                JsonSchema.object()
                    .requiredInteger("x", -30_000_000, 30_000_000)
                    .requiredInteger("y", -2_048, 2_048)
                    .requiredInteger("z", -30_000_000, 30_000_000)
                    .build(),
                "{\"action\":\"pathfind\",\"parameters\":{\"x\":10,\"y\":64,\"z\":-4}}",
                ActionCapability.MOVEMENT),
            (steve, task, ctx) -> new PathfindAction(steve, task), priority);

        registry.register(descriptor(
                "mine",
                "Find and mine a bounded quantity of a block or ore",
                ActionPermission.GATHERING,
                resourceSchema("block"),
                "{\"action\":\"mine\",\"parameters\":{\"block\":\"iron\",\"quantity\":8}}",
                ActionCapability.MOVEMENT, ActionCapability.WORLD_READ,
                ActionCapability.WORLD_WRITE, ActionCapability.INVENTORY_WRITE),
            (steve, task, ctx) -> new MineBlockAction(steve, task), priority);

        registry.register(descriptor(
                "gather",
                "Gather a bounded quantity of a resource",
                ActionPermission.GATHERING,
                resourceSchema("resource"),
                "{\"action\":\"gather\",\"parameters\":{\"resource\":\"oak_log\",\"quantity\":16}}",
                ActionCapability.MOVEMENT, ActionCapability.WORLD_READ,
                ActionCapability.WORLD_WRITE, ActionCapability.INVENTORY_WRITE),
            (steve, task, ctx) -> new GatherResourceAction(steve, task), priority);

        registry.register(descriptor(
                "place",
                "Place one block at server coordinates",
                ActionPermission.BUILDING,
                JsonSchema.object()
                    .requiredString("block", 1, 128)
                    .requiredInteger("x", -30_000_000, 30_000_000)
                    .requiredInteger("y", -2_048, 2_048)
                    .requiredInteger("z", -30_000_000, 30_000_000)
                    .build(),
                "{\"action\":\"place\",\"parameters\":{\"block\":\"oak_planks\","
                    + "\"x\":10,\"y\":64,\"z\":-4}}",
                ActionCapability.WORLD_WRITE, ActionCapability.INVENTORY_WRITE),
            (steve, task, ctx) -> new PlaceBlockAction(steve, task), priority);

        registry.register(descriptor(
                "build",
                "Build a bounded structure from a palette and dimensions",
                ActionPermission.BUILDING,
                buildSchema(),
                "{\"action\":\"build\",\"parameters\":{\"structure\":\"house\","
                    + "\"blocks\":[\"oak_planks\",\"cobblestone\"],\"dimensions\":[9,6,9]}}",
                ActionCapability.MOVEMENT, ActionCapability.WORLD_READ,
                ActionCapability.WORLD_WRITE, ActionCapability.INVENTORY_WRITE),
            (steve, task, ctx) -> new BuildStructureAction(steve, task), priority);

        registry.register(descriptor(
                "attack",
                "Attack a nearby entity matching the target selector",
                ActionPermission.COMBAT,
                JsonSchema.object().requiredString("target", 1, 128).build(),
                "{\"action\":\"attack\",\"parameters\":{\"target\":\"hostile\"}}",
                ActionCapability.MOVEMENT, ActionCapability.WORLD_READ, ActionCapability.COMBAT),
            (steve, task, ctx) -> new CombatAction(steve, task), priority);

         registry.register(descriptor(
                 "follow",
                 "Follow a nearby player by name",
                 ActionPermission.MOVEMENT,
                 JsonSchema.object().requiredString("player", 1, 64).build(),
                 "{\"action\":\"follow\",\"parameters\":{\"player\":\"PlayerName\"}}",
                 ActionCapability.MOVEMENT, ActionCapability.WORLD_READ),
             (steve, task, ctx) -> new FollowPlayerAction(steve, task), priority);

        // ── Inventory actions ────────────────────────────────────────

        registry.register(descriptor(
                "pickup_item",
                "Pick up item entities from the ground",
                ActionPermission.GATHERING,
                JsonSchema.object()
                    .requiredString("item", 1, 128)
                    .requiredInteger("quantity", 1, 256)
                    .build(),
                "{\"action\":\"pickup_item\",\"parameters\":{\"item\":\"oak_log\",\"quantity\":16}}",
                ActionCapability.MOVEMENT, ActionCapability.WORLD_READ, ActionCapability.INVENTORY_WRITE),
            (steve, task, ctx) -> new PickupItemAction(steve, task), priority);

        registry.register(descriptor(
                "give_item",
                "Give items from inventory to a player",
                ActionPermission.INVENTORY,
                JsonSchema.object()
                    .requiredString("item", 1, 128)
                    .requiredInteger("quantity", 1, 256)
                    .optionalString("player", 1, 64)
                    .build(),
                "{\"action\":\"give_item\",\"parameters\":{\"item\":\"bread\",\"quantity\":4,\"player\":\"owner\"}}",
                ActionCapability.MOVEMENT, ActionCapability.INVENTORY_WRITE),
            (steve, task, ctx) -> new GiveItemAction(steve, task), priority);

        registry.register(descriptor(
                "deposit_item",
                "Deposit items into a nearby container",
                ActionPermission.INVENTORY,
                JsonSchema.object()
                    .requiredString("item", 1, 128)
                    .requiredInteger("quantity", 1, 256)
                    .build(),
                "{\"action\":\"deposit_item\",\"parameters\":{\"item\":\"cobblestone\",\"quantity\":64}}",
                ActionCapability.MOVEMENT, ActionCapability.INVENTORY_WRITE, ActionCapability.WORLD_READ),
            (steve, task, ctx) -> new DepositItemAction(steve, task), priority);

        registry.register(descriptor(
                "withdraw_item",
                "Withdraw items from a nearby container",
                ActionPermission.INVENTORY,
                JsonSchema.object()
                    .requiredString("item", 1, 128)
                    .requiredInteger("quantity", 1, 256)
                    .build(),
                "{\"action\":\"withdraw_item\",\"parameters\":{\"item\":\"iron_ingot\",\"quantity\":8}}",
                ActionCapability.MOVEMENT, ActionCapability.INVENTORY_READ, ActionCapability.WORLD_READ),
            (steve, task, ctx) -> new WithdrawItemAction(steve, task), priority);

        registry.register(descriptor(
                "equip_item",
                "Equip an item from inventory into an equipment slot",
                ActionPermission.INVENTORY,
                JsonSchema.object()
                    .requiredString("item", 1, 128)
                    .requiredString("slot", 1, 32)
                    .build(),
                "{\"action\":\"equip_item\",\"parameters\":{\"item\":\"iron_pickaxe\",\"slot\":\"main_hand\"}}",
                ActionCapability.INVENTORY_WRITE),
            (steve, task, ctx) -> new EquipItemAction(steve, task), priority);

        registry.register(descriptor(
                "unequip_item",
                "Unequip an item from an equipment slot into inventory",
                ActionPermission.INVENTORY,
                JsonSchema.object()
                    .requiredString("slot", 1, 32)
                    .build(),
                "{\"action\":\"unequip_item\",\"parameters\":{\"slot\":\"main_hand\"}}",
                ActionCapability.INVENTORY_WRITE),
            (steve, task, ctx) -> new UnequipItemAction(steve, task), priority);

        registry.register(descriptor(
                "drop_item",
                "Drop items from inventory at current position",
                ActionPermission.INVENTORY,
                JsonSchema.object()
                    .requiredString("item", 1, 128)
                    .requiredInteger("quantity", 1, 256)
                    .build(),
                "{\"action\":\"drop_item\",\"parameters\":{\"item\":\"cobblestone\",\"quantity\":16}}",
                ActionCapability.INVENTORY_WRITE),
            (steve, task, ctx) -> new DropItemAction(steve, task), priority);

        registry.register(descriptor(
                "consume_item",
                "Consume one item from inventory (e.g. food)",
                ActionPermission.INVENTORY,
                JsonSchema.object()
                    .requiredString("item", 1, 128)
                    .build(),
                "{\"action\":\"consume_item\",\"parameters\":{\"item\":\"bread\"}}",
                ActionCapability.INVENTORY_WRITE),
            (steve, task, ctx) -> new ConsumeItemAction(steve, task), priority);

        registry.register(descriptor(
                "inspect_inventory",
                "Inspect the Steve inventory and report contents",
                ActionPermission.INTERACTION,
                JsonSchema.object().build(),
                "{\"action\":\"inspect_inventory\",\"parameters\":{}}",
                ActionCapability.INVENTORY_READ),
            (steve, task, ctx) -> new InspectInventoryAction(steve, task), priority);

        // ── Crafting actions ─────────────────────────────────────────

        registry.register(descriptor(
                "craft",
                "Craft an item using available ingredients and a crafting table",
                ActionPermission.CRAFTING,
                JsonSchema.object()
                    .requiredString("item", 1, 128)
                    .requiredInteger("quantity", 1, 64)
                    .build(),
                "{\"action\":\"craft\",\"parameters\":{\"item\":\"iron_pickaxe\",\"quantity\":1}}",
                ActionCapability.MOVEMENT, ActionCapability.INVENTORY_READ,
                ActionCapability.INVENTORY_WRITE, ActionCapability.CRAFTING),
            (steve, task, ctx) -> new CraftItemAction(steve, task), priority);

        registry.register(descriptor(
                "smelt",
                "Smelt items in a furnace",
                ActionPermission.CRAFTING,
                JsonSchema.object()
                    .requiredString("item", 1, 128)
                    .requiredInteger("quantity", 1, 64)
                    .build(),
                "{\"action\":\"smelt\",\"parameters\":{\"item\":\"iron_ingot\",\"quantity\":8}}",
                ActionCapability.MOVEMENT, ActionCapability.INVENTORY_READ,
                ActionCapability.INVENTORY_WRITE, ActionCapability.WORLD_WRITE),
            (steve, task, ctx) -> new SmeltItemAction(steve, task), priority);

        LOGGER.info("CoreActionsPlugin loaded {} actions", registry.getActionCount());
    }

    private static ActionDescriptor descriptor(String name, String description, ActionPermission permission,
            JsonSchema schema, String example, ActionCapability... capabilities) {
        return new ActionDescriptor(
            name,
            description,
            PLUGIN_ID,
            "1",
            permission,
            schema,
            List.of(example),
            Set.of(capabilities));
    }

    private static JsonSchema resourceSchema(String resourceParameter) {
        return JsonSchema.object()
            .requiredString(resourceParameter, 1, 128)
            .requiredInteger("quantity", 1, 2_048)
            .build();
    }

    private static JsonSchema buildSchema() {
        return JsonSchema.object()
            .requiredString("structure", 1, 128)
            .optionalStringArray("blocks", 1, 16, 1, 128)
            .optionalString("material", 1, 128)
            .optionalIntegerArray("dimensions", 3, 3, 1, 64)
            .optionalInteger("width", 1, 64)
            .optionalInteger("height", 1, 64)
            .optionalInteger("depth", 1, 64)
            .constraint("build volume must not exceed 65536", values -> {
                Object dimensions = values.get("dimensions");
                if (dimensions instanceof List<?> list && list.size() == 3) {
                    return ((Number) list.get(0)).longValue()
                        * ((Number) list.get(1)).longValue()
                        * ((Number) list.get(2)).longValue() <= 65_536;
                }
                long width = ((Number) values.getOrDefault("width", 9)).longValue();
                long height = ((Number) values.getOrDefault("height", 6)).longValue();
                long depth = ((Number) values.getOrDefault("depth", 9)).longValue();
                return width * height * depth <= 65_536;
            })
            .build();
    }

    @Override
    public void onUnload() {
        LOGGER.info("CoreActionsPlugin unloading");
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public String[] getDependencies() {
        return new String[0]; // No dependencies - this is the base plugin
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Core Steve AI actions: mining, building, combat, pathfinding, and more";
    }
}
