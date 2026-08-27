package com.steve.ai.autonomy;

import net.minecraft.nbt.CompoundTag;

/**
 * Bounded, versioned description of what a goal intends to achieve.
 *
 * <p>The intent contains only primitive/string planning hints. It is not an
 * execution object and never contains a task, action, or arbitrary payload.</p>
 */
public final class GoalIntent {
    public static final int DATA_VERSION = 1;
    public static final int MAX_DESCRIPTION_LENGTH = 512;
    public static final int MAX_KIND_LENGTH = 64;
    public static final int MAX_TARGET_LENGTH = 128;
    public static final int MAX_COUNTER = 1_000_000;

    private final String description;
    private final String kind;
    private final String target;
    private final int targetQuantity;

    public GoalIntent(String description) {
        this(description, "", "", 0);
    }

    public GoalIntent(String description, String kind, String target, int targetQuantity) {
        this.description = bounded(description, MAX_DESCRIPTION_LENGTH);
        this.kind = bounded(kind, MAX_KIND_LENGTH);
        this.target = bounded(target, MAX_TARGET_LENGTH);
        this.targetQuantity = bounded(targetQuantity);
    }

    public static GoalIntent of(String description) {
        return new GoalIntent(description);
    }

    public String getDescription() {
        return description;
    }

    public String description() {
        return description;
    }

    public String getKind() {
        return kind;
    }

    public String kind() {
        return kind;
    }

    public String getTarget() {
        return target;
    }

    public String target() {
        return target;
    }

    public int getTargetQuantity() {
        return targetQuantity;
    }

    public int targetQuantity() {
        return targetQuantity;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", DATA_VERSION);
        tag.putString("Description", description);
        tag.putString("Kind", kind);
        tag.putString("Target", target);
        tag.putInt("TargetQuantity", targetQuantity);
        return tag;
    }

    public static GoalIntent load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return new GoalIntent("");
        }
        return new GoalIntent(
            tag.getString("Description"),
            tag.getString("Kind"),
            tag.getString("Target"),
            tag.getInt("TargetQuantity"));
    }

    private static int bounded(int value) {
        return Math.max(0, Math.min(MAX_COUNTER, value));
    }

    private static String bounded(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
