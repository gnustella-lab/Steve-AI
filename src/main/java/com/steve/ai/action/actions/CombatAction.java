package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CombatAction extends BaseAction {
    private String targetType;
    private LivingEntity target;
    private int ticksRunning;
    private int ticksStuck;
    private final Set<UUID> seenTargets = new HashSet<>();
    private final Set<UUID> engagedTargets = new HashSet<>();
    private final Set<UUID> countedKills = new HashSet<>();
    private int targetsKilled;
    private double lastX, lastZ;
    private static final int MAX_TICKS = 600;
    private static final int TARGET_SEARCH_TIMEOUT = 100;
    private static final int STUCK_TIMEOUT = 80;
    private static final double ATTACK_RANGE = 3.5;

    public CombatAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        targetType = task.getStringParameter("target");
        ticksRunning = 0;
        ticksStuck = 0;
        targetsKilled = 0;
        seenTargets.clear();
        engagedTargets.clear();
        countedKills.clear();
        lastX = steve.getX();
        lastZ = steve.getZ();

        // Make sure we're not flying (in case we were building)
        steve.setFlying(false);

        steve.getSteveInventory().equipBestWeapon();
        steve.getSteveInventory().equipBestArmor();

        findTarget();

        if (target == null) {
            com.steve.ai.SteveMod.LOGGER.warn("Steve '{}' no targets nearby", steve.getSteveName());
        }
    }

    @Override
    protected void onTick() {
        ticksRunning++;

        if (ticksRunning > MAX_TICKS) {
            result = combatFailure(ActionResult.ERROR_TIMEOUT,
                "Combat timed out before a target was defeated", true, true);
            return;
        }

        // Resolve the previous target before searching again. A death after engagement is
        // deterministic progress; removal while still alive means the entity disappeared.
        if (target != null && (!target.isAlive() || target.isRemoved())) {
            UUID targetId = target.getUUID();
            if (!target.isAlive() && engagedTargets.contains(targetId) && countedKills.add(targetId)) {
                targetsKilled++;
                result = combatSuccess("Target defeated");
                return;
            }
            result = combatFailure(ActionResult.ERROR_ENTITY_GONE,
                "Combat target disappeared before a verified defeat", true, true);
            return;
        }

        // Re-search for targets periodically or if current target is absent.
        if (target == null) {
            if (ticksRunning % 20 == 0) {
                findTarget();
            }
            if (target == null) {
                if (seenTargets.isEmpty() && ticksRunning >= TARGET_SEARCH_TIMEOUT) {
                    result = combatFailure(ActionResult.ERROR_TARGET_NOT_FOUND,
                        "No compatible combat target was found nearby", true, true);
                }
                return;
            }
        }

        double distance = steve.distanceTo(target);

        steve.setSprinting(true);
        steve.getNavigation().moveTo(target, 2.5); // High speed multiplier for sprinting

        double currentX = steve.getX();
        double currentZ = steve.getZ();
        if (Math.abs(currentX - lastX) < 0.1 && Math.abs(currentZ - lastZ) < 0.1) {
            ticksStuck++;

            if (ticksStuck > STUCK_TIMEOUT && distance > ATTACK_RANGE) {
                result = combatFailure(ActionResult.ERROR_PATHING,
                    "Unable to reach combat target without teleporting", true, true);
                return;
            }
        } else {
            ticksStuck = 0;
        }
        lastX = currentX;
        lastZ = currentZ;

        if (distance <= ATTACK_RANGE) {
            // Attack 3 times per second (every 6-7 ticks)
            if (ticksRunning % 7 == 0) {
                UUID targetId = target.getUUID();
                if (steve.doHurtTarget(target)) {
                    engagedTargets.add(targetId);
                }
                steve.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);

                if (!target.isAlive() && engagedTargets.contains(targetId) && countedKills.add(targetId)) {
                    targetsKilled++;
                    result = combatSuccess("Target defeated");
                    return;
                }

                if (steve.getSteveInventory().isEquippedToolBroken()) {
                    steve.getSteveInventory().equipBestWeapon();
                }
            }
        }
    }

    @Override
    protected void onCancel() {
        target = null;
    }

    @Override
    protected void onFinish() {
        steve.getNavigation().stop();
        steve.setSprinting(false);
        steve.setFlying(false);
    }

    @Override
    public String getDescription() {
        return "Attack " + (targetType != null ? targetType : task.getStringParameter("target"));
    }

    private void findTarget() {
        AABB searchBox = steve.getBoundingBox().inflate(32.0);
        List<Entity> entities = steve.level().getEntities(steve, searchBox);

        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity living && isValidTarget(living)) {
                double distance = steve.distanceTo(living);
                if (distance < nearestDistance) {
                    nearest = living;
                    nearestDistance = distance;
                }
            }
        }

        target = nearest;
        if (target != null) {
            seenTargets.add(target.getUUID());
            com.steve.ai.SteveMod.LOGGER.info("Steve '{}' locked onto: {} at {}m",
                steve.getSteveName(), target.getType().toString(), (int)nearestDistance);
        }
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (!entity.isAlive() || entity.isRemoved()) {
            return false;
        }

        // Don't attack other Steves or players
        if (entity instanceof SteveEntity || entity instanceof net.minecraft.world.entity.player.Player) {
            return false;
        }

        if (targetType == null || targetType.isBlank()) {
            return false;
        }
        String targetLower = targetType.toLowerCase();

        // Match ANY hostile mob
        if (targetLower.contains("mob") || targetLower.contains("hostile") ||
            targetLower.contains("monster") || targetLower.equals("any")) {
            return entity instanceof Monster;
        }

        // Match specific entity type
        String entityTypeName = entity.getType().toString().toLowerCase();
        return entityTypeName.contains(targetLower);
    }

    private ActionResult combatSuccess(String message) {
        return ActionResult.success(message)
            .observation("actionType", "combat")
            .observation("combat", true)
            .observation("targetType", targetType == null ? "" : targetType)
            .observation("targetsSeen", seenTargets.size())
            .observation("targetsEngaged", engagedTargets.size())
            .observation("targetsKilled", targetsKilled)
            .observation("combatDurationTicks", ticksRunning)
            .build();
    }

    private ActionResult combatFailure(String code, String message, boolean retryable,
            boolean requiresReplanning) {
        return ActionResult.failure(code, message)
            .partialSuccess(!engagedTargets.isEmpty())
            .retryable(retryable)
            .requiresReplanning(requiresReplanning)
            .observation("actionType", "combat")
            .observation("combat", true)
            .observation("targetType", targetType == null ? "" : targetType)
            .observation("targetsSeen", seenTargets.size())
            .observation("targetsEngaged", engagedTargets.size())
            .observation("targetsKilled", targetsKilled)
            .observation("combatDurationTicks", ticksRunning)
            .build();
    }
}
