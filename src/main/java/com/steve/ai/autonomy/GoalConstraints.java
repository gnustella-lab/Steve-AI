package com.steve.ai.autonomy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable, bounded conditions used by deterministic goal verification. */
public record GoalConstraints(
    String targetItem,
    int targetQuantity,
    String targetBlock,
    UUID targetPlayerUuid,
    BlockPos targetPosition,
    int positionTolerance,
    boolean allowExploration,
    boolean requireDelivery
) {
    public GoalConstraints {
        targetItem = bounded(targetItem, 128);
        targetBlock = bounded(targetBlock, 128);
        targetQuantity = Math.max(0, Math.min(targetQuantity, 2_048));
        positionTolerance = Math.max(0, Math.min(positionTolerance, 64));
    }

    public static GoalConstraints empty() {
        return new GoalConstraints("", 0, "", null, null, 2, true, false);
    }

    public static GoalConstraints forItem(String item, int quantity) {
        return new GoalConstraints(item, quantity, "", null, null, 2, true, false);
    }

    /** Extracts only deterministic quantity/item hints; the LLM still plans how to obtain them. */
    public static GoalConstraints fromDescription(String description) {
        String text = description == null ? "" : description.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_:\\- ]", " ").replaceAll("\\s+", " ").trim();
        Pattern pattern = Pattern.compile("\\b(?:get|gather|collect|obtain|bring|make|craft|mine)"
            + "(?:\\s+me)?\\s+(?:(\\d+)\\s+)?([a-z0-9_:-]+(?:\\s+[a-z0-9_:-]+)?)");
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return empty();
        int quantity = matcher.group(1) == null ? 1 : parseQuantity(matcher.group(1));
        String item = normalizeItem(matcher.group(2));
        return item.isBlank() ? empty() : forItem(item, quantity);
    }

    public GoalConstraints withDelivery(UUID playerUuid) {
        return new GoalConstraints(targetItem, targetQuantity, targetBlock, playerUuid,
            targetPosition, positionTolerance, allowExploration, true);
    }

    public GoalConstraints withTargetBlock(String block) {
        return new GoalConstraints(targetItem, targetQuantity, block, targetPlayerUuid,
            targetPosition, positionTolerance, allowExploration, requireDelivery);
    }

    public GoalConstraints withTargetPosition(BlockPos position, int tolerance) {
        return new GoalConstraints(targetItem, targetQuantity, targetBlock, targetPlayerUuid,
            position, tolerance, allowExploration, requireDelivery);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("TargetItem", targetItem);
        tag.putInt("TargetQuantity", targetQuantity);
        tag.putString("TargetBlock", targetBlock);
        if (targetPlayerUuid != null) {
            tag.putUUID("TargetPlayer", targetPlayerUuid);
        }
        if (targetPosition != null) {
            tag.putInt("TargetX", targetPosition.getX());
            tag.putInt("TargetY", targetPosition.getY());
            tag.putInt("TargetZ", targetPosition.getZ());
        }
        tag.putInt("PositionTolerance", positionTolerance);
        tag.putBoolean("AllowExploration", allowExploration);
        tag.putBoolean("RequireDelivery", requireDelivery);
        return tag;
    }

    public static GoalConstraints load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return empty();
        }
        BlockPos position = null;
        if (tag.contains("TargetX") && tag.contains("TargetY") && tag.contains("TargetZ")) {
            position = new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        }
        return new GoalConstraints(
            tag.getString("TargetItem"),
            tag.getInt("TargetQuantity"),
            tag.getString("TargetBlock"),
            tag.hasUUID("TargetPlayer") ? tag.getUUID("TargetPlayer") : null,
            position,
            tag.contains("PositionTolerance") ? tag.getInt("PositionTolerance") : 2,
            !tag.contains("AllowExploration") || tag.getBoolean("AllowExploration"),
            tag.getBoolean("RequireDelivery"));
    }

    private static String bounded(String value, int max) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private static int parseQuantity(String value) {
        try {
            return Math.max(1, Math.min(2_048, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String normalizeItem(String value) {
        String normalized = value == null ? "" : value.trim().replace(' ', '_');
        if (normalized.endsWith("_ingots")) return normalized.substring(0, normalized.length() - 1);
        if (normalized.endsWith("_logs")) return normalized.substring(0, normalized.length() - 1);
        if (normalized.endsWith("_diamonds")) return normalized.substring(0, normalized.length() - 1);
        if (normalized.endsWith("_ores")) return normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
