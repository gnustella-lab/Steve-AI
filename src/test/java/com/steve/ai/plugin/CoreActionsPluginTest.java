package com.steve.ai.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreActionsPluginTest {
    private static final Set<String> CORE_ACTIONS = Set.of(
        "pathfind", "mine", "gather", "search_resource", "place", "build", "attack", "follow",
        "pickup_item", "give_item", "deposit_item", "withdraw_item",
        "equip_item", "unequip_item", "drop_item", "consume_item",
        "inspect_inventory", "craft", "smelt");

    @BeforeEach
    void clearRegistry() {
        ActionRegistry.getInstance().clear();
    }

    @AfterEach
    void cleanUpRegistry() {
        ActionRegistry.getInstance().clear();
    }

    @Test
    void registersEveryCoreFactoryWithItsDescriptorAndSchema() {
        CoreActionsPlugin plugin = new CoreActionsPlugin();
        plugin.onLoad(ActionRegistry.getInstance(), null);

        assertEquals(CORE_ACTIONS, Set.copyOf(ActionRegistry.getInstance().getRegisteredActions()));
        assertEquals(Integer.MAX_VALUE, plugin.getPriority());
        for (String action : CORE_ACTIONS) {
            ActionDescriptor descriptor = ActionRegistry.getInstance().getDescriptor(action).orElseThrow();
            assertEquals(action, descriptor.name());
            assertTrue(descriptor.parameterSchema().toJson().contains("\"properties\""));
        }
    }
}
