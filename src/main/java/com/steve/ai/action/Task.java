package com.steve.ai.action;

import java.util.Map;

/** Immutable, bounded handoff from planning to the action runtime. */
public final class Task {
    public static final int MAX_PARAMETER_COUNT = BoundedData.MAX_ENTRIES;

    private final String action;
    private final Map<String, Object> parameters;

    public Task(String action, Map<String, Object> parameters) {
        this.action = BoundedData.boundedString(action);
        this.parameters = BoundedData.copyMap(parameters);
    }

    public String getAction() {
        return action;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public Object getParameter(String key) {
        return parameters.get(key);
    }

    public String getStringParameter(String key) {
        Object value = parameters.get(key);
        return value != null ? value.toString() : null;
    }

    public String getStringParameter(String key, String defaultValue) {
        Object value = parameters.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    public int getIntParameter(String key, int defaultValue) {
        Object value = parameters.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    public boolean hasParameters(String... keys) {
        for (String key : keys) {
            if (!parameters.containsKey(key)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "Task{action='" + action + "', parameters=" + parameters + "}";
    }
}

