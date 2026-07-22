package com.steve.ai.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Small, deterministic JSON Schema subset used at the LLM trust boundary.
 *
 * <p>The supported types intentionally match action parameters: string and integer. Unknown properties are rejected
 * unless a legacy permissive schema is explicitly requested.</p>
 */
public final class JsonSchema {
    private final Map<String, Property> properties;
    private final Set<String> required;
    private final boolean additionalProperties;
    private final List<Constraint> constraints;

    private JsonSchema(Map<String, Property> properties, Set<String> required, boolean additionalProperties,
            List<Constraint> constraints) {
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        this.required = Set.copyOf(required);
        this.additionalProperties = additionalProperties;
        this.constraints = List.copyOf(constraints);
    }

    public static Builder object() {
        return new Builder(false);
    }

    public static JsonSchema permissiveObject() {
        return new Builder(true).build();
    }

    /** Validates a decoded JSON object without coercing values. */
    public ValidationResult validate(Map<String, Object> values) {
        if (values == null) {
            return ValidationResult.invalid("parameters must be an object");
        }
        List<String> errors = new ArrayList<>();
        for (String field : required) {
            if (!values.containsKey(field)) {
                errors.add("missing required parameter: " + field);
            }
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Property property = properties.get(entry.getKey());
            if (property == null) {
                if (!additionalProperties) {
                    errors.add("unknown parameter: " + entry.getKey());
                }
                continue;
            }
            property.validate(entry.getKey(), entry.getValue(), errors);
        }
        if (errors.isEmpty()) {
            for (Constraint constraint : constraints) {
                if (!constraint.validator.test(values)) {
                    errors.add(constraint.description);
                }
            }
        }
        return errors.isEmpty() ? ValidationResult.valid() : new ValidationResult(false, errors);
    }

