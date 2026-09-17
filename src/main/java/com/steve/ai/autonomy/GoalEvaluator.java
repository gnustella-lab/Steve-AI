package com.steve.ai.autonomy;

import com.steve.ai.action.ActionResult;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Map;

/** Conservative deterministic verifier for common Minecraft goal conditions. */
public final class GoalEvaluator {
    public enum Status {
        COMPLETE,
        IN_PROGRESS,
        BLOCKED,
        UNKNOWN
    }

    public record Evaluation(Status status, String reason, boolean deterministic) { }

    public Evaluation evaluate(AgentGoal goal, SteveEntity steve, BlockPos position,
            ActionResult lastResult, boolean planExhausted) {
        if (steve == null) return evaluate(goal, Map.of(), position, lastResult, planExhausted);
        GoalConstraints constraints = goal == null ? GoalConstraints.empty() : goal.getConstraints();
        if (constraints.targetItem().isBlank()) {
            return evaluate(goal, Map.of(), position, lastResult, planExhausted);
        }
        Item item = parseItem(constraints.targetItem());
        if (item == Items.AIR) return new Evaluation(Status.UNKNOWN, "Target item is not registered", false);
        return evaluate(goal, Map.of(constraints.targetItem(), steve.getSteveInventory().count(item)),
            position, lastResult, planExhausted);
    }

    public Evaluation evaluate(AgentGoal goal, Map<String, Integer> inventoryCounts,
            BlockPos position, ActionResult lastResult, boolean planExhausted) {
        if (goal == null) return new Evaluation(Status.UNKNOWN, "No goal", false);
        GoalConstraints constraints = goal.getConstraints();
        if (Boolean.parseBoolean(String.valueOf(goal.getMetadata().get("localCompound")))) {
            int completedSteps = parseInt(String.valueOf(goal.getMetadata().get("localCompletedSteps")), 0);
            boolean done = completedSteps == 2;
            return new Evaluation(done ? Status.COMPLETE : Status.IN_PROGRESS,
                done ? "Both local commands verified in order" : "Local command sequence is not finished", true);
        }

        boolean requiresPosition = constraints.targetPosition() != null;
        boolean positionReached = requiresPosition && position != null
            && position.closerThan(constraints.targetPosition(), constraints.positionTolerance() + 0.5);

        if (!constraints.targetItem().isBlank() && constraints.targetQuantity() > 0) {
            boolean itemConditionMet;
            String successReason;
            if (constraints.requireDelivery()) {
                String deliveredItem = String.valueOf(lastResult == null ? ""
                    : lastResult.getObservation("deliveredItem"));
                int deliveredQuantity = observationInt(lastResult, "deliveredQuantity");
                String recipient = String.valueOf(lastResult == null ? ""
                    : lastResult.getObservation("recipientUuid"));
                boolean recipientMatches = constraints.targetPlayerUuid() == null
                    || constraints.targetPlayerUuid().toString().equals(recipient);
                itemConditionMet = lastResult != null && lastResult.isSuccess()
                    && Boolean.TRUE.equals(lastResult.getObservation("delivered"))
                    && normalizeId(deliveredItem).equals(normalizeId(constraints.targetItem()))
                    && deliveredQuantity >= constraints.targetQuantity() && recipientMatches;
                successReason = "Delivery quantity and recipient were observed";
            } else {
                int count = inventoryCounts == null ? 0
                    : inventoryCounts.getOrDefault(constraints.targetItem(), 0);
                itemConditionMet = count >= constraints.targetQuantity();
                successReason = "Inventory quantity verified";
                if (!itemConditionMet) {
                    return new Evaluation(Status.IN_PROGRESS,
                        "Need " + constraints.targetQuantity() + " " + constraints.targetItem()
                            + ", have " + count, true);
                }
            }
            if (!itemConditionMet) {
                return new Evaluation(Status.IN_PROGRESS, "Delivery has not been verified", true);
            }
            if (requiresPosition && !positionReached) {
                return new Evaluation(Status.IN_PROGRESS,
                    "Item condition is met but target position has not been reached", true);
            }
            return new Evaluation(Status.COMPLETE, successReason, true);
        }

        if (requiresPosition) {
            return positionReached
                ? new Evaluation(Status.COMPLETE, "Reached target position", true)
                : new Evaluation(Status.IN_PROGRESS, "Target position has not been reached", true);
        }

        if (lastResult != null && !lastResult.isSuccess()
                && ActionResult.ERROR_PROTECTED.equals(lastResult.getErrorCode())) {
            return new Evaluation(Status.IN_PROGRESS, "Protected approach requires another strategy", true);
        }
        if (isCombatGoal(goal)) {
            int targetsKilled = observationInt(lastResult, "targetsKilled");
            String observedType = String.valueOf(lastResult == null ? ""
                : lastResult.getObservation("targetType"));
            boolean targetMatches = constraints.targetBlock().isBlank()
                || normalizeId(observedType).endsWith(normalizeId(constraints.targetBlock()));
            int required = constraints.targetQuantity() > 0 ? constraints.targetQuantity() : 1;
            int verifiedTotal = Math.max(targetsKilled, goal.getProgress().getCompletedUnits());
            if (lastResult != null && lastResult.isSuccess()
                    && "combat".equals(lastResult.getObservation("actionType"))
                    && targetMatches && verifiedTotal >= required) {
                return new Evaluation(Status.COMPLETE,
                    "Combat target defeats verified: " + verifiedTotal + "/" + required, true);
            }
            return new Evaluation(Status.IN_PROGRESS,
                "Combat progress verified: " + verifiedTotal + "/" + required, true);
        }
        if (isBuildGoal(goal)) {
            if (lastResult != null && lastResult.isSuccess()
                    && "build".equals(lastResult.getObservation("actionType"))) {
                return new Evaluation(Status.COMPLETE, "Requested structure was built", true);
            }
            return new Evaluation(Status.IN_PROGRESS, "Structure has not been built yet", true);
        }
        return new Evaluation(Status.IN_PROGRESS, "Goal condition is not verified yet", false);
    }

    private static boolean isBuildGoal(AgentGoal goal) {
        if (goal == null || goal.getDescription() == null) return false;
        String description = LocalGoalPlanner.normalize(goal.getDescription());
        return description.matches(".*\\b(build|construir|construa|monte|montar)\\b.*"
            + "\\b(house|home|casa|castle|castelo|tower|torre|barn|celeiro|shed|galpao|"
            + "wall|muro|platform|plataforma|hut|cabana)\\b.*");
    }

    private static boolean isCombatGoal(AgentGoal goal) {
        if (goal == null || goal.getDescription() == null) return false;
        String description = goal.getDescription().toLowerCase(java.util.Locale.ROOT);
        return description.matches(".*\\b(attack|fight|kill|defeat)\\b.*");
    }

    private static int observationInt(ActionResult result, String key) {
        if (result == null) return 0;
        Object value = result.getObservation(key);
        return value instanceof Number number ? Math.max(0, number.intValue()) : 0;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String normalizeId(String value) {
        if (value == null) return "";
        String normalized = value.toLowerCase(java.util.Locale.ROOT).trim().replace(' ', '_');
        int namespace = normalized.indexOf(':');
        if (namespace >= 0) normalized = normalized.substring(namespace + 1);
        if (normalized.endsWith("s") && !normalized.endsWith("ss")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static Item parseItem(String name) {
        String normalized = name.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
        ResourceLocation location = ResourceLocation.tryParse(normalized);
        return location == null ? Items.AIR : BuiltInRegistries.ITEM.get(location);
    }
}
