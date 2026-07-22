package com.steve.ai.gametest;

import com.steve.ai.SteveMod;
import com.steve.ai.action.Task;
import com.steve.ai.action.actions.MineBlockAction;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.entity.SteveManager;
import com.steve.ai.plugin.ActionRegistry;
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
        steve.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        steve.getSteveInventory().insert(new ItemStack(Items.IRON_PICKAXE));
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
        helper.assertTrue(steve.getSteveInventory().count(Items.IRON_PICKAXE) == 1,
            "Temporary tool was lost or duplicated during cancellation");
        helper.assertTrue(steve.getSteveInventory().count(Items.DIAMOND_SWORD) == 0,
            "Previous main-hand item was duplicated into inventory");
        helper.succeed();
    }
}
