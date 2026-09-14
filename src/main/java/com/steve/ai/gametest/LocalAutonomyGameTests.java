package com.steve.ai.gametest;

import com.steve.ai.SteveMod;
import com.steve.ai.autonomy.AgentGoal;
import com.steve.ai.autonomy.GoalStatus;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.execution.AgentState;
import com.steve.ai.security.ActionPermission;
import com.steve.ai.security.PermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

/** Run in a separate server with maxLlmCallsPerGoal=0; no live provider is used. */
@GameTestHolder("steve_local")
@PrefixGameTestTemplate(false)
public final class LocalAutonomyGameTests {
    private static SteveEntity spawn(GameTestHelper helper) {
        helper.assertTrue(SteveConfig.AUTONOMY_MAX_LLM_CALLS_PER_GOAL.get() == 0,
            "This suite requires an offline configuration with zero LLM budget");
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("Local_" + UUID.randomUUID().toString().substring(0, 8));
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        steve.setPersistenceRequired();
        steve.getAutonomyController().setPlanner(context -> {
            throw new AssertionError("Local goals must not call the LLM");
        });
        helper.getLevel().addFreshEntity(steve);
        return steve;
    }

    private static void completed(GameTestHelper helper, AgentGoal goal) {
        helper.assertTrue(goal.getStatus() == GoalStatus.COMPLETED, "Goal must be verified complete");
        helper.assertTrue(goal.getBudget().getLlmCalls() == 0, "Goal must spend zero calls");
    }

    @GameTest(templateNamespace = "steve_local", template = "empty", timeoutTicks = 240)
    public static void craftsOnlyTheMissingQuantity(GameTestHelper helper) {
        SteveEntity steve = spawn(helper);
        steve.getSteveInventory().insert(new ItemStack(Items.OAK_PLANKS, 4));
        steve.getSteveInventory().insert(new ItemStack(Items.OAK_LOG, 1));
        AgentGoal goal = steve.getAutonomyController().submitUserGoal("craft 8 oak_planks", null);
        helper.runAfterDelay(180, () -> {
            completed(helper, goal);
            helper.assertTrue(steve.getSteveInventory().count(Items.OAK_PLANKS) == 8,
                "Must produce only four missing planks");
            helper.assertTrue(steve.getSteveInventory().count(Items.OAK_LOG) == 0,
                "Crafting must consume the log");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "steve_local", template = "empty", timeoutTicks = 120)
    public static void satisfiedGoalDoesNotConsumeMaterials(GameTestHelper helper) {
        SteveEntity steve = spawn(helper);
        steve.getSteveInventory().insert(new ItemStack(Items.OAK_PLANKS, 8));
        steve.getSteveInventory().insert(new ItemStack(Items.OAK_LOG, 1));
        AgentGoal goal = steve.getAutonomyController().submitUserGoal("craft 8 oak_planks", null);
        helper.runAfterDelay(80, () -> {
            completed(helper, goal);
            helper.assertTrue(steve.getSteveInventory().count(Items.OAK_LOG) == 1,
                "A fulfilled goal must not craft again");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "steve_local", template = "empty", timeoutTicks = 360)
    public static void gathersFromTheWorldWithoutLlm(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.OAK_LOG);
        SteveEntity steve = spawn(helper);
        AgentGoal goal = steve.getAutonomyController().submitUserGoal("gather 1 oak_log", null);
        helper.runAfterDelay(300, () -> {
            completed(helper, goal);
            helper.assertTrue(steve.getSteveInventory().count(Items.OAK_LOG) >= 1,
                "Resource must reach the actual inventory");
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(2, 2, 1))).isAir(),
                "Gathering must consume the world block");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "steve_local", template = "empty", timeoutTicks = 700)
    public static void smeltsOneMissingIngotWithoutLlm(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.FURNACE);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getLevel()
            .getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
        furnace.setItem(1, new ItemStack(Items.COAL, 1));
        SteveEntity steve = spawn(helper);
        steve.getSteveInventory().insert(new ItemStack(Items.IRON_INGOT, 15));
        steve.getSteveInventory().insert(new ItemStack(Items.RAW_IRON, 1));
        AgentGoal goal = steve.getAutonomyController().submitUserGoal("smelt 16 iron_ingots", null);
        helper.runAfterDelay(600, () -> {
            completed(helper, goal);
            helper.assertTrue(steve.getSteveInventory().count(Items.IRON_INGOT) == 16,
                "Must smelt exactly the missing ingot");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "steve_local", template = "empty", timeoutTicks = 240)
    public static void pauseAndStopPreventLocalExecution(GameTestHelper helper) {
        SteveEntity steve = spawn(helper);
        steve.getSteveInventory().insert(new ItemStack(Items.OAK_LOG, 1));
        var controller = steve.getAutonomyController();
        AgentGoal goal = controller.submitUserGoal("craft 4 oak_planks", null);
        controller.pause();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(controller.getState() == AgentState.PAUSED, "Pause must hold");
            helper.assertTrue(steve.getSteveInventory().count(Items.OAK_PLANKS) == 0,
                "Paused goal must not execute");
            controller.resume();
            controller.stop();
        });
        helper.runAfterDelay(180, () -> {
            helper.assertTrue(goal.getStatus() == GoalStatus.CANCELLED, "Stop must cancel the goal");
            helper.assertTrue(steve.getSteveInventory().count(Items.OAK_PLANKS) == 0,
                "Cancelled local goal must not restart");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "steve_local", template = "empty", timeoutTicks = 240)
    public static void localPlanStillRequiresPermission(GameTestHelper helper) {
        SteveEntity steve = spawn(helper);
        steve.getSteveInventory().insert(new ItemStack(Items.OAK_LOG, 1));
        PermissionManager.getInstance().setPermission(steve.getUUID(), ActionPermission.NONE);
        AgentGoal goal = steve.getAutonomyController().submitUserGoal("craft 4 oak_planks", null);
        helper.runAfterDelay(180, () -> {
            helper.assertTrue(goal.getStatus() == GoalStatus.BLOCKED, "Denied goal must block");
            helper.assertTrue(steve.getSteveInventory().count(Items.OAK_LOG) == 1
                && steve.getSteveInventory().count(Items.OAK_PLANKS) == 0,
                "Permission denial must leave materials unchanged");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "steve_local", template = "empty", timeoutTicks = 120)
    public static void unsupportedGoalBlocksWithoutCallingProvider(GameTestHelper helper) {
        SteveEntity steve = spawn(helper);
        AgentGoal goal = steve.getAutonomyController().submitUserGoal("craft stick then build a house", null);
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(goal.getStatus() == GoalStatus.BLOCKED, "Offline unknown goal must block");
            helper.assertTrue(goal.getBudget().getLlmCalls() == 0, "No provider call allowed");
            helper.succeed();
        });
    }
}
