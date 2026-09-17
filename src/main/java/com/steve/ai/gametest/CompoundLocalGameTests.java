package com.steve.ai.gametest;

import com.steve.ai.SteveMod;
import com.steve.ai.autonomy.GoalStatus;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("steve_compound")
@PrefixGameTestTemplate(false)
public final class CompoundLocalGameTests {
    @GameTest(templateNamespace = "steve_compound", template = "empty", timeoutTicks = 400)
    public static void craftsInOrderAndRecountsSecondTarget(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        steve.setSteveName("Compound");
        steve.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0, 0);
        steve.setPersistenceRequired();
        steve.getAutonomyController().setPlanner(context -> {
            throw new AssertionError("Compound must never call LLM");
        });
        helper.getLevel().addFreshEntity(steve);
        steve.getSteveInventory().insert(new ItemStack(Items.OAK_LOG, 2));
        steve.getSteveInventory().insert(new ItemStack(Items.OAK_PLANKS, 2));
        var goal = steve.getAutonomyController().submitUserGoal("fazer 4 gravetos e fazer 4 tabuas", null);
        helper.runAfterDelay(320, () -> {
            helper.assertTrue(goal.getStatus() == GoalStatus.COMPLETED, "Both clauses must complete: " + steve.getAutonomyController().getStatusSummary());
            helper.assertTrue(steve.getSteveInventory().count(Items.STICK) == 4, "First clause crafts sticks");
            helper.assertTrue(steve.getSteveInventory().count(Items.OAK_PLANKS) == 4, "Second clause recalculates after sticks consume initial planks");
            helper.assertTrue(steve.getSteveInventory().count(Items.OAK_LOG) == 1, "Exactly one log used");
            helper.assertTrue(goal.getBudget().getLlmCalls() == 0, "No LLM calls");
            helper.succeed();
        });
    }
}
