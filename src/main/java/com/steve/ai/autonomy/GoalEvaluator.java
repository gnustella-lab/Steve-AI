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

        if (constraints.targetPosition() != null && position != null
                && position.closerThan(constraints.targetPosition(), constraints.positionTolerance() + 0.5)) {
            return new Evaluation(Status.COMPLETE, "Reached target position", true);
        }

        if (!constraints.targetItem().isBlank() && constraints.targetQuantity() > 0) {
            if (constraints.requireDelivery()) {
                boolean delivered = lastResult != null && lastResult.isSuccess()
                    && (Boolean.TRUE.equals(lastResult.getObservation("delivered"))
                        || lastResult.getMessage().toLowerCase(java.util.Locale.ROOT).contains("deliver"));
                return delivered
                    ? new Evaluation(Status.COMPLETE, "Delivery was observed", true)
                    : new Evaluation(Status.IN_PROGRESS, "Delivery has not been observed", true);
            }
            int count = inventoryCounts == null ? 0 : inventoryCounts.getOrDefault(constraints.targetItem(), 0);
            return count >= constraints.targetQuantity()
                ? new Evaluation(Status.COMPLETE, "Inventory quantity verified", true)
                : new Evaluation(Status.IN_PROGRESS,
                    "Need " + constraints.targetQuantity() + " " + constraints.targetItem() + ", have " + count, true);
        }

        if (lastResult != null && !lastResult.isSuccess()
                && ActionResult.ERROR_PROTECTED.equals(lastResult.getErrorCode())) {
            return new Evaluation(Status.IN_PROGRESS, "Protected approach requires another strategy", true);
        }
        if (planExhausted && lastResult != null && lastResult.isSuccess()) {
            return new Evaluation(Status.COMPLETE, "Last bounded plan completed successfully", false);
        }
        return new Evaluation(Status.IN_PROGRESS, "Goal condition is not verified yet", false);
    }

    private static Item parseItem(String name) {
        String normalized = name.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
        ResourceLocation location = ResourceLocation.tryParse(normalized);
        return location == null ? Items.AIR : BuiltInRegistries.ITEM.get(location);
    }
}
