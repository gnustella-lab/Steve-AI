package com.steve.ai.gametest;

import com.steve.ai.SteveMod;
import com.steve.ai.action.Task;
import com.steve.ai.action.actions.BuildStructureAction;
import com.steve.ai.autonomy.GoalStatus;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("steve_craft")
@PrefixGameTestTemplate(false)
public final class SurvivalCraftGameTests {
    private static SteveEntity spawn(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("Craft_" + UUID.randomUUID().toString().substring(0, 8));
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        steve.moveTo(helper.absolutePos(new BlockPos(1, 1, 1)), 0, 0);
        steve.setPersistenceRequired();
        steve.getAutonomyController().setPlanner(context -> { throw new AssertionError("LLM called"); });
        helper.getLevel().addFreshEntity(steve);
        return steve;
    }

    @GameTest(templateNamespace = "steve_craft", template = "empty", timeoutTicks = 240)
    public static void pickaxeFromThreeLogsIncludesTable(GameTestHelper helper) {
        SteveEntity steve = spawn(helper);
        steve.getSteveInventory().insert(new ItemStack(Items.OAK_LOG, 3));
        var goal = steve.getAutonomyController().submitUserGoal("craft 1 wooden_pickaxe", null);
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(goal.getStatus() == GoalStatus.COMPLETED, "Pickaxe goal: " + goal.getStatus());
            helper.assertTrue(steve.getSteveInventory().count(Items.WOODEN_PICKAXE) == 1, "Real pickaxe");
            helper.assertTrue(steve.getSteveInventory().count(Items.CRAFTING_TABLE) == 1, "Real table");
            helper.assertTrue(steve.getSteveInventory().count(Items.OAK_PLANKS) == 3, "No material duplication");
            helper.assertTrue(goal.getBudget().getLlmCalls() == 0, "Offline");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "steve_craft", template = "empty", timeoutTicks = 80)
    public static void missingHousePlanksRequestCrafting(GameTestHelper helper) {
        SteveEntity steve = spawn(helper);
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.STONE);
        BuildStructureAction action = new BuildStructureAction(steve, new Task("build", Map.of("structure", "house")));
        action.start();
        action.tick();
        var result = action.getResult();
        helper.assertTrue(result != null && !result.isSuccess(), "Survival must not create free blocks");
        helper.assertTrue("craft".equals(result.getObservation("required_action")), "Planks must be crafted, not mined");
        helper.assertTrue("minecraft:oak_planks".equals(result.getObservation("required_item")), "Registry id required");
        helper.assertTrue(((Number)result.getObservation("required_quantity")).intValue() > 4, "Whole build material demand");
        helper.succeed();
    }
}
