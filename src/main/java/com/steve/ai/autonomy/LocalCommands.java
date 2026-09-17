package com.steve.ai.autonomy;

import com.steve.ai.action.Task;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Lets the LLM hand a single craft/gather/smelt command to {@link LocalGoalPlanner}
 * so that step runs offline with English or Portuguese grammar.
 */
public final class LocalCommands {
    private static final LocalGoalPlanner PLANNER = new LocalGoalPlanner();
    private static final Set<String> DIRECT_ACTIONS = Set.of("craft", "smelt", "gather", "mine");

    private LocalCommands() {
    }

    /** Parses one offline command into an executable craft/gather/smelt task. */
    public static Optional<Task> toExecutableTask(String command, Predicate<String> registeredItem,
            ToIntFunction<String> inventoryCount) {
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }
        return PLANNER.parse(command, registeredItem)
            .map(request -> request.task(inventoryCount.applyAsInt(request.item())));
    }

    /**
     * Expands {@code local} commands and rewrites Portuguese/English item names on
     * craft/gather/mine/smelt tasks. Unknown commands are returned unchanged.
     */
    public static Task expand(Task task, Predicate<String> registeredItem, ToIntFunction<String> inventoryCount) {
        if (task == null || task.getAction() == null) {
            return task;
        }
        String action = task.getAction().toLowerCase(Locale.ROOT);
        if ("local".equals(action)) {
            return toExecutableTask(task.getStringParameter("command"), registeredItem, inventoryCount)
                .orElse(task);
        }
        if (!DIRECT_ACTIONS.contains(action)) {
            return task;
        }
        String itemKey = switch (action) {
            case "gather" -> "resource";
            case "mine" -> "block";
            default -> "item";
        };
        String raw = task.getStringParameter(itemKey);
        if (raw == null || raw.isBlank()) {
            return task;
        }
        int quantity = task.getIntParameter("quantity", 1);
        String synthetic = action + " " + quantity + " " + raw.replace('_', ' ');
        return toExecutableTask(synthetic, registeredItem, inventoryCount).orElse(task);
    }
}
