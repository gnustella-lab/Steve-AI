package com.steve.ai.gametest;

import com.steve.ai.SteveMod;
import com.steve.ai.action.Task;
import com.steve.ai.action.ActionResult;
import com.steve.ai.action.CollaborativeBuildManager;
import com.steve.ai.action.actions.BuildStructureAction;
import com.steve.ai.action.actions.CombatAction;
import com.steve.ai.action.actions.MineBlockAction;
import com.steve.ai.autonomy.AutonomyController;
import com.steve.ai.autonomy.AgentGoal;
import com.steve.ai.autonomy.GoalOrigin;
import com.steve.ai.autonomy.GoalPriority;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.entity.SteveManager;
import com.steve.ai.llm.ResponseParser;
import com.steve.ai.planning.Plan;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.security.ActionPermission;
import com.steve.ai.security.AgentCapability;
import com.steve.ai.security.MobilityPolicy;
import com.steve.ai.security.PermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(SteveMod.MODID)
@PrefixGameTestTemplate(false)
public final class SteveGameTests {
    private SteveGameTests() {
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void dedicatedServerLoadsCoreRuntime(GameTestHelper helper) {
        helper.assertTrue(SteveMod.getServiceContainer() != null,
            "The shared service container was not initialized");
        helper.assertTrue(ActionRegistry.getInstance().hasAction("mine"),
            "Core action plugin did not register mining");
        helper.assertTrue(ActionRegistry.getInstance().hasAction("build"),
            "Core action plugin did not register building");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty")
    public static void inventoryAndOwnershipPersistInEntityNbt(GameTestHelper helper) {
        SteveEntity original = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        UUID ownerUuid = UUID.randomUUID();
        original.setOwnerUuid(ownerUuid);
        original.getSteveInventory().insert(new ItemStack(Items.OAK_LOG, 32));
        original.getSteveInventory().setMainHandItem(new ItemStack(Items.IRON_PICKAXE));
        original.syncEquipmentFromInventory();
        CompoundTag entityTag = new CompoundTag();
        original.saveWithoutId(entityTag);

        SteveEntity restored = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        restored.load(entityTag);

        helper.assertTrue(restored.getSteveInventory().count(Items.OAK_LOG) == 32,
            "Inventory stack was not restored from entity NBT");
        helper.assertTrue(ownerUuid.equals(restored.getOwnerUuid()),
            "Owner UUID was not restored from entity NBT");
        helper.assertTrue(restored.getMainHandItem().is(Items.IRON_PICKAXE),
            "Persisted equipment must be restored into the vanilla entity hand");
        restored.syncEquipmentToInventory();
        helper.assertTrue(restored.getSteveInventory().getMainHandItem().is(Items.IRON_PICKAXE),
            "The first reverse sync must not erase persisted equipment");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void damageAndTransientInvulnerabilityFollowRuntimePolicy(GameTestHelper helper) {
        SteveEntity normal = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        normal.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(normal);
        float normalHealth = normal.getHealth();
        boolean damaged = normal.hurt(helper.getLevel().damageSources().generic(), 4.0F);
        helper.assertTrue(damaged && normal.getHealth() < normalHealth,
            "Steve must receive ordinary survival damage");

        SteveEntity transientlyProtected = new SteveEntity(
            SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        transientlyProtected.moveTo(helper.absolutePos(new BlockPos(2, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(transientlyProtected);
        transientlyProtected.setInvulnerableBuilding(true);
        float protectedHealth = transientlyProtected.getHealth();
        helper.assertTrue(!transientlyProtected.hurt(
                helper.getLevel().damageSources().generic(), 4.0F)
                && transientlyProtected.getHealth() == protectedHealth,
            "Authorized transient invulnerability must block ordinary damage");
        transientlyProtected.setInvulnerableBuilding(false);
        transientlyProtected.invulnerableTime = 0;
        helper.assertTrue(transientlyProtected.hurt(
                helper.getLevel().damageSources().generic(), 4.0F)
                && transientlyProtected.getHealth() < protectedHealth,
            "Disabling transient invulnerability must restore damage");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void transientInvulnerabilityDoesNotSurviveEntityReload(GameTestHelper helper) {
        SteveEntity original = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        original.setInvulnerableBuilding(true);
        original.setInvulnerable(true); // Simulate a save produced by the old implementation.
        CompoundTag entityTag = new CompoundTag();
        original.saveWithoutId(entityTag);

        SteveEntity restored = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        restored.load(entityTag);

        helper.assertTrue(!restored.isBuildingInvulnerable(),
            "Transient build invulnerability must not be restored from NBT");
        helper.assertTrue(!restored.isInvulnerableTo(helper.getLevel().damageSources().generic()),
            "Legacy vanilla Invulnerable flag must be cleared on reload");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void combatWithoutTargetFailsInsteadOfReportingSuccess(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(steve);
        CombatAction combat = new CombatAction(steve,
            new Task("combat", Map.of("target", "creeper")));
        combat.start();
        for (int tick = 0; tick < 105 && !combat.isComplete(); tick++) {
            combat.tick();
        }

        ActionResult result = combat.getResult();
        helper.assertTrue(result != null && !result.isSuccess(),
            "Combat without a target must not report success");
        helper.assertTrue(ActionResult.ERROR_TARGET_NOT_FOUND.equals(result.getErrorCode()),
            "Missing target must produce target_not_found, got " + result);
        helper.assertTrue(Integer.valueOf(0).equals(result.getObservation("targetsKilled")),
            "Missing target must report zero kills");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void combatSucceedsOnlyAfterObservedDefeat(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(steve);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "Could not create combat target");
        zombie.moveTo(helper.absolutePos(new BlockPos(2, 2, 1)), 0.0F, 0.0F);
        zombie.setHealth(1.0F);
        helper.getLevel().addFreshEntity(zombie);

        CombatAction combat = new CombatAction(steve,
            new Task("combat", Map.of("target", "zombie")));
        combat.start();
        for (int tick = 0; tick < 20 && !combat.isComplete(); tick++) combat.tick();

        ActionResult result = combat.getResult();
        helper.assertTrue(result != null && result.isSuccess(),
            "Combat should succeed after a verified target defeat");
        helper.assertTrue(Integer.valueOf(1).equals(result.getObservation("targetsKilled")),
            "Successful combat must report one verified kill");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void combatTargetRemovedBeforeEngagementIsEntityGone(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(steve);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "Could not create combat target");
        zombie.moveTo(helper.absolutePos(new BlockPos(2, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(zombie);

        CombatAction combat = new CombatAction(steve,
            new Task("combat", Map.of("target", "zombie")));
        combat.start();
        zombie.discard();
        combat.tick();

        helper.assertTrue(ActionResult.ERROR_ENTITY_GONE.equals(combat.getResult().getErrorCode()),
            "Removed target before engagement must be reported as entity_gone");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void combatPartialEngagementTimesOutWithoutFalseSuccess(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(steve);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "Could not create combat target");
        zombie.moveTo(helper.absolutePos(new BlockPos(2, 2, 1)), 0.0F, 0.0F);
        zombie.setHealth(zombie.getMaxHealth());
        helper.getLevel().addFreshEntity(zombie);

        CombatAction combat = new CombatAction(steve,
            new Task("combat", Map.of("target", "zombie")));
        combat.start();
        for (int tick = 0; tick < 8 && !combat.isComplete(); tick++) combat.tick();
        zombie.setInvulnerable(true);
        for (int tick = 0; tick < 610 && !combat.isComplete(); tick++) combat.tick();

        ActionResult result = combat.getResult();
        helper.assertTrue(result != null && !result.isSuccess()
                && ActionResult.ERROR_TIMEOUT.equals(result.getErrorCode()),
            "Partial combat must time out as failure when no target is defeated");
        helper.assertTrue(result.isPartialSuccess(),
            "A real engagement before timeout should be marked partial progress");
        helper.assertTrue(Integer.valueOf(0).equals(result.getObservation("targetsKilled")),
            "Timeout must not invent a defeated target");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void combatCancellationRestoresTransientState(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(steve);
        CombatAction combat = new CombatAction(steve,
            new Task("combat", Map.of("target", "zombie")));
        combat.start();
        steve.setInvulnerableBuilding(true); // Simulate a transient scope owned elsewhere.
        combat.cancel();

        helper.assertTrue(ActionResult.ERROR_CANCELLED.equals(combat.getResult().getErrorCode()),
            "Cancelled combat must have an explicit cancelled result");
        helper.assertTrue(steve.isBuildingInvulnerable() && !steve.isFlying(),
            "Combat must not clear an invulnerability scope it does not own");
        steve.setInvulnerableBuilding(false);
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void buildCancellationRestoresTransientState(GameTestHelper helper) {
        CollaborativeBuildManager.clearAllBuilds();
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(steve);
        BuildStructureAction build = new BuildStructureAction(steve,
            new Task("build", Map.of("structure", "house", "width", 3, "height", 3, "depth", 3)));
        build.start();
        steve.setInvulnerableBuilding(true);
        build.cancel();

        helper.assertTrue(!steve.isBuildingInvulnerable() && !steve.isFlying(),
            "Build cancellation must always restore transient state");
        CollaborativeBuildManager.clearAllBuilds();
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void mobilityCapabilitiesFailClosedAndProtectedTeleportIsDenied(GameTestHelper helper) {
        BlockPos destination = null;
        try {
            SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
            steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
            helper.getLevel().addFreshEntity(steve);

            // Capabilities alone never override the global fail-closed configuration.
            steve.getAccessProfile().grantCapability(AgentCapability.ALLOW_FLIGHT);
            steve.setFlying(true);
            helper.assertTrue(!steve.isFlying(),
                "A profile capability must not bypass the global flight gate");

            steve.getAccessProfile().grantCapability(AgentCapability.ALLOW_TELEPORT);
            BlockPos relativeDestination = new BlockPos(3, 2, 3);
            helper.setBlock(relativeDestination.below(), Blocks.STONE);
            helper.setBlock(relativeDestination, Blocks.AIR);
            helper.setBlock(relativeDestination.above(), Blocks.AIR);
            destination = helper.absolutePos(relativeDestination);
            PermissionManager.getInstance().protectRegion(helper.getLevel(), destination, destination);
            BlockPos before = steve.blockPosition();

            helper.assertTrue(!MobilityPolicy.isValidTeleportDestination(steve, destination),
                "Protected region must fail destination validation independently of capability/config");
            helper.assertTrue(!steve.teleportSafely(destination),
                "Fail-closed teleport policy must deny a protected destination");
            helper.assertTrue(before.equals(steve.blockPosition()),
                "Denied teleport must not move Steve");
        } finally {
            if (destination != null) {
                PermissionManager.getInstance().unprotectRegion(helper.getLevel(), destination, destination);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void flightStateDoesNotSurviveReloadEvenWhenCapabilityPersists(GameTestHelper helper) {
        SteveEntity original = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        original.getAccessProfile().grantCapability(AgentCapability.ALLOW_FLIGHT);
        original.setNoGravity(true); // Simulate a transient/legacy flight state in the entity NBT.
        CompoundTag tag = new CompoundTag();
        original.saveWithoutId(tag);

        SteveEntity restored = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        restored.load(tag);
        helper.assertTrue(restored.getAccessProfile().hasCapability(AgentCapability.ALLOW_FLIGHT),
            "Persistent capability should survive reload");
        helper.assertTrue(!restored.isFlying() && !restored.isNoGravity(),
            "Transient flying/no-gravity state must never survive reload");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void persistedPlanIsCheckpointOnlyAndNeverResumed(GameTestHelper helper) {
        SteveEntity original = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        AgentGoal goal = AgentGoal.create("Place one stone", GoalOrigin.USER, GoalPriority.USER, null, 1L);
        Plan plan = new Plan(goal.getId(), goal.getDescription(), null, original.getUUID(), 3, 3, 3, 0, 2L);
        plan.loadHorizon(java.util.List.of(new Task("place", Map.of(
            "block", "stone", "x", 10, "y", 64, "z", 10))), "checkpoint", "runtime", 3L);
        original.getMemory().setActivePlan(plan);
        CompoundTag tag = new CompoundTag();
        original.saveWithoutId(tag);

        SteveEntity restored = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        restored.load(tag);
        helper.assertTrue(restored.getMemory().getActivePlan() != null,
            "Test precondition: plan checkpoint should deserialize for diagnostics");
        restored.getAutonomyController().tick();

        helper.assertTrue(restored.getMemory().getActivePlan() == null,
            "Restart must clear the persisted plan instead of resuming its mutation queue");
        helper.assertTrue(restored.getMemory().getRecentActions(5).stream()
                .anyMatch(action -> action.contains("Restart checkpoint (not resumed)")),
            "Discarded checkpoint should remain available as diagnostic context");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty")
    public static void protectedRegionKeysCanonicalizeCornerOrder(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos max = helper.absolutePos(new BlockPos(3, 3, 3));
        PermissionManager permissions = PermissionManager.getInstance();
        permissions.protectRegion(helper.getLevel(), max, min);
        boolean protectedBefore = permissions.isProtected(helper.getLevel(), min);
        boolean removed = permissions.unprotectRegion(helper.getLevel(), min, max);
        boolean protectedAfter = permissions.isProtected(helper.getLevel(), min);
        if (!removed) permissions.unprotectRegion(helper.getLevel(), max, min);

        helper.assertTrue(protectedBefore, "Reversed corners must still protect the canonical region");
        helper.assertTrue(removed && !protectedAfter,
            "Unprotect must work regardless of the order of the two corners");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty")
    public static void minedBlockLootMovesToSteveInventory(GameTestHelper helper) {
        BlockPos relativeBlock = new BlockPos(1, 1, 1);
        helper.setBlock(relativeBlock, Blocks.STONE);
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("Phase1Miner");
        steve.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        steve.setPersistenceRequired();
        helper.getLevel().addFreshEntity(steve);

        boolean broken = steve.breakBlockIntoInventory(helper.absolutePos(relativeBlock));

        helper.assertTrue(broken, "Steve did not break the test block");
        helper.assertBlockNotPresent(Blocks.STONE, relativeBlock);
        helper.assertTrue(steve.getSteveInventory().count(Items.COBBLESTONE) == 1,
            "Mined cobblestone was not committed to Steve inventory");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty")
    public static void miningApiRejectsRemoteBlockMutation(GameTestHelper helper) {
        BlockPos remoteRelative = new BlockPos(8, 1, 1);
        helper.setBlock(remoteRelative, Blocks.STONE);
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 1, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(steve);

        boolean broken = steve.breakBlockIntoInventory(helper.absolutePos(remoteRelative));

        helper.assertTrue(!broken, "Steve must not break blocks outside embodied reach");
        helper.assertBlockPresent(Blocks.STONE, remoteRelative);
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 100)
    public static void groundItemMovesToSteveInventory(GameTestHelper helper) {
        BlockPos relativePosition = new BlockPos(1, 1, 1);
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("Phase1Collector");
        steve.moveTo(helper.absolutePos(relativePosition), 0.0F, 0.0F);
        steve.setPersistenceRequired();
        helper.getLevel().addFreshEntity(steve);
        ItemEntity itemEntity = helper.spawnItem(Items.BREAD, relativePosition);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(steve.getSteveInventory().count(Items.BREAD) == 1,
                "Ground item was not committed to Steve inventory");
            helper.assertTrue(itemEntity.isRemoved(), "Picked-up item entity still exists");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void removingSteveDropsInventoryExactlyOnce(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("Phase1Removal");
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        steve.getSteveInventory().insert(new ItemStack(Items.BREAD, 3));
        helper.getLevel().addFreshEntity(steve);
        SteveManager manager = new SteveManager();
        manager.registerSteve(steve);

        helper.assertTrue(manager.removeSteve("Phase1Removal"), "First removal was rejected");
        helper.assertTrue(!manager.removeSteve("Phase1Removal"), "Repeated removal unexpectedly succeeded");
        AABB area = AABB.ofSize(steve.position(), 4.0, 4.0, 4.0);
        int droppedBread = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
            .filter(item -> item.getItem().is(Items.BREAD))
            .mapToInt(item -> item.getItem().getCount())
            .sum();
        helper.assertTrue(droppedBread == 3, "Removal did not drop exactly three bread items");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void cancelledMiningRestoresPreviousMainHandItem(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("Phase1ToolRestore");
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        steve.getSteveInventory().insert(new ItemStack(Items.IRON_PICKAXE));
        steve.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        steve.syncEquipmentToInventory();
        helper.getLevel().addFreshEntity(steve);
        MineBlockAction action = new MineBlockAction(
            steve,
            new Task("mine", Map.of("block", "stone", "quantity", 1)));

        action.start();
        helper.assertTrue(steve.getMainHandItem().is(Items.IRON_PICKAXE),
            "Mining did not equip its temporary tool");
        action.cancel();
        action.cancel();

        helper.assertTrue(steve.getMainHandItem().is(Items.DIAMOND_SWORD),
            "Mining cancellation did not restore the previous main-hand item");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 200)
    public static void placingBlockInSurvivalConsumesInventoryMaterial(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.AIR.defaultBlockState());
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("Phase2Placer");
        steve.moveTo(helper.absolutePos(relative).above(), 0.0F, 0.0F);
        steve.setPersistenceRequired();
        steve.getSteveInventory().insert(new ItemStack(Items.OAK_PLANKS, 3));
        helper.getLevel().addFreshEntity(steve);

        var placeAction = new com.steve.ai.action.actions.PlaceBlockAction(
            steve,
            new Task("place", Map.of(
                "block", "oak_planks",
                "x", helper.absolutePos(relative).getX(),
                "y", helper.absolutePos(relative).getY(),
                "z", helper.absolutePos(relative).getZ())));
        placeAction.start();

        tickUntilComplete(helper, placeAction, () -> {
            helper.assertTrue(steve.getSteveInventory().count(Items.OAK_PLANKS) == 2,
                "Survival placement should consume exactly one plank");
            helper.assertTrue(steve.level().getBlockState(helper.absolutePos(relative))
                    .is(Blocks.OAK_PLANKS),
                "Plank was not actually placed in the world");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 200)
    public static void refusesToPlaceWhenInventoryIsEmpty(GameTestHelper helper) {
        BlockPos slot = new BlockPos(1, 1, 1);
        helper.setBlock(slot, Blocks.AIR.defaultBlockState());
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("Phase2BoundedPlacer");
        steve.moveTo(helper.absolutePos(slot.above()), 0.0F, 0.0F);
        steve.setPersistenceRequired();
        steve.getSteveInventory().insert(new ItemStack(Items.COBBLESTONE, 1));
        helper.getLevel().addFreshEntity(steve);

        var firstPlace = new com.steve.ai.action.actions.PlaceBlockAction(
            steve,
            new Task("place", Map.of(
                "block", "cobblestone",
                "x", helper.absolutePos(slot).getX(),
                "y", helper.absolutePos(slot).getY(),
                "z", helper.absolutePos(slot).getZ())));
        firstPlace.start();
        tickUntilComplete(helper, firstPlace, () -> {
            helper.assertTrue(steve.getSteveInventory().count(Items.COBBLESTONE) == 0,
                "Inventory should be empty after consuming the single cobblestone");
            BlockPos alt = new BlockPos(2, 1, 1);
            helper.setBlock(alt, Blocks.AIR.defaultBlockState());
            var secondPlace = new com.steve.ai.action.actions.PlaceBlockAction(
                steve,
                new Task("place", Map.of(
                    "block", "cobblestone",
                    "x", helper.absolutePos(alt).getX(),
                    "y", helper.absolutePos(alt).getY(),
                    "z", helper.absolutePos(alt).getZ())));
            secondPlace.start();
            tickUntilComplete(helper, secondPlace, () -> {
                helper.assertTrue(
                    secondPlace.getResult() != null
                        && !secondPlace.getResult().isSuccess(),
                    "Steve should refuse to place when out of material");
                helper.assertTrue(
                    !steve.level().getBlockState(helper.absolutePos(alt)).is(Blocks.COBBLESTONE),
                    "Second placement should not have happened");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 200)
    public static void craftingConsumesIngredientsAndProducesResult(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("Phase2Crafter");
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        steve.setPersistenceRequired();
        helper.getLevel().addFreshEntity(steve);

        steve.getSteveInventory().insert(new ItemStack(Items.OAK_LOG, 4));

        var craftAction = new com.steve.ai.action.actions.CraftItemAction(
            steve,
            new Task("craft", Map.of("item", "oak_planks", "quantity", 8)));
        craftAction.start();

        tickUntilComplete(helper, craftAction, () -> {
            helper.assertTrue(
                craftAction.getResult() != null && craftAction.getResult().isSuccess(),
                "Crafting should succeed when ingredients are available: "
                    + (craftAction.getResult() == null ? "no result" : craftAction.getResult().toString()));
            helper.assertTrue(
                steve.getSteveInventory().count(Items.OAK_PLANKS) >= 8,
                "Crafting should produce at least eight oak planks, found "
                    + steve.getSteveInventory().count(Items.OAK_PLANKS)
                    + " result=" + craftAction.getResult());
            helper.assertTrue(
                steve.getSteveInventory().count(Items.OAK_LOG) == 2,
                "Crafting should consume exactly two oak logs");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 600)
    public static void smeltingConsumesInputAndProducesIngot(GameTestHelper helper) {
        BlockPos furnacePos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.FURNACE);
        if (!(helper.getLevel().getBlockEntity(furnacePos)
                instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace)) {
            helper.fail("Furnace block entity did not spawn");
            return;
        }
        furnace.setItem(1, new ItemStack(Items.COAL, 4));

        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("Phase2Smelter");
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        steve.setPersistenceRequired();
        steve.getSteveInventory().insert(new ItemStack(Items.RAW_IRON, 2));
        helper.getLevel().addFreshEntity(steve);

        var smeltAction = new com.steve.ai.action.actions.SmeltItemAction(
            steve,
            new Task("smelt", Map.of("item", "iron_ingot", "quantity", 2)));
        smeltAction.start();

        tickUntilComplete(helper, smeltAction, () -> {
            helper.assertTrue(
                steve.getSteveInventory().count(Items.IRON_INGOT) >= 2,
                "Smelting should produce two iron ingots in the Steve inventory: "
                    + (smeltAction.getResult() == null ? "no result" : smeltAction.getResult().toString()));
            helper.assertTrue(
                steve.getSteveInventory().count(Items.RAW_IRON) == 0,
                "Smelting should consume the raw iron inputs");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty")
    public static void smeltingDoesNotStealOrCountUnrelatedOutput(GameTestHelper helper) {
        BlockPos furnacePos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.FURNACE);
        if (!(helper.getLevel().getBlockEntity(furnacePos)
                instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace)) {
            helper.fail("Furnace block entity did not spawn");
            return;
        }
        furnace.setItem(2, new ItemStack(Items.DIAMOND));
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        steve.getSteveInventory().insert(new ItemStack(Items.RAW_IRON));
        steve.getSteveInventory().insert(new ItemStack(Items.COAL));
        helper.getLevel().addFreshEntity(steve);
        var smelt = new com.steve.ai.action.actions.SmeltItemAction(
            steve, new Task("smelt", Map.of("item", "iron_ingot", "quantity", 1)));

        smelt.start();
        smelt.tick();

        helper.assertTrue(smelt.isComplete() && !smelt.getResult().isSuccess(),
            "Unrelated furnace output must block the action instead of proving success");
        helper.assertTrue(furnace.getItem(2).is(Items.DIAMOND)
                && furnace.getItem(2).getCount() == 1,
            "The action must not remove another operation's furnace output");
        helper.assertTrue(steve.getSteveInventory().count(Items.IRON_INGOT) == 0,
            "Unrelated output must not be counted as the requested ingot");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 400)
    public static void depositingItemsMovesStacksToNearbyContainer(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("Phase2Depositor");
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        steve.setPersistenceRequired();
        steve.getSteveInventory().insert(new ItemStack(Items.IRON_INGOT, 8));
        helper.getLevel().addFreshEntity(steve);

        var deposit = new com.steve.ai.action.actions.DepositItemAction(
            steve,
            new Task("deposit_item", Map.of("item", "iron_ingot", "quantity", 5)));
        deposit.start();

        tickUntilComplete(helper, deposit, () -> {
            helper.assertTrue(deposit.getResult() != null && deposit.getResult().isSuccess(),
                "Deposit action failed: " + (deposit.getResult() == null
                    ? "no result" : deposit.getResult().getMessage()));
            helper.assertTrue(steve.getSteveInventory().count(Items.IRON_INGOT) == 3,
                "Steve should retain three ingots after depositing five");
            var chest = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
            int inChest = 0;
            if (chest instanceof net.minecraft.world.Container container) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (stack.is(Items.IRON_INGOT)) {
                        inChest += stack.getCount();
                    }
                }
            }
            helper.assertTrue(inChest == 5,
                "Chest should contain five deposited ingots, found " + inChest);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 100)
    public static void toolWearProgressesDurabilityDuringMining(GameTestHelper helper) {
        BlockPos stone = new BlockPos(1, 1, 1);
        helper.setBlock(stone, Blocks.STONE);
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("Phase2ToolWear");
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        steve.setPersistenceRequired();
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        int initialDamage = pickaxe.getDamageValue();
        steve.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        steve.syncEquipmentToInventory();
        helper.getLevel().addFreshEntity(steve);

        boolean broken = steve.breakBlockIntoInventory(helper.absolutePos(stone));
        helper.assertTrue(broken, "Steve did not break stone");
        ItemStack equipped = steve.getMainHandItem();
        helper.assertTrue(equipped.is(Items.IRON_PICKAXE),
            "Pickaxe should still be in main hand after one use");
        helper.assertTrue(equipped.getDamageValue() > initialDamage,
            "Pickaxe should have worn at least one durability point");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 160)
    public static void pausingWhilePlanningDiscardsLateResponse(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("AutonomyPause_" + UUID.randomUUID().toString().substring(0, 8));
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        steve.setPersistenceRequired();
        helper.getLevel().addFreshEntity(steve);

        CompletableFuture<ResponseParser.ParsedResponse> pending = new CompletableFuture<>();
        AutonomyController controller = steve.getAutonomyController();
        controller.setPlanner(context -> pending);
        controller.submitUserGoal("Pause before the plan arrives", null);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(controller.isPlanning(), "The fake planner should still be in flight");
            controller.pause();
            pending.complete(ResponseParser.parseAIResponse(
                "{\"decision\":\"act\",\"summary\":\"late\",\"goalStatus\":\"in_progress\","
                    + "\"tasks\":[{\"action\":\"inspect_inventory\",\"parameters\":{}}]}"));
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(controller.getState() == com.steve.ai.execution.AgentState.PAUSED,
                "A paused controller must remain paused after a late planner completion");
            helper.assertTrue(!steve.getActionExecutor().hasPendingAutonomousTasks(),
                "A late plan must not enqueue autonomous actions after pause");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 700)
    public static void autonomousIronGoalSmeltsAndVerifiesInventory(GameTestHelper helper) {
        BlockPos relativeFurnace = new BlockPos(1, 1, 1);
        BlockPos furnacePos = helper.absolutePos(relativeFurnace);
        helper.setBlock(relativeFurnace, Blocks.FURNACE);
        if (!(helper.getLevel().getBlockEntity(furnacePos)
                instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace)) {
            helper.fail("Furnace block entity did not spawn");
            return;
        }
        furnace.setItem(1, new ItemStack(Items.COAL, 16));

        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("AutonomousIron_" + UUID.randomUUID().toString().substring(0, 8));
        steve.moveTo(furnacePos.above(), 0.0F, 0.0F);
        steve.setPersistenceRequired();
        steve.getSteveInventory().insert(new ItemStack(Items.IRON_INGOT, 15));
        steve.getSteveInventory().insert(new ItemStack(Items.RAW_IRON, 1));
        helper.getLevel().addFreshEntity(steve);

        AtomicInteger plannerCalls = new AtomicInteger();
        AutonomyController controller = steve.getAutonomyController();
        controller.setPlanner(context -> {
            plannerCalls.incrementAndGet();
            String response = "{\"decision\":\"act\",\"summary\":\"Smelt the available iron\","
                + "\"goalStatus\":\"in_progress\",\"tasks\":[{\"action\":\"smelt\",\"parameters\":{"
                + "\"item\":\"iron_ingot\",\"quantity\":1}}]}";
            return CompletableFuture.completedFuture(ResponseParser.parseAIResponse(response));
        });
        controller.submitUserGoal("Get me 16 iron ingots", null);

        helper.runAfterDelay(500, () -> {
            helper.assertTrue(plannerCalls.get() >= 1,
                "The persistent goal should invoke the injected planner");
            helper.assertTrue(steve.getSteveInventory().count(Items.IRON_INGOT) >= 16,
                "The autonomous goal should verify sixteen iron ingots");
            helper.assertTrue(controller.getActiveGoal() == null,
                "The verified iron goal should return to idle");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void refusesToBreakProtectedBlock(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.STONE);
        BlockPos target = helper.absolutePos(relative);
        PermissionManager.getInstance().protectRegion(helper.getLevel(), target, target);
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(steve);
        helper.assertTrue(!steve.breakBlockIntoInventory(target),
            "Protected mining must fail closed");
        helper.assertBlockPresent(Blocks.STONE, relative);
        helper.assertTrue(steve.getSteveInventory().count(Items.COBBLESTONE) == 0,
            "Protected mining must not commit loot");
        PermissionManager.getInstance().unprotectRegion(helper.getLevel(), target, target);
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 200)
    public static void refusesToPlaceInProtectedRegion(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.AIR.defaultBlockState());
        BlockPos target = helper.absolutePos(relative);
        PermissionManager.getInstance().protectRegion(helper.getLevel(), target, target);
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(target.above(), 0.0F, 0.0F);
        steve.getSteveInventory().insert(new ItemStack(Items.OAK_PLANKS, 2));
        helper.getLevel().addFreshEntity(steve);
        var place = new com.steve.ai.action.actions.PlaceBlockAction(steve,
            new Task("place", Map.of(
                "block", "oak_planks",
                "x", target.getX(), "y", target.getY(), "z", target.getZ())));
        place.start();
        tickUntilComplete(helper, place, () -> {
            helper.assertTrue(place.getResult() != null
                    && ActionResult.ERROR_PROTECTED.equals(place.getResult().getErrorCode()),
                "Protected placement must fail without mutation");
            helper.assertTrue(helper.getLevel().getBlockState(target).isAir(),
                "Protected placement must not place a block");
            helper.assertTrue(steve.getSteveInventory().count(Items.OAK_PLANKS) == 2,
                "Failed protected placement must not consume materials");
            PermissionManager.getInstance().unprotectRegion(helper.getLevel(), target, target);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void miningOverflowDropsRemainderInsteadOfDeletingOrDuplicating(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.STONE);
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        ItemStack filler = new ItemStack(Items.DIRT, 64);
        while (steve.getSteveInventory().canInsert(filler.copy())) {
            steve.getSteveInventory().insert(filler.copy());
        }
        helper.getLevel().addFreshEntity(steve);

        boolean broken = steve.breakBlockIntoInventory(helper.absolutePos(relative));
        helper.assertTrue(broken, "Full inventory must not prevent the block from breaking");
        helper.assertTrue(steve.getSteveInventory().count(Items.COBBLESTONE) == 0,
            "Overflow cobblestone must not be forced into a full inventory");
        AABB area = AABB.ofSize(steve.position(), 6.0, 6.0, 6.0);
        int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
            .filter(item -> item.getItem().is(Items.COBBLESTONE))
            .mapToInt(item -> item.getItem().getCount())
            .sum();
        helper.assertTrue(dropped == 1, "Overflow cobblestone should drop exactly once");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void miningDoesNotDuplicateLootOnRepeatedBreak(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.STONE);
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        steve.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        helper.getLevel().addFreshEntity(steve);

        helper.assertTrue(steve.breakBlockIntoInventory(helper.absolutePos(relative)),
            "First break should succeed");
        helper.assertTrue(!steve.breakBlockIntoInventory(helper.absolutePos(relative)),
            "Second break of air must fail");
        helper.assertTrue(steve.getSteveInventory().count(Items.COBBLESTONE) == 1,
            "Repeated break must not duplicate loot");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void brokenToolIsRemovedWithoutDuplication(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.STONE);
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        ItemStack pickaxe = new ItemStack(Items.WOODEN_PICKAXE);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
        steve.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        steve.syncEquipmentToInventory();
        helper.getLevel().addFreshEntity(steve);

        helper.assertTrue(steve.breakBlockIntoInventory(helper.absolutePos(relative)),
            "The almost-broken pickaxe should still mine one block");
        helper.assertTrue(steve.getMainHandItem().isEmpty(),
            "Broken tool must leave the main hand empty");
        helper.assertTrue(steve.getSteveInventory().getMainHandItem().isEmpty(),
            "Broken tool must not remain duplicated in SteveInventory");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 80)
    public static void permissionRevokedDuringActionInterruptsWithoutWorldMutation(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 1, 2);
        helper.setBlock(relative, Blocks.AIR.defaultBlockState());
        BlockPos target = helper.absolutePos(relative);
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        steve.getSteveInventory().insert(new ItemStack(Items.OAK_PLANKS, 1));
        helper.getLevel().addFreshEntity(steve);

        Plan plan = new Plan(UUID.randomUUID(), "place plank", null, steve.getUUID(), 3, 3, 3, 0, 1L);
        plan.loadHorizon(java.util.List.of(new Task("place", Map.of(
            "block", "oak_planks",
            "x", target.getX(), "y", target.getY(), "z", target.getZ()))),
            "place", "runtime", 2L);
        steve.getActionExecutor().acceptAutonomousPlan(plan);
        for (int tick = 0; tick < 40
                && steve.getActionExecutor().getCurrentActionDescription().isBlank(); tick++) {
            steve.getActionExecutor().tick();
        }
        PermissionManager.getInstance().setPermission(steve.getUUID(), ActionPermission.NONE);
        try {
            steve.getActionExecutor().tick();
            var completion = steve.getActionExecutor().consumeCompletedAction();
            helper.assertTrue(completion != null
                    && ActionResult.ERROR_PERMISSION_DENIED.equals(completion.result().getErrorCode()),
                "Revoked permission must interrupt the running action");
            helper.assertTrue(helper.getLevel().getBlockState(target).isAir(),
                "Interrupted placement must not mutate the world");
        } finally {
            PermissionManager.getInstance().clearPermission(steve.getUUID());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 80)
    public static void pausedGoalRemainsPausedAfterReload(GameTestHelper helper) {
        SteveEntity original = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        original.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(original);
        AutonomyController controller = original.getAutonomyController();
        controller.setPlanner(context -> new CompletableFuture<>());
        controller.submitUserGoal("Keep gathering oak", null);
        controller.pause();

        CompoundTag tag = new CompoundTag();
        original.saveWithoutId(tag);
        SteveEntity restored = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        restored.load(tag);
        restored.getAutonomyController().tick();

        helper.assertTrue(restored.getAutonomyController().getState() == com.steve.ai.execution.AgentState.PAUSED,
            "A paused goal must remain paused after reload");
        helper.assertTrue(restored.getAutonomyController().getActiveGoal() != null,
            "The paused goal should still be the active restored objective");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 20)
    public static void blockedGoalIsNotAutoResumedAfterReload(GameTestHelper helper) {
        SteveEntity original = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        AgentGoal blocked = AgentGoal.create("Build inside spawn", GoalOrigin.USER, GoalPriority.USER, null, 1L);
        blocked.block("protected", 2L);
        original.getMemory().setActiveGoal(blocked);
        CompoundTag tag = new CompoundTag();
        original.saveWithoutId(tag);

        SteveEntity restored = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        restored.load(tag);
        restored.getAutonomyController().tick();

        helper.assertTrue(restored.getAutonomyController().getActiveGoal() == null,
            "A blocked goal must not become executable after reload");
        helper.assertTrue(restored.getAutonomyController().getState() != com.steve.ai.execution.AgentState.EXECUTING,
            "Blocked restoration must not resume mutation");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 80)
    public static void stopIsAbsoluteAndDoesNotResume(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(steve);
        AutonomyController controller = steve.getAutonomyController();
        controller.setPlanner(context -> new CompletableFuture<>());
        controller.submitUserGoal("Never finish this", null);
        controller.stop();
        for (int tick = 0; tick < 10; tick++) {
            controller.tick();
        }
        helper.assertTrue(controller.getActiveGoal() == null, "Stop must clear the active goal");
        helper.assertTrue(controller.getState() == com.steve.ai.execution.AgentState.IDLE,
            "Stop must return the executive to idle");
        helper.succeed();
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 240)
    public static void invalidLlmTaskNeverMutatesTheWorld(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 1, 1);
        helper.setBlock(relative, Blocks.STONE);
        BlockPos target = helper.absolutePos(relative);
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.moveTo(target.above(), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(steve);

        AutonomyController controller = steve.getAutonomyController();
        controller.setPlanner(context -> CompletableFuture.completedFuture(ResponseParser.parseAIResponse(
            "{\"decision\":\"act\",\"summary\":\"invalid\",\"goalStatus\":\"in_progress\","
                + "\"tasks\":[{\"action\":\"explode_world\",\"parameters\":{"
                + "\"allowTeleport\":true,\"capability\":\"ALLOW_FLIGHT\"}}]}")));
        controller.submitUserGoal("Destroy the world", null);

        helper.runAfterDelay(120, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(target).is(Blocks.STONE),
                "An invalid LLM task must never mutate the world");
            helper.assertTrue(controller.getActiveGoal() == null
                    || controller.getState() != com.steve.ai.execution.AgentState.EXECUTING,
                "Invalid LLM output must not keep a mutating action running");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 240)
    public static void autonomousControllerReplansAfterProtectedAction(GameTestHelper helper) {
        BlockPos relativeTarget = new BlockPos(2, 1, 1);
        BlockPos target = helper.absolutePos(relativeTarget);
        helper.setBlock(relativeTarget, Blocks.AIR.defaultBlockState());
        BlockPos relativeSafeFloor = new BlockPos(1, 1, 1);
        helper.setBlock(relativeSafeFloor, Blocks.STONE.defaultBlockState());
        PermissionManager.getInstance().protectRegion(helper.getLevel(), target, target);

        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("AutonomyRecovery_" + UUID.randomUUID().toString().substring(0, 8));
        steve.moveTo(helper.absolutePos(relativeSafeFloor.above()), 0.0F, 0.0F);
        steve.setPersistenceRequired();
        helper.getLevel().addFreshEntity(steve);

        AtomicInteger plannerCalls = new AtomicInteger();
        AutonomyController controller = steve.getAutonomyController();
        controller.setPlanner(context -> {
            int call = plannerCalls.getAndIncrement();
            String response = call == 0
                ? "{\"decision\":\"act\",\"summary\":\"Try protected placement\","
                    + "\"goalStatus\":\"in_progress\",\"tasks\":[{\"action\":\"place\",\"parameters\":{"
                    + "\"block\":\"stone\",\"x\":" + target.getX() + ",\"y\":" + target.getY()
                    + ",\"z\":" + target.getZ() + "}}]}"
                : "{\"decision\":\"blocked\",\"summary\":\"Protected location requires a different authorized goal\","
                    + "\"goalStatus\":\"blocked\",\"tasks\":[]}";
            return CompletableFuture.completedFuture(ResponseParser.parseAIResponse(response));
        });
        controller.submitUserGoal("Complete autonomous recovery probe", null);

        helper.runAfterDelay(180, () -> {
            helper.assertTrue(plannerCalls.get() >= 2,
                "Autonomy should request a second horizon after protected failure");
            helper.assertTrue(controller.getActiveGoal() == null,
                "A blocked protected goal should leave no active executable goal");
            helper.assertTrue(controller.getState() == com.steve.ai.execution.AgentState.BLOCKED,
                "Protected recovery must block instead of treating an unrelated success as proof");
            helper.assertTrue(helper.getLevel().getBlockState(target).isAir(),
                "Protected placement must never mutate the protected block");
            PermissionManager.getInstance().unprotectRegion(helper.getLevel(), target, target);
            helper.succeed();
        });
    }

    /**
     * Repeatedly ticks an action every server tick, then invokes the assertion callback
     * once the action is complete. The callback must call {@link GameTestHelper#succeed()}.
     */
    private static void tickUntilComplete(GameTestHelper helper,
            com.steve.ai.action.actions.BaseAction action, Runnable assertions) {
        helper.startSequence()
            .thenWaitUntil(() -> {
                action.tick();
                helper.assertTrue(action.isComplete(), "Action is still running");
            })
            .thenExecute(assertions);
    }
}
