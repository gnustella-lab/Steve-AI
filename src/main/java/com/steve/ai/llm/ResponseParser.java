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

public class ResponseParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseParser.class);
    private static final int MAX_RESPONSE_LENGTH = 65_536;
    private static final int MAX_SUMMARY_LENGTH = 160;
    private static final int MAX_TASKS = 64;
    private static final int MAX_PARAMETERS = 32;
    private static final int MAX_PARAMETER_STRING_LENGTH = 512;
    private static final Set<String> TOP_LEVEL_FIELDS = Set.of(
        "decision", "goalStatus", "summary", "tasks", "plan", "reasoning");
    private static final Set<String> TASK_FIELDS = Set.of("action", "parameters");
    private static final Set<String> GOAL_STATUSES = Set.of("in_progress", "complete", "blocked", "paused", "failed");

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
            
            JsonElement root = JsonParser.parseString(jsonString);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject json = root.getAsJsonObject();
            if (json.keySet().stream().anyMatch(field -> !TOP_LEVEL_FIELDS.contains(field))) {
                return null;
            }

            Decision decision = json.has("decision")
                ? parseDecision(readBoundedString(json.get("decision"), 32)) : Decision.ACT;
            if (decision == null) {
                return null;
            }
            String goalStatus = json.has("goalStatus")
                ? readBoundedString(json.get("goalStatus"), 32).toLowerCase(Locale.ROOT)
                : "in_progress";
            if (!GOAL_STATUSES.contains(goalStatus)) {
                return null;
            }

            String summary = json.has("summary")
                ? readBoundedString(json.get("summary"), MAX_SUMMARY_LENGTH)
                : readOptionalBoundedString(json, "plan", MAX_SUMMARY_LENGTH);
            String legacyReasoning = readOptionalBoundedString(json, "reasoning", MAX_SUMMARY_LENGTH);
            List<Task> tasks = new ArrayList<>();

            if (!json.has("tasks") || !json.get("tasks").isJsonArray()) {
                return null;
            }
            JsonArray tasksArray = json.getAsJsonArray("tasks");
            if (tasksArray.size() > Math.max(0, Math.min(MAX_TASKS, maxTasks))) {
                return null;
            }
            for (JsonElement taskElement : tasksArray) {
                if (!taskElement.isJsonObject()) {
                    return null;
                }
                Task task = parseTask(taskElement.getAsJsonObject());
                if (task == null || !TaskValidator.isValid(task)) {
                    return null;
                }
                tasks.add(task);
            }

            return new ParsedResponse(decision, goalStatus, summary, legacyReasoning, tasks);
            
        } catch (Exception e) {
            LOGGER.warn("Failed to parse AI response ({} characters): {}",
                response.length(), e.getClass().getSimpleName());
            return null;
        }
    }

    private static Decision parseDecision(String value) {
        if (value == null) return null;
        try {
            return Decision.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String extractJSON(String response) {
        String cleaned = response.trim();
        
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        cleaned = cleaned.trim();

        int objectStart = cleaned.indexOf('{');
        if (objectStart < 0) {
            return cleaned;
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;

        for (int i = objectStart; i < cleaned.length(); i++) {
            char current = cleaned.charAt(i);

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
                if (depth == 0) {
                    return cleaned.substring(objectStart, i + 1);
                }
            }
        }

        return cleaned.substring(objectStart);
    }

    private static Task parseTask(JsonObject taskObj) {
        if (taskObj.keySet().stream().anyMatch(field -> !TASK_FIELDS.contains(field))) {
            return null;
        }
        if (!taskObj.has("action") || !taskObj.get("action").isJsonPrimitive()
                || !taskObj.getAsJsonPrimitive("action").isString()) {
            return null;
        }
        
        String action = taskObj.get("action").getAsString().trim().toLowerCase(Locale.ROOT);
        if (action.isEmpty() || action.length() > 128) {
            return null;
        }
        Map<String, Object> parameters = new HashMap<>();

        if (!taskObj.has("parameters") || !taskObj.get("parameters").isJsonObject()) {
            return null;
        }
        JsonObject paramsObj = taskObj.getAsJsonObject("parameters");
        if (paramsObj.size() > MAX_PARAMETERS) {
            return null;
        }
        for (String key : paramsObj.keySet()) {
            if (key.isBlank() || key.length() > 64) {
                return null;
            }
            Object value = parseParameterValue(paramsObj.get(key));
            if (value == null) {
                return null;
            }
            parameters.put(key, value);
        }
        return new Task(action, parameters);
    }

    private static Object parseParameterValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isNumber()) {
                return value.getAsNumber();
            }
            if (value.getAsJsonPrimitive().isBoolean()) {
                return value.getAsBoolean();
            }
            String text = value.getAsString();
            return text.length() <= MAX_PARAMETER_STRING_LENGTH ? text : null;
        }
        if (!value.isJsonArray() || value.getAsJsonArray().size() > MAX_PARAMETERS) {
            return null;
        }
        List<Object> list = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive()) {
                return null;
            }
            Object item = parseParameterValue(element);
            if (item == null) {
                return null;
            }
            list.add(item);
        }
        return list;
    }

    private static String readOptionalBoundedString(JsonObject json, String field, int maximumLength) {
        return json.has(field) ? readBoundedString(json.get(field), maximumLength) : "";
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
        private final String legacyReasoning;
        private final List<Task> tasks;

        private ParsedResponse(Decision decision, String goalStatus, String summary,
                String legacyReasoning, List<Task> tasks) {
            this.decision = decision;
            this.goalStatus = goalStatus;
            this.summary = summary;
            this.legacyReasoning = legacyReasoning;
            this.tasks = List.copyOf(tasks);
        }

        public Decision getDecision() {
            return decision;
        }

        public String getGoalStatus() {
            return goalStatus;
        }

        public String getSummary() {
            return summary;
        }

        /** @deprecated Private reasoning is no longer requested. Retained for one response-format version. */
        @Deprecated(forRemoval = false)
        public String getReasoning() {
            return legacyReasoning;
        }

        /** @deprecated Use {@link #getSummary()}. */
        @Deprecated(forRemoval = false)
        public String getPlan() {
            return summary;
        }

        public List<Task> getTasks() {
            return tasks;
        }
    }
}

