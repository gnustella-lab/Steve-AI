package com.steve.ai.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleEventBusTest {

    @Test
    void notifiesConcreteAndSupertypeSubscribersInPriorityOrder() {
        SimpleEventBus bus = new SimpleEventBus();
        List<String> calls = new ArrayList<>();

        bus.subscribe(Object.class, ignored -> calls.add("object"), 10);
        bus.subscribe(TestEvent.class, ignored -> calls.add("concrete"), 20);
        bus.subscribe(Marker.class, ignored -> calls.add("interface"), 5);

        bus.publish(new TestEvent());
        bus.shutdown();

        assertEquals(List.of("concrete", "object", "interface"), calls);
    }

    private interface Marker {
    }

    private static final class TestEvent implements Marker {
    }
}
