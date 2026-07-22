package com.steve.ai.plugin;

import com.steve.ai.action.Task;
import com.steve.ai.security.ActionPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionSchemaTest {

    @AfterEach
    void clearRegistry() {
        ActionRegistry.getInstance().clear();
    }

    @Test
    void validatesTypesBoundsRequiredFieldsAndUnknownParameters() {
        JsonSchema schema = JsonSchema.object()
            .requiredString("block", 1, 128)
            .requiredInteger("quantity", 1, 64)
            .build();

        assertTrue(schema.validate(Map.of("block", "minecraft:iron_ore", "quantity", 8)).isValid());
        assertFalse(schema.validate(Map.of("quantity", 8)).isValid());
        assertFalse(schema.validate(Map.of("block", "iron_ore", "quantity", 1.5)).isValid());
        assertFalse(schema.validate(Map.of("block", "iron_ore", "quantity", 65)).isValid());
        assertFalse(schema.validate(Map.of(
            "block", "iron_ore", "quantity", 8, "command", "/op Steve")).isValid());
    }

    @Test
    void validatesBoundedArraysAndCrossFieldConstraints() {
        JsonSchema schema = JsonSchema.object()
            .requiredString("structure", 1, 128)
            .optionalStringArray("blocks", 1, 16, 1, 128)
            .optionalIntegerArray("dimensions", 3, 3, 1, 64)
            .constraint("build volume must not exceed 65536", values -> {
                Object dimensions = values.get("dimensions");
                if (!(dimensions instanceof List<?> list) || list.size() != 3) {
                    return true;
                }
                return ((Number) list.get(0)).longValue()
                    * ((Number) list.get(1)).longValue()
                    * ((Number) list.get(2)).longValue() <= 65_536;
            })
            .build();

        assertTrue(schema.validate(Map.of(
            "structure", "house",
            "blocks", List.of("oak_planks", "cobblestone"),
            "dimensions", List.of(9, 6, 9))).isValid());
        assertFalse(schema.validate(Map.of(
            "structure", "house",
            "blocks", List.of("oak_planks", 42),
            "dimensions", List.of(9, 6, 9))).isValid());
        assertFalse(schema.validate(Map.of(
            "structure", "warehouse",
            "dimensions", List.of(64, 64, 64))).isValid());
    }

    @Test
    void registryOwnsFactoryDescriptorAndValidationAsOneAtomicEntry() {
        ActionRegistry registry = ActionRegistry.getInstance();
        ActionDescriptor descriptor = descriptor("inspect_inventory", "inventory-plugin");

        registry.register(descriptor, (steve, task, context) -> null, 100);

        assertTrue(registry.hasAction("inspect_inventory"));
        assertEquals(descriptor, registry.getDescriptor("inspect_inventory").orElseThrow());
        assertTrue(registry.validate(new Task("inspect_inventory", Map.of())).isValid());
    }

    @Test
    void pluginCannotSilentlyOverwriteAnExistingDescriptor() {
        ActionRegistry registry = ActionRegistry.getInstance();
        registry.register(descriptor("inspect_inventory", "plugin-a"),
            (steve, task, context) -> null, 10);

        assertThrows(IllegalStateException.class, () -> registry.register(
            descriptor("inspect_inventory", "plugin-b"),
            (steve, task, context) -> null,
            100));
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyFactoryRemainsDirectlyAvailableButIsNotPlannable() {
        ActionRegistry registry = ActionRegistry.getInstance();
        registry.register("legacy_action", (steve, task, context) -> null);

        assertTrue(registry.hasAction("legacy_action"));
        assertFalse(registry.validate(new Task("legacy_action", Map.of("arbitrary", "value"))).isValid());
        assertFalse(registry.getPlannableDescriptors().stream()
            .anyMatch(descriptor -> descriptor.name().equals("legacy_action")));
    }

    @Test
    void failedPluginRegistrationRollsBackAllEntriesFromThatLoadAttempt() {
        ActionRegistry registry = ActionRegistry.getInstance();
        registry.register(descriptor("protected_action", "plugin-a"),
            (steve, task, context) -> null, 10);

        assertThrows(IllegalStateException.class, () -> registry.runRegistrationTransaction(() -> {
            registry.register(descriptor("partial_action", "plugin-b"),
                (steve, task, context) -> null, 20);
            registry.register(descriptor("protected_action", "plugin-b"),
                (steve, task, context) -> null, 20);
        }));

        assertFalse(registry.hasAction("partial_action"));
        assertEquals("plugin-a", registry.getPluginForAction("protected_action"));
    }

    private static ActionDescriptor descriptor(String name, String pluginId) {
        return new ActionDescriptor(
            name,
            "Lists the Steve inventory",
            pluginId,
            "1",
            ActionPermission.INTERACTION,
            JsonSchema.object().build(),
            List.of("{\"action\":\"" + name + "\",\"parameters\":{}}"),
            Set.of(ActionCapability.INVENTORY_READ));
    }
}
