package com.steve.ai.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Compact event record for recent goal execution, bounded by SteveMemory. */
public final class EpisodicMemoryEntry {
    private final UUID goalId;
    private final String action;
    private final String resultCode;
    private final String message;
    private final String dimension;
    private final BlockPos position;
    private final long tick;

    public EpisodicMemoryEntry(UUID goalId, String action, String resultCode, String message,
            String dimension, BlockPos position, long tick) {
        this.goalId = goalId;
        this.action = bounded(action, 128);
        this.resultCode = bounded(resultCode, 64);
        this.message = bounded(message, 256);
        this.dimension = bounded(dimension, 128);
        this.position = position;
        this.tick = Math.max(0L, tick);
    }

    public UUID goalId() { return goalId; }
    public String action() { return action; }
    public String resultCode() { return resultCode; }
    public String message() { return message; }
    public String dimension() { return dimension; }
    public BlockPos position() { return position; }
    public long tick() { return tick; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (goalId != null) tag.putUUID("GoalId", goalId);
        tag.putString("Action", action);
        tag.putString("ResultCode", resultCode);
        tag.putString("Message", message);
        tag.putString("Dimension", dimension);
        if (position != null) {
            tag.putInt("X", position.getX());
            tag.putInt("Y", position.getY());
            tag.putInt("Z", position.getZ());
        }
        tag.putLong("Tick", tick);
        return tag;
    }

    public static EpisodicMemoryEntry load(CompoundTag tag) {
        BlockPos position = tag.contains("X") && tag.contains("Y") && tag.contains("Z")
            ? new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")) : null;
        return new EpisodicMemoryEntry(
            tag.hasUUID("GoalId") ? tag.getUUID("GoalId") : null,
            tag.getString("Action"), tag.getString("ResultCode"), tag.getString("Message"),
            tag.getString("Dimension"), position, tag.getLong("Tick"));
    }

    private static String bounded(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
