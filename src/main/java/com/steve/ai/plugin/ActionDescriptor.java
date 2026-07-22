package com.steve.ai.plugin;

import com.steve.ai.security.ActionPermission;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable metadata and parameter contract for a registered action. */
public record ActionDescriptor(
    String name,
    String description,
    String pluginId,
    String schemaVersion,
    ActionPermission requiredPermission,
    JsonSchema parameterSchema,
    List<String> examples,
    Set<ActionCapability> capabilities
) {
    public ActionDescriptor {
        name = requireText(name, "name").toLowerCase(java.util.Locale.ROOT);
        description = requireText(description, "description");
        pluginId = requireText(pluginId, "pluginId");
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        requiredPermission = Objects.requireNonNull(requiredPermission, "requiredPermission");
        parameterSchema = Objects.requireNonNull(parameterSchema, "parameterSchema");
        examples = List.copyOf(Objects.requireNonNull(examples, "examples"));
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }

    /** Creates compatibility metadata for the deprecated factory-only registration API. */
    public static ActionDescriptor legacy(String name, String pluginId) {
        return new ActionDescriptor(
            name,
            "Legacy action without declared metadata",
            pluginId == null || pluginId.isBlank() ? "legacy" : pluginId,
            "legacy",
            ActionPermission.requiredFor(name),
            JsonSchema.permissiveObject(),
            List.of(),
            Set.of());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }
}
