package com.steve.ai.llm;

import com.steve.ai.plugin.ActionCapability;
import com.steve.ai.plugin.ActionDescriptor;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.plugin.JsonSchema;
import com.steve.ai.security.ActionPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    @AfterEach
    void clearRegistry() {
        ActionRegistry.getInstance().clear();
    }

    @Test
    void buildsAvailableActionsFromRegistryMetadata() {
        ActionDescriptor descriptor = new ActionDescriptor(
            "dance",
            "Performs a harmless dance",
            "example-plugin",
            "1",
            ActionPermission.INTERACTION,
            JsonSchema.object().optionalInteger("seconds", 1, 30).build(),
            List.of("{\"action\":\"dance\",\"parameters\":{\"seconds\":5}}"),
            Set.of(ActionCapability.MOVEMENT));
        ActionRegistry.getInstance().register(descriptor, (steve, task, context) -> null, 0);

        String prompt = PromptBuilder.buildSystemPrompt();

        assertTrue(prompt.contains("dance"));
        assertTrue(prompt.contains("Performs a harmless dance"));
        assertTrue(prompt.contains("seconds"));
        assertTrue(prompt.contains("\"summary\""));
        assertFalse(prompt.contains("brief thought"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void excludesMetadataFreeLegacyActionsFromTheLlmCatalog() {
        ActionRegistry.getInstance().register("legacy_action", (steve, task, context) -> null);

        String prompt = PromptBuilder.buildSystemPrompt();

        assertFalse(prompt.contains("legacy_action"));
    }
}
