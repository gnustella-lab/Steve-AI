package com.steve.ai.action;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredDataImmutabilityTest {

    @Test
    void taskCopiesNestedParametersAndBoundsPayloads() {
        List<String> tags = new ArrayList<>(List.of("ore"));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("tags", tags);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("nested", nested);
        parameters.put("long", "x".repeat(2_000));

        Task task = new Task("mine", parameters);
        tags.add("mutated");
        nested.put("other", "mutated");
        parameters.put("new", "mutated");

        Map<String, Object> snapshot = task.getParameters();
        assertEquals(2, snapshot.size());
        assertEquals(1, ((List<?>) ((Map<?, ?>) snapshot.get("nested")).get("tags")).size());
        assertEquals(512, ((String) snapshot.get("long")).length());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("x", 1));
        assertThrows(UnsupportedOperationException.class,
            () -> ((List<Object>) ((Map<?, ?>) snapshot.get("nested")).get("tags")).add("x"));
    }

    @Test
    void actionResultCopiesNestedObservationsAndDropsNullValues() {
        List<Object> evidence = new ArrayList<>(List.of("item", 4));
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("evidence", evidence);

        ActionResult result = ActionResult.failure(ActionResult.ERROR_RESOURCE, "missing")
            .observation("details", observation)
            .observation("nullable", null)
            .build();

        evidence.add("mutated");
        observation.put("other", "mutated");

        Map<String, Object> snapshot = result.getObservations();
        assertTrue(snapshot.containsKey("details"));
        assertEquals(2, ((List<?>) ((Map<?, ?>) snapshot.get("details")).get("evidence")).size());
        assertTrue(!snapshot.containsKey("nullable"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("x", 1));
        assertThrows(UnsupportedOperationException.class,
            () -> ((List<Object>) ((Map<?, ?>) snapshot.get("details")).get("evidence")).add("x"));
    }
}
