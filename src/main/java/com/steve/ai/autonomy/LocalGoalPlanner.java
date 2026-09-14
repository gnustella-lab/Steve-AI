package com.steve.ai.autonomy;

import com.steve.ai.action.Task;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict local grammar: never interpret only the first clause of an open-ended request. */
public final class LocalGoalPlanner {
    private static final Pattern COMMAND = Pattern.compile(
        "^(craft|make|smelt|gather|collect|mine)\\s+(?:([0-9]{1,4})\\s+)?"
            + "([a-z0-9_:/-]+(?: [a-z0-9_-]+){0,2})[.!]?$"
    );

    public record Request(String action, String item, int quantity) {
        public Task task(int inventoryCount) {
            int missing = Math.max(0, quantity - Math.max(0, inventoryCount));
            return new Task(action, Map.of("gather".equals(action) ? "resource" : "item",
                item, "quantity", missing));
        }
    }

    public Optional<Request> parse(String description, Predicate<String> registeredItem) {
        if (description == null || description.length() > 256) return Optional.empty();
        Matcher matcher = COMMAND.matcher(description.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) return Optional.empty();
        int quantity = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
        if (quantity < 1 || quantity > 2048) return Optional.empty();
        String item = matcher.group(3).replace(' ', '_');
        if (!item.contains(":")) item = "minecraft:" + item;
        // Try the exact registry ID first: plural IDs such as oak_planks must stay intact.
        if (!registeredItem.test(item)) {
            if (!item.endsWith("s") || !registeredItem.test(item.substring(0, item.length() - 1))) {
                return Optional.empty();
            }
            item = item.substring(0, item.length() - 1);
        }
        String action = switch (matcher.group(1)) {
            case "craft", "make" -> "craft";
            case "smelt" -> "smelt";
            default -> "gather";
        };
        return Optional.of(new Request(action, item, quantity));
    }
}
