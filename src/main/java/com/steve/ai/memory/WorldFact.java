package com.steve.ai.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, timestamped world fact used for relevant long-term recall. */
public final class WorldFact {
    public enum Kind {
        RESOURCE,
        RESOURCE_DEPLETED,
        CONTAINER,
        CRAFTING_STATION,
        FURNACE,
        BASE,
        LANDMARK,
        HAZARD,
        PROTECTED,
        PATH,
        FAILURE
    }

    private final Kind kind;
    private final String key;
    private final String dimension;
    private final BlockPos position;
    private final long lastSeenTick;
    private final double confidence;
    private final long ttlTicks;
    private final Map<String, String> details;

    public WorldFact(Kind kind, String key, String dimension, BlockPos position,
            long lastSeenTick, double confidence, long ttlTicks, Map<String, String> details) {
        this.kind = kind == null ? Kind.LANDMARK : kind;
        this.key = bounded(key, 128);
        this.dimension = bounded(dimension, 128);
        this.position = position;
        this.lastSeenTick = Math.max(0L, lastSeenTick);
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.ttlTicks = Math.max(0L, Math.min(ttlTicks, 7_200_000L));
        Map<String, String> copy = new LinkedHashMap<>();
        if (details != null) {
            details.entrySet().stream().limit(12).forEach(entry ->
                copy.put(bounded(entry.getKey(), 64), bounded(entry.getValue(), 256)));
        }
        this.details = Collections.unmodifiableMap(copy);
    }

    public static WorldFact resource(String resource, String dimension, BlockPos position,
            long tick, double confidence) {
        return new WorldFact(Kind.RESOURCE, resource, dimension, position, tick,
            confidence, 24_000L, Map.of());
    }

    public Kind kind() { return kind; }
    public String key() { return key; }
    public String dimension() { return dimension; }
    public BlockPos position() { return position; }
    public long lastSeenTick() { return lastSeenTick; }
    public double confidence() { return confidence; }
    public long ttlTicks() { return ttlTicks; }
    public Map<String, String> details() { return details; }

    public boolean isExpired(long now) {
        return ttlTicks > 0 && now - lastSeenTick > ttlTicks;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", kind.name());
        tag.putString("Key", key);
        tag.putString("Dimension", dimension);
        if (position != null) {
            tag.putInt("X", position.getX());
            tag.putInt("Y", position.getY());
            tag.putInt("Z", position.getZ());
        }
        tag.putLong("LastSeen", lastSeenTick);
        tag.putDouble("Confidence", confidence);
        tag.putLong("Ttl", ttlTicks);
        CompoundTag detailTag = new CompoundTag();
        details.forEach(detailTag::putString);
        tag.put("Details", detailTag);
        return tag;
    }

    public static WorldFact load(CompoundTag tag) {
        BlockPos position = tag.contains("X") && tag.contains("Y") && tag.contains("Z")
            ? new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")) : null;
        Kind kind;
        try {
            kind = Kind.valueOf(tag.getString("Kind"));
        } catch (IllegalArgumentException ignored) {
            kind = Kind.LANDMARK;
        }
        Map<String, String> details = new LinkedHashMap<>();
        if (tag.contains("Details", CompoundTag.TAG_COMPOUND)) {
            CompoundTag detailTag = tag.getCompound("Details");
            for (String key : detailTag.getAllKeys()) {
                details.put(key, detailTag.getString(key));
            }
        }
        return new WorldFact(kind, tag.getString("Key"), tag.getString("Dimension"), position,
            tag.getLong("LastSeen"), tag.contains("Confidence") ? tag.getDouble("Confidence") : 0.5,
            tag.contains("Ttl") ? tag.getLong("Ttl") : 0L, details);
    }

    private static String bounded(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
