package com.steve.ai.security;

import com.steve.ai.plugin.ActionCapability;
import com.steve.ai.plugin.ActionDescriptor;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.plugin.JsonSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionManagerTest {

    @AfterEach
    void cleanSingletons() {
        PermissionManager.getInstance().clear();
        ActionRegistry.getInstance().clear();
    }

    @Test
    void resolvesRequiredPermissionFromDescriptorAndSteveUuid() {
        ActionDescriptor descriptor = new ActionDescriptor(
            "dangerous_test",
            "Test-only combat action",
            "test-plugin",
            "1",
            ActionPermission.COMBAT,
            JsonSchema.object().build(),
            List.of(),
            Set.of(ActionCapability.COMBAT));
        ActionRegistry.getInstance().register(descriptor, (steve, task, context) -> null, 0);
        UUID steveUuid = UUID.randomUUID();
        PermissionManager.getInstance().setPermission(steveUuid, ActionPermission.BUILDING);

        assertFalse(PermissionManager.getInstance().canExecute(steveUuid, "dangerous_test"));

        PermissionManager.getInstance().setPermission(steveUuid, ActionPermission.COMBAT);
        assertTrue(PermissionManager.getInstance().canExecute(steveUuid, "dangerous_test"));
    }

    @Test
    void unknownActionsFailClosedEvenWithOrdinaryPermissions() {
        UUID steveUuid = UUID.randomUUID();
        PermissionManager.getInstance().setPermission(steveUuid, ActionPermission.COMBAT);

        assertFalse(PermissionManager.getInstance().canExecute(steveUuid, "not_registered"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyNameOverrideRemainsEffectiveUntilUuidMigrationCompletes() {
        ActionRegistry.getInstance().register(
            new ActionDescriptor(
                "compatibility_test",
                "Compatibility permission test",
                "test-plugin",
                "1",
                ActionPermission.MOVEMENT,
                JsonSchema.object().build(),
                List.of(),
                Set.of(ActionCapability.MOVEMENT)),
            (steve, task, context) -> null,
            0);
        UUID steveUuid = UUID.randomUUID();
        PermissionManager manager = PermissionManager.getInstance();
        manager.setPermission("Bob", ActionPermission.NONE);

        assertFalse(manager.canExecute(steveUuid, "Bob", "compatibility_test"));

        manager.setPermission(steveUuid, ActionPermission.MOVEMENT);
        assertTrue(manager.canExecute(steveUuid, "Bob", "compatibility_test"));
    }
}
