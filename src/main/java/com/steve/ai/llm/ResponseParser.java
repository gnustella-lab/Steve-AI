package com.steve.ai.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.steve.ai.action.Task;
import com.steve.ai.action.TaskValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict parser for bounded operational decisions returned by an LLM. */
public class ResponseParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseParser.class);
    private static final int MAX_RESPONSE_LENGTH = 65_536;
    private static final int MAX_SUMMARY_LENGTH = 160;
    private static final int MAX_TASKS = 16;
    private static final int MAX_PARAMETERS = 32;
    private static final int MAX_PARAMETER_STRING_LENGTH = 512;
    private static final Set<String> TASK_FIELDS = Set.of("action", "parameters");
    private static final Set<String> GOAL_STATUSES = Set.of(
        "in_progress", "complete", "blocked", "paused", "failed");

    public enum Decision {
        ACT,
        COMPLETE,
        BLOCKED,
        ASK_USER
    }

    public static ParsedResponse parseAIResponse(String response) {
        return parseAIResponse(response, MAX_TASKS);
    }

    /** Parses a response while applying the caller's receding-horizon task limit. */
    public static ParsedResponse parseAIResponse(String response, int maxTasks) {
        if (response == null || response.isBlank() || response.length() > MAX_RESPONSE_LENGTH) {
            return null;
        }

        try {
            String jsonString = extractJSON(response);
            if (jsonString == null) return null;
            JsonElement root = JsonParser.parseString(jsonString);
            if (!root.isJsonObject()) return null;

            JsonObject json = root.getAsJsonObject();
            // Extra keys such as "reasoning" are ignored; only the operational schema is read.

            Decision decision = json.has("decision")
                ? parseDecision(readBoundedString(json.get("decision"), 32)) : Decision.ACT;
            if (decision == null) return null;

            String goalStatus = json.has("goalStatus")
                ? readBoundedString(json.get("goalStatus"), 32).toLowerCase(Locale.ROOT)
                : "in_progress";
            if (!GOAL_STATUSES.contains(goalStatus)) return null;

            String summary = readSummary(json);
            if (summary == null || summary.isBlank()) return null;

            if (!json.has("tasks") || !json.get("tasks").isJsonArray()) return null;
            JsonArray tasksArray = json.getAsJsonArray("tasks");
            int taskLimit = Math.max(0, Math.min(MAX_TASKS, maxTasks));
            if (tasksArray.size() > taskLimit) return null;

            List<Task> tasks = new ArrayList<>();
            for (JsonElement taskElement : tasksArray) {
                if (!taskElement.isJsonObject()) return null;
                Task task = parseTask(taskElement.getAsJsonObject());
                if (task == null || !TaskValidator.isValid(task)) return null;
                tasks.add(task);
            }

            if (!isConsistent(decision, goalStatus, tasks)) return null;
            if (!isSpeechExclusive(tasks)) return null;
            return new ParsedResponse(decision, goalStatus, summary, tasks);
        } catch (Exception e) {
            LOGGER.debug("Rejected AI response ({} characters): {}",
                response.length(), e.getClass().getSimpleName());
            return null;
        }
    }

    private static String readSummary(JsonObject json) {
        boolean hasSummary = json.has("summary");
        boolean hasLegacyPlan = json.has("plan");
        if (!hasSummary && !hasLegacyPlan) return null;
        String summary = hasSummary ? readBoundedString(json.get("summary"), MAX_SUMMARY_LENGTH) : "";
        String legacyPlan = hasLegacyPlan ? readBoundedString(json.get("plan"), MAX_SUMMARY_LENGTH) : "";
        if (hasSummary && hasLegacyPlan && !summary.equals(legacyPlan)) return null;
        return hasSummary ? summary : legacyPlan;
    }

    private static boolean isConsistent(Decision decision, String goalStatus, List<Task> tasks) {
        boolean hasTasks = !tasks.isEmpty();
        return switch (decision) {
            case ACT -> hasTasks && "in_progress".equals(goalStatus);
            case COMPLETE -> !hasTasks && "complete".equals(goalStatus);
            case BLOCKED -> !hasTasks && ("blocked".equals(goalStatus) || "failed".equals(goalStatus));
            case ASK_USER -> !hasTasks && ("paused".equals(goalStatus) || "blocked".equals(goalStatus));
        };
    }

    /** Player chat cannot share a horizon with world-mutating work. */
    private static boolean isSpeechExclusive(List<Task> tasks) {
        boolean anySay = false;
        for (Task task : tasks) {
            if ("say".equals(task.getAction())) {
                anySay = true;
                break;
            }
        }
        return !anySay || tasks.size() == 1;
    }

    private static Decision parseDecision(String value) {
        if (value == null) return null;
        try {
            return Decision.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Accepts exactly one JSON object, optionally wrapped by one conventional Markdown fence.
     * No substring extraction is performed, so surrounding prose is always rejected.
     */
    private static String extractJSON(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```") ) {
            int newline = cleaned.indexOf('\n');
            if (newline < 0) return null;
            String opening = cleaned.substring(0, newline).trim();
            if (!opening.equals("```") && !opening.equals("```json")) return null;
            if (!cleaned.endsWith("```")) return null;
            String body = cleaned.substring(newline + 1, cleaned.length() - 3).trim();
            if (body.contains("```")) return null;
            return isSingleObject(body) ? body : null;
        }
        if (cleaned.contains("```") || !isSingleObject(cleaned)) return null;
        return cleaned;
    }

    private static boolean isSingleObject(String value) {
        if (value == null || value.length() < 2
                || value.charAt(0) != '{' || value.charAt(value.length() - 1) != '}') {
            return false;
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0 && i != value.length() - 1) return false;
                if (depth < 0) return false;
            }
        }
        return !inString && !escaped && depth == 0;
    }

    private static Task parseTask(JsonObject taskObj) {
        if (taskObj.keySet().stream().anyMatch(field -> !TASK_FIELDS.contains(field))) return null;
        if (!taskObj.has("action") || !taskObj.get("action").isJsonPrimitive()
                || !taskObj.getAsJsonPrimitive("action").isString()) return null;

        String action = taskObj.get("action").getAsString().trim().toLowerCase(Locale.ROOT);
        if (action.isEmpty() || action.length() > 128) return null;
        if (!taskObj.has("parameters") || !taskObj.get("parameters").isJsonObject()) return null;

        JsonObject paramsObj = taskObj.getAsJsonObject("parameters");
        if (paramsObj.size() > MAX_PARAMETERS) return null;
        Map<String, Object> parameters = new HashMap<>();
        for (String key : paramsObj.keySet()) {
            if (key.isBlank() || key.length() > 64) return null;
            Object value = parseParameterValue(paramsObj.get(key));
            if (value == null) return null;
            parameters.put(key, value);
        }
        return new Task(action, Map.copyOf(parameters));
    }

    private static Object parseParameterValue(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isNumber()) {
                Number number = value.getAsNumber();
                return Double.isFinite(number.doubleValue()) ? number : null;
            }
            if (value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean();
            String text = value.getAsString();
            return text.length() <= MAX_PARAMETER_STRING_LENGTH ? text : null;
        }
        if (!value.isJsonArray() || value.getAsJsonArray().size() > MAX_PARAMETERS) return null;
        List<Object> list = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive()) return null;
            Object item = parseParameterValue(element);
            if (item == null) return null;
            list.add(item);
        }
        return List.copyOf(list);
    }

    private static String readBoundedString(JsonElement value, int maximumLength) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected a string field");
        }
        String text = value.getAsString().trim();
        if (text.length() > maximumLength) {
            throw new IllegalArgumentException("String field exceeds " + maximumLength + " characters");
        }
        return text;
    }

    public static class ParsedResponse {
        private final Decision decision;
        private final String goalStatus;
        private final String summary;
        private final List<Task> tasks;

        private ParsedResponse(Decision decision, String goalStatus, String summary, List<Task> tasks) {
            this.decision = decision;
            this.goalStatus = goalStatus;
            this.summary = summary;
            this.tasks = List.copyOf(tasks);
        }

        public Decision getDecision() { return decision; }
        public String getGoalStatus() { return goalStatus; }
        public String getSummary() { return summary; }

        /** @deprecated Private reasoning is not part of the operational protocol. */
        @Deprecated(forRemoval = false)
        public String getReasoning() { return ""; }

        /** @deprecated Use {@link #getSummary()}. */
        @Deprecated(forRemoval = false)
        public String getPlan() { return summary; }

        public List<Task> getTasks() { return tasks; }
    }
}
