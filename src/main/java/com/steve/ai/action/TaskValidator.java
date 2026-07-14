package com.steve.ai.action;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Valida tarefas geradas por LLM antes que elas alcancem a API do Minecraft.
 */
public final class TaskValidator {
    private static final int MAX_COORDINATE = 30_000_000;
    private static final int MIN_Y = -2_048;
    private static final int MAX_Y = 2_048;
    private static final int MAX_QUANTITY = 2_048;
    private static final int MAX_BUILD_DIMENSION = 64;
    private static final long MAX_BUILD_VOLUME = 65_536;

    private TaskValidator() {
    }

    public static boolean isValid(Task task) {
        if (task == null || task.getAction() == null || task.getParameters() == null) {
            return false;
        }

        String action = task.getAction().trim().toLowerCase(Locale.ROOT);
        if (action.isEmpty()) {
            return false;
        }

        Map<String, Object> parameters = task.getParameters();
        return switch (action) {
            case "pathfind" -> validCoordinates(parameters);
            case "mine" -> validIdentifier(parameters.get("block"))
                && validInteger(parameters.get("quantity"), 1, MAX_QUANTITY);
            case "place" -> validIdentifier(parameters.get("block"))
                && validCoordinates(parameters);
            case "attack" -> validIdentifier(parameters.get("target"));
            case "follow" -> validIdentifier(parameters.get("player"));
            case "gather" -> validIdentifier(parameters.get("resource"))
                && validInteger(parameters.get("quantity"), 1, MAX_QUANTITY);
            case "build" -> validBuild(parameters);
            default -> false;
        };
    }

    private static boolean validCoordinates(Map<String, Object> parameters) {
        return validInteger(parameters.get("x"), -MAX_COORDINATE, MAX_COORDINATE)
            && validInteger(parameters.get("y"), MIN_Y, MAX_Y)
            && validInteger(parameters.get("z"), -MAX_COORDINATE, MAX_COORDINATE);
    }

    private static boolean validBuild(Map<String, Object> parameters) {
        if (!validIdentifier(parameters.get("structure"))) {
            return false;
        }

        Object blocks = parameters.get("blocks");
        if (blocks != null) {
            if (!(blocks instanceof List<?> blockList) || blockList.isEmpty() || blockList.size() > 16) {
                return false;
            }
            if (blockList.stream().anyMatch(block -> !validIdentifier(block))) {
                return false;
            }
        }

        Object dimensions = parameters.get("dimensions");
        if (dimensions == null) {
            Object width = parameters.getOrDefault("width", 9);
            Object height = parameters.getOrDefault("height", 6);
            Object depth = parameters.getOrDefault("depth", 9);
            return validInteger(width, 1, MAX_BUILD_DIMENSION)
                && validInteger(height, 1, MAX_BUILD_DIMENSION)
                && validInteger(depth, 1, MAX_BUILD_DIMENSION)
                && ((Number) width).longValue()
                    * ((Number) height).longValue()
                    * ((Number) depth).longValue() <= MAX_BUILD_VOLUME;
        }

        if (!(dimensions instanceof List<?> values) || values.size() != 3) {
            return false;
        }

        if (!validInteger(values.get(0), 1, MAX_BUILD_DIMENSION)
                || !validInteger(values.get(1), 1, MAX_BUILD_DIMENSION)
                || !validInteger(values.get(2), 1, MAX_BUILD_DIMENSION)) {
            return false;
        }

        long width = ((Number) values.get(0)).longValue();
        long height = ((Number) values.get(1)).longValue();
        long depth = ((Number) values.get(2)).longValue();
        return width * height * depth <= MAX_BUILD_VOLUME;
    }


    private static boolean validIdentifier(Object value) {
        if (!(value instanceof String stringValue)) {
            return false;
        }
        String trimmed = stringValue.trim();
        return !trimmed.isEmpty() && trimmed.length() <= 128;
    }

    private static boolean validInteger(Object value, int min, int max) {
        if (!(value instanceof Number number)) {
            return false;
        }
        double numericValue = number.doubleValue();
        return Double.isFinite(numericValue)
            && numericValue == Math.rint(numericValue)
            && numericValue >= min
            && numericValue <= max;
    }
}
