package com.steve.ai.action;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small bounded deep-copy utility for action handoff data. */
public final class BoundedData {
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_COLLECTION_ITEMS = 64;
    public static final int MAX_STRING_LENGTH = 512;
    public static final int MAX_DEPTH = 4;

    private BoundedData() {
    }

    public static Map<String, Object> copyMap(Map<?, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return copyMap(source, 0, new IdentityHashMap<>());
    }

    public static Object copyValue(Object value) {
        return copyValue(value, 0, new IdentityHashMap<>());
    }

    public static String boundedString(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_STRING_LENGTH
            ? value
            : value.substring(0, MAX_STRING_LENGTH);
    }

    private static Map<String, Object> copyMap(Map<?, ?> source, int depth,
            IdentityHashMap<Object, Boolean> visiting) {
        if (depth > MAX_DEPTH) {
            return Map.of("value", boundedString(String.valueOf(source)));
        }
        if (visiting.put(source, Boolean.TRUE) != null) {
            return Map.of("value", "<cycle>");
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (count++ >= MAX_ENTRIES) {
                break;
            }
            String key = boundedString(String.valueOf(entry.getKey()));
            copy.put(key, copyValue(entry.getValue(), depth + 1, visiting));
        }
        visiting.remove(source);
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyValue(Object value, int depth,
            IdentityHashMap<Object, Boolean> visiting) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Character || value instanceof Enum<?>) {
            return value instanceof String string ? boundedString(string) : value;
        }
        if (depth > MAX_DEPTH) {
            return boundedString(String.valueOf(value));
        }
        if (value instanceof Map<?, ?> map) {
            return copyMap(map, depth, visiting);
        }
        if (value instanceof Iterable<?> iterable) {
            if (visiting.put(value, Boolean.TRUE) != null) {
                return List.of("<cycle>");
            }
            List<Object> copy = new ArrayList<>();
            int count = 0;
            for (Object element : iterable) {
                if (count++ >= MAX_COLLECTION_ITEMS) {
                    break;
                }
                copy.add(copyValue(element, depth + 1, visiting));
            }
            visiting.remove(value);
            return Collections.unmodifiableList(copy);
        }
        if (value.getClass().isArray()) {
            if (visiting.put(value, Boolean.TRUE) != null) {
                return List.of("<cycle>");
            }
            int length = Math.min(Array.getLength(value), MAX_COLLECTION_ITEMS);
            List<Object> copy = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                copy.add(copyValue(Array.get(value, index), depth + 1, visiting));
            }
            visiting.remove(value);
            return Collections.unmodifiableList(copy);
        }
        return boundedString(String.valueOf(value));
    }
}
