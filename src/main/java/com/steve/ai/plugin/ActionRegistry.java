package com.steve.ai.plugin;

import com.steve.ai.action.actions.BaseAction;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.execution.ActionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for action factories using the Registry Pattern.
 *
 * <p>This class provides dynamic action lookup and creation, replacing the
 * hardcoded switch statement in ActionExecutor with a flexible, extensible
 * approach that follows the Open/Closed Principle.</p>
 *
 * <p><b>Thread Safety:</b> All operations are thread-safe using ConcurrentHashMap.</p>
 *
 * <p><b>Example Usage:</b></p>
 * <pre>
 * ActionRegistry registry = ActionRegistry.getInstance();
 *
 * // Register an action factory
 * registry.register("mine", (steve, task, ctx) -&gt; new MineBlockAction(steve, task));
 *
 * // Create an action
 * BaseAction action = registry.createAction("mine", steve, task, context);
 *
 * // Check if action exists
 * if (registry.hasAction("mine")) {
 *     // ...
 * }
 * </pre>
 *
 * <p><b>Design Patterns:</b></p>
 * <ul>
 *   <li><b>Registry Pattern</b>: Central lookup for factories</li>
 *   <li><b>Singleton Pattern</b>: Single registry instance</li>
 *   <li><b>Factory Pattern</b>: Factories create action instances</li>
 * </ul>
 *
 * @since 1.1.0
 * @see ActionPlugin
 * @see ActionFactory
 */
