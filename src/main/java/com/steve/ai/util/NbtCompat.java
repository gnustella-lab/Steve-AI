package com.steve.ai.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.Optional;
import java.util.UUID;

/** NBT UUID helpers for the 26.3 tag API, where UUID convenience methods were removed. */
public final class NbtCompat {
    private NbtCompat() {
    }

    public static void putUUID(CompoundTag tag, String key, UUID value) {
        tag.putString(key, value.toString());
    }

    public static boolean hasUUID(CompoundTag tag, String key) {
        return tag.contains(key) && !tag.getStringOr(key, "").isEmpty();
    }

    public static Optional<UUID> getUUID(CompoundTag tag, String key) {
        try {
            return Optional.of(UUID.fromString(tag.getStringOr(key, "")));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static CompoundTag saveEntity(Entity entity) {
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    public static void loadEntity(Entity entity, CompoundTag tag) {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, null, tag));
    }
}