    /** Returns deterministic JSON Schema text suitable for prompts and diagnostics. */
    public String toJson() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject propertiesJson = new JsonObject();
        for (Map.Entry<String, Property> entry : properties.entrySet()) {
            propertiesJson.add(entry.getKey(), entry.getValue().toJson());
        }
        schema.add("properties", propertiesJson);
        JsonArray requiredJson = new JsonArray();
        required.stream().sorted().forEach(requiredJson::add);
        schema.add("required", requiredJson);
        schema.addProperty("additionalProperties", additionalProperties);
        if (!constraints.isEmpty()) {
            JsonArray constraintDescriptions = new JsonArray();
            constraints.forEach(constraint -> constraintDescriptions.add(constraint.description));
            schema.add("x-steve-constraints", constraintDescriptions);
        }
        return schema.toString();
    }

    public static final class Builder {
        private final Map<String, Property> properties = new LinkedHashMap<>();
        private final java.util.Set<String> required = new java.util.LinkedHashSet<>();
        private final List<Constraint> constraints = new ArrayList<>();
        private final boolean additionalProperties;

        private Builder(boolean additionalProperties) {
            this.additionalProperties = additionalProperties;
        }

        public Builder requiredString(String name, int minLength, int maxLength) {
            add(name, Property.string(minLength, maxLength));
            required.add(name);
            return this;
        }

        public Builder optionalString(String name, int minLength, int maxLength) {
            add(name, Property.string(minLength, maxLength));
            return this;
        }

        public Builder requiredInteger(String name, long minimum, long maximum) {
            add(name, Property.integer(minimum, maximum));
            required.add(name);
            return this;
        }

        public Builder optionalInteger(String name, long minimum, long maximum) {
            add(name, Property.integer(minimum, maximum));
            return this;
        }

        public Builder optionalStringArray(String name, int minItems, int maxItems,
                int minLength, int maxLength) {
            add(name, Property.stringArray(minItems, maxItems, minLength, maxLength));
            return this;
        }

        public Builder optionalIntegerArray(String name, int minItems, int maxItems,
                long minimum, long maximum) {
            add(name, Property.integerArray(minItems, maxItems, minimum, maximum));
            return this;
        }

        public Builder constraint(String description, Predicate<Map<String, Object>> validator) {
            if (description == null || description.isBlank() || validator == null) {
                throw new IllegalArgumentException("Constraint description and validator are required");
            }
            constraints.add(new Constraint(description.trim(), validator));
            return this;
        }

        public JsonSchema build() {
            return new JsonSchema(properties, required, additionalProperties, constraints);
        }

        private void add(String name, Property property) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Property name cannot be blank");
            }
            if (properties.putIfAbsent(name, property) != null) {
                throw new IllegalArgumentException("Duplicate property: " + name);
            }
        }
    }

    public record ValidationResult(boolean isValid, List<String> errors) {
        public ValidationResult {
            errors = List.copyOf(errors);
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, List.of(error));
        }
    }

    private enum PropertyType {
        STRING,
        INTEGER,
        STRING_ARRAY,
        INTEGER_ARRAY
    }

    private record Property(PropertyType type, long minimum, long maximum, int minItems, int maxItems) {
        private static Property string(int minLength, int maxLength) {
            if (minLength < 0 || maxLength < minLength) {
                throw new IllegalArgumentException("Invalid string length range");
            }
            return new Property(PropertyType.STRING, minLength, maxLength, 0, 0);
        }

        private static Property integer(long minimum, long maximum) {
            if (maximum < minimum) {
                throw new IllegalArgumentException("Invalid integer range");
            }
            return new Property(PropertyType.INTEGER, minimum, maximum, 0, 0);
        }

        private static Property stringArray(int minItems, int maxItems, int minLength, int maxLength) {
            validateArrayBounds(minItems, maxItems);
            if (minLength < 0 || maxLength < minLength) {
                throw new IllegalArgumentException("Invalid string length range");
            }
            return new Property(PropertyType.STRING_ARRAY, minLength, maxLength, minItems, maxItems);
        }

        private static Property integerArray(int minItems, int maxItems, long minimum, long maximum) {
            validateArrayBounds(minItems, maxItems);
            if (maximum < minimum) {
                throw new IllegalArgumentException("Invalid integer range");
            }
            return new Property(PropertyType.INTEGER_ARRAY, minimum, maximum, minItems, maxItems);
        }

        private void validate(String name, Object value, List<String> errors) {
            if (type == PropertyType.STRING) {
                if (!(value instanceof String text)
                        || text.length() < minimum
                        || text.length() > maximum) {
                    errors.add(name + " must be a string with length " + minimum + ".." + maximum);
                }
                return;
            }
            if (type == PropertyType.STRING_ARRAY || type == PropertyType.INTEGER_ARRAY) {
                validateArray(name, value, errors);
                return;
            }
            if (!(value instanceof Number number)) {
                errors.add(name + " must be an integer");
                return;
            }
            double numeric = number.doubleValue();
            if (!Double.isFinite(numeric) || numeric != Math.rint(numeric)
                    || numeric < minimum || numeric > maximum) {
                errors.add(name + " must be an integer between " + minimum + " and " + maximum);
            }
        }

        private JsonObject toJson() {
            JsonObject json = new JsonObject();
            if (type == PropertyType.STRING) {
                json.addProperty("type", "string");
                json.addProperty("minLength", minimum);
                json.addProperty("maxLength", maximum);
            } else if (type == PropertyType.INTEGER) {
                json.addProperty("type", "integer");
                json.addProperty("minimum", minimum);
                json.addProperty("maximum", maximum);
            } else {
                json.addProperty("type", "array");
                json.addProperty("minItems", minItems);
                json.addProperty("maxItems", maxItems);
                JsonObject items = new JsonObject();
                if (type == PropertyType.STRING_ARRAY) {
                    items.addProperty("type", "string");
                    items.addProperty("minLength", minimum);
                    items.addProperty("maxLength", maximum);
                } else {
                    items.addProperty("type", "integer");
                    items.addProperty("minimum", minimum);
                    items.addProperty("maximum", maximum);
                }
                json.add("items", items);
            }
            return json;
        }

        private void validateArray(String name, Object value, List<String> errors) {
            if (!(value instanceof List<?> list) || list.size() < minItems || list.size() > maxItems) {
                errors.add(name + " must be an array with " + minItems + ".." + maxItems + " items");
                return;
            }
            for (Object item : list) {
                if (type == PropertyType.STRING_ARRAY) {
                    if (!(item instanceof String text)
                            || text.length() < minimum || text.length() > maximum) {
                        errors.add(name + " contains an invalid string item");
                        return;
                    }
                } else if (!(item instanceof Number number)
                        || !Double.isFinite(number.doubleValue())
                        || number.doubleValue() != Math.rint(number.doubleValue())
                        || number.doubleValue() < minimum
                        || number.doubleValue() > maximum) {
                    errors.add(name + " contains an invalid integer item");
                    return;
                }
            }
        }

        private static void validateArrayBounds(int minItems, int maxItems) {
            if (minItems < 0 || maxItems < minItems) {
                throw new IllegalArgumentException("Invalid array size range");
            }
        }
    }

    private record Constraint(String description, Predicate<Map<String, Object>> validator) {
    }
}