public class ActionRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActionRegistry.class);

    private static final ActionRegistry INSTANCE = new ActionRegistry();

    /**
     * Map of action name to factory, with metadata for conflict resolution.
     * Thread-safe via ConcurrentHashMap.
     */
    private final ConcurrentHashMap<String, FactoryEntry> factories;

    /**
     * Tracks which plugin registered which action for debugging.
     */
    private final ConcurrentHashMap<String, String> actionToPlugin;

    private ActionRegistry() {
        this.factories = new ConcurrentHashMap<>();
        this.actionToPlugin = new ConcurrentHashMap<>();
    }

    /**
     * Returns the singleton registry instance.
     *
     * @return ActionRegistry singleton
     */
    public static ActionRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers an action factory with default priority.
     *
     * @param actionName Action name (e.g., "mine", "build")
     * @param factory    Factory to create action instances
     * @throws IllegalArgumentException if actionName or factory is null
     * @deprecated Register an {@link ActionDescriptor} with its factory instead.
     */
    @Deprecated(forRemoval = false)
    public void register(String actionName, ActionFactory factory) {
        register(actionName, factory, 0, "unknown");
    }

    /**
     * Registers a factory and its complete planning and validation contract atomically.
     *
     * <p>This API never replaces an action owned by another plugin. A conflicting plugin must choose a distinct
     * action name instead of relying on load order.</p>
     *
     * @param descriptor action metadata and parameter schema
     * @param factory action factory
     * @param priority priority retained for diagnostics and same-plugin reloads
     */
    public void register(ActionDescriptor descriptor, ActionFactory factory, int priority) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(factory, "factory");
        String normalizedName = normalize(descriptor.name());

        factories.compute(normalizedName, (key, existing) -> {
            if (existing != null && !existing.pluginId.equals(descriptor.pluginId())) {
                throw new IllegalStateException("Action '" + normalizedName + "' is already registered by plugin '"
                    + existing.pluginId + "'");
            }
            if (existing != null && priority < existing.priority) {
                LOGGER.warn("Ignoring lower-priority reload of action '{}' from plugin '{}'",
                    normalizedName, descriptor.pluginId());
                return existing;
            }
            actionToPlugin.put(normalizedName, descriptor.pluginId());
            LOGGER.info("Registered described action '{}' from plugin '{}' (schema {}, priority {})",
                normalizedName, descriptor.pluginId(), descriptor.schemaVersion(), priority);
            return new FactoryEntry(factory, priority, descriptor.pluginId(), descriptor);
        });
    }

    /**
     * Registers an action factory with priority for conflict resolution.
     *
     * <p>If an action with the same name is already registered, the factory
     * with higher priority wins. If priorities are equal, the newer registration
     * wins (last-write-wins).</p>
     *
     * @param actionName Action name (e.g., "mine", "build")
     * @param factory    Factory to create action instances
     * @param priority   Priority for conflict resolution (higher wins)
     * @param pluginId   ID of the plugin registering this action
     * @throws IllegalArgumentException if actionName or factory is null
     */
    @Deprecated(forRemoval = false)
    public void register(String actionName, ActionFactory factory, int priority, String pluginId) {
        if (actionName == null || actionName.isBlank()) {
            throw new IllegalArgumentException("Action name cannot be null or blank");
        }
        if (factory == null) {
            throw new IllegalArgumentException("Factory cannot be null");
        }

        String normalizedName = normalize(actionName);
        LOGGER.warn("Plugin '{}' used deprecated metadata-free registration for action '{}'; "
            + "migrate to ActionDescriptor", pluginId, normalizedName);
        ActionDescriptor descriptor = ActionDescriptor.legacy(normalizedName, pluginId);

        factories.compute(normalizedName, (key, existing) -> {
            if (existing == null) {
                LOGGER.info("Registered action '{}' from plugin '{}' (priority: {})",
                    normalizedName, pluginId, priority);
                actionToPlugin.put(normalizedName, pluginId);
                return new FactoryEntry(factory, priority, pluginId, descriptor);
            }

            // Conflict resolution: higher priority wins
            if (priority > existing.priority) {
                LOGGER.info("Action '{}' overridden by plugin '{}' (priority {} > {})",
                    normalizedName, pluginId, priority, existing.priority);
                actionToPlugin.put(normalizedName, pluginId);
                return new FactoryEntry(factory, priority, pluginId, descriptor);
            } else if (priority == existing.priority) {
                LOGGER.warn("Action '{}' already registered by '{}' with same priority, keeping existing",
                    normalizedName, existing.pluginId);
                return existing;
            } else {
                LOGGER.debug("Action '{}' registration from '{}' ignored (priority {} < {})",
                    normalizedName, pluginId, priority, existing.priority);
                return existing;
            }
        });
    }

    /**
     * Unregisters an action.
     *
     * @param actionName Action name to unregister
     * @return true if action was registered and removed, false otherwise
     */
    public boolean unregister(String actionName) {
        if (actionName == null) return false;

        String normalizedName = normalize(actionName);
        FactoryEntry removed = factories.remove(normalizedName);
        actionToPlugin.remove(normalizedName);

        if (removed != null) {
            LOGGER.info("Unregistered action '{}'", normalizedName);
            return true;
        }
        return false;
    }

    /**
     * Creates an action instance using the registered factory.
     *
     * @param actionName Action name
     * @param steve      Steve entity
     * @param task       Task with parameters
     * @param context    Action context with dependencies
     * @return Created action, or null if action not found
     */
    public BaseAction createAction(String actionName, SteveEntity steve, Task task, ActionContext context) {
        if (actionName == null) {
            LOGGER.warn("Cannot create action: actionName is null");
            return null;
        }

        String normalizedName = normalize(actionName);
        FactoryEntry entry = factories.get(normalizedName);

        if (entry == null) {
            LOGGER.warn("No factory registered for action '{}'", normalizedName);
            return null;
        }

        try {
            BaseAction action = entry.factory.create(steve, task, context);
            LOGGER.debug("Created action '{}' from plugin '{}'", normalizedName, entry.pluginId);
            return action;
        } catch (Exception e) {
            LOGGER.error("Failed to create action '{}': {}", normalizedName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Checks if an action is registered.
     *
     * @param actionName Action name
     * @return true if registered
     */
    public boolean hasAction(String actionName) {
        if (actionName == null) return false;
        return factories.containsKey(normalize(actionName));
    }

    /** Returns metadata for an action name. */
    public Optional<ActionDescriptor> getDescriptor(String actionName) {
        if (actionName == null) {
            return Optional.empty();
        }
        FactoryEntry entry = factories.get(normalize(actionName));
        return entry == null ? Optional.empty() : Optional.of(entry.descriptor);
    }

    /** Returns all descriptors in deterministic action-name order. */
    public List<ActionDescriptor> getDescriptors() {
        return factories.values().stream()
            .map(entry -> entry.descriptor)
            .sorted(Comparator.comparing(ActionDescriptor::name))
            .toList();
    }

    /**
     * Returns only actions with an explicit parameter contract, safe for LLM planning.
     * Metadata-free registrations remain available to trusted plugin code during migration.
     */
    public List<ActionDescriptor> getPlannableDescriptors() {
        return getDescriptors().stream()
            .filter(descriptor -> !"legacy".equals(descriptor.schemaVersion()))
            .toList();
    }

    /** Validates one task against the schema owned by its registered factory. */
    public JsonSchema.ValidationResult validate(Task task) {
        if (task == null || task.getAction() == null || task.getParameters() == null) {
            return JsonSchema.ValidationResult.invalid("task, action and parameters are required");
        }
        FactoryEntry entry = factories.get(normalize(task.getAction()));
        if (entry == null) {
            return JsonSchema.ValidationResult.invalid("unknown action: " + task.getAction());
        }
        if ("legacy".equals(entry.descriptor.schemaVersion())) {
            return JsonSchema.ValidationResult.invalid(
                "legacy action requires an explicit schema before LLM planning");
        }
        return entry.descriptor.parameterSchema().validate(task.getParameters());
    }

    /**
     * Executes one plugin's registrations atomically and restores the previous registry on failure.
     * Plugin loading is serialized by {@link PluginManager}; this guard also synchronizes direct tests.
     */
    synchronized void runRegistrationTransaction(Runnable registration) {
        Objects.requireNonNull(registration, "registration");
        Map<String, FactoryEntry> factorySnapshot = Map.copyOf(factories);
        Map<String, String> ownerSnapshot = Map.copyOf(actionToPlugin);
        try {
            registration.run();
        } catch (RuntimeException | Error failure) {
            factories.clear();
            factories.putAll(factorySnapshot);
            actionToPlugin.clear();
            actionToPlugin.putAll(ownerSnapshot);
            throw failure;
        }
    }

    /**
     * Returns all registered action names.
     *
     * @return Unmodifiable set of action names
     */
    public Set<String> getRegisteredActions() {
        return Collections.unmodifiableSet(factories.keySet());
    }

    /**
     * Returns the number of registered actions.
     *
     * @return Action count
     */
    public int getActionCount() {
        return factories.size();
    }

    /**
     * Returns which plugin registered a specific action.
     *
     * @param actionName Action name
     * @return Plugin ID, or null if action not found
     */
    public String getPluginForAction(String actionName) {
        if (actionName == null) return null;
        return actionToPlugin.get(normalize(actionName));
    }

    /**
     * Clears all registered actions.
     *
     * <p>Used during shutdown or testing.</p>
     */
    public void clear() {
        factories.clear();
        actionToPlugin.clear();
        LOGGER.info("ActionRegistry cleared");
    }

    /**
     * Returns a formatted string listing all registered actions.
     *
     * <p>Useful for dynamic prompt building to tell LLM what actions are available.</p>
     *
     * @return Comma-separated list of action names
     */
    public String getActionsAsList() {
        return String.join(", ", getRegisteredActions());
    }

    /**
     * Returns a map of action names to the plugins that registered them.
     * Useful for detecting conflicts between plugins.
     *
     * @return Unmodifiable map of action name → plugin ID
     */
    public Map<String, String> getActionOwnership() {
        return Collections.unmodifiableMap(new HashMap<>(actionToPlugin));
    }

    /**
     * Validates that no two different plugins register the same action name
     * with the same priority. Returns a report string suitable for logging or
     * displaying in status commands.
     *
     * @return A summary of conflicts, or "No conflicts detected" if clean
     */
    public String validateConflicts() {
        // Group registrations by action name
        Map<String, Set<String>> actionToPlugins = new HashMap<>();
        for (var entry : actionToPlugin.entrySet()) {
            actionToPlugins.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).add(entry.getValue());
        }

        StringBuilder report = new StringBuilder();
        int conflictCount = 0;

        for (var entry : actionToPlugins.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflictCount++;
                report.append("  • '").append(entry.getKey()).append("' registered by: ")
                    .append(String.join(", ", entry.getValue())).append("\n");
            }
        }

        if (conflictCount == 0) {
            return "No conflicts detected";
        }

        return conflictCount + " action name conflict(s) found:\n" + report.toString().trim();
    }

    /**
     * Internal entry storing factory with metadata.
     */
    private static class FactoryEntry {
        final ActionFactory factory;
        final int priority;
        final String pluginId;
        final ActionDescriptor descriptor;

        FactoryEntry(ActionFactory factory, int priority, String pluginId, ActionDescriptor descriptor) {
            this.factory = factory;
            this.priority = priority;
            this.pluginId = pluginId;
            this.descriptor = descriptor;
        }
    }

    private static String normalize(String actionName) {
        return actionName.toLowerCase(Locale.ROOT).trim();
    }
}
