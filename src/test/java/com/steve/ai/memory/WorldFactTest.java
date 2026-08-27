package com.steve.ai.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldFactTest {
    @Test
    void worldFactClampsNonFiniteConfidenceAndBoundsDetails() {
        WorldFact fact = new WorldFact(
            WorldFact.Kind.HAZARD,
            "lava",
            "overworld",
            new BlockPos(10, 63, 20),
            42,
            Double.NaN,
            Long.MAX_VALUE,
            Map.of("detail", "x".repeat(400)));

        assertTrue(Double.isFinite(fact.confidence()));
        assertEquals(0.0, fact.confidence());
        assertEquals(7_200_000L, fact.ttlTicks());
        assertTrue(fact.isExpired(41), "a negative clock delta is unsafe for TTL facts");
        assertEquals(256, fact.details().get("detail").length());
        assertEquals(new BlockPos(10, 63, 20), fact.position());
        assertThrows(UnsupportedOperationException.class,
            () -> fact.details().put("new", "value"));
    }

    @Test
    void worldFactLoadPreservesPositionAndRejectsNonFiniteStoredConfidence() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", "RESOURCE");
        tag.putString("Key", "iron_ore");
        tag.putString("Dimension", "overworld");
        tag.putInt("X", 1);
        tag.putInt("Y", 2);
        tag.putInt("Z", 3);
        tag.putLong("LastSeen", 7);
        tag.putDouble("Confidence", Double.POSITIVE_INFINITY);
        tag.putLong("Ttl", 100);

        WorldFact loaded = WorldFact.load(tag);

        assertEquals(WorldFact.Kind.RESOURCE, loaded.kind());
        assertEquals(new BlockPos(1, 2, 3), loaded.position());
        assertFalse(Double.isInfinite(loaded.confidence()));
        assertEquals(0.0, loaded.confidence());
    }
}
