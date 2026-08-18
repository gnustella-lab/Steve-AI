package com.steve.ai.gametest;

import com.steve.ai.SteveMod;
import com.steve.ai.action.Task;
import com.steve.ai.action.actions.MineBlockAction;
import com.steve.ai.autonomy.AutonomyController;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.entity.SteveManager;
import com.steve.ai.llm.ResponseParser;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.security.PermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
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
        CompoundTag entityTag = new CompoundTag();
        original.saveWithoutId(entityTag);

        SteveEntity restored = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        restored.load(entityTag);

        helper.assertTrue(restored.getSteveInventory().count(Items.OAK_LOG) == 32,
            "Inventory stack was not restored from entity NBT");
        helper.assertTrue(ownerUuid.equals(restored.getOwnerUuid()),
            "Owner UUID was not restored from entity NBT");
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
        steve.setSteveName("AutonomousIronGoal");
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

    @GameTest(templateNamespace = SteveMod.MODID, template = "empty", timeoutTicks = 240)
    public static void autonomousControllerReplansAfterProtectedAction(GameTestHelper helper) {
        BlockPos relativeTarget = new BlockPos(2, 1, 1);
        BlockPos target = helper.absolutePos(relativeTarget);
        helper.setBlock(relativeTarget, Blocks.AIR.defaultBlockState());
        PermissionManager.getInstance().protectRegion(helper.getLevel(), target, target);

        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("AutonomyRecovery");
        steve.moveTo(target.above(), 0.0F, 0.0F);
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
                : "{\"decision\":\"act\",\"summary\":\"Verify after replanning\","
                    + "\"goalStatus\":\"in_progress\",\"tasks\":[{\"action\":\"inspect_inventory\",\"parameters\":{}}]}";
            return CompletableFuture.completedFuture(ResponseParser.parseAIResponse(response));
        });
        controller.submitUserGoal("Complete autonomous recovery probe", null);

        helper.runAfterDelay(180, () -> {
            helper.assertTrue(plannerCalls.get() >= 2,
                "Autonomy should request a second horizon after protected failure");
            helper.assertTrue(controller.getActiveGoal() == null,
                "Autonomy should verify the goal after the replacement horizon");
            helper.assertTrue(helper.getLevel().getBlockState(target).isAir(),
                "Protected placement must never mutate the protected block");
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
