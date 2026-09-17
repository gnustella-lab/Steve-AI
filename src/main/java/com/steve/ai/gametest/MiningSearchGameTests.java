package com.steve.ai.gametest;

import com.steve.ai.SteveMod;
import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.action.actions.GatherResourceAction;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.Map;

@GameTestHolder("steve_search")
@PrefixGameTestTemplate(false)
public final class MiningSearchGameTests {
    @GameTest(templateNamespace = "steve_search", template = "empty", timeoutTicks = 20)
    public static void targetOutsideLocalRadiusIsNotChased(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        steve.moveTo(origin, 0, 0);
        BlockPos distant = origin.east(9);
        helper.getLevel().setBlock(distant, Blocks.STRIPPED_BIRCH_LOG.defaultBlockState(), 3);
        GatherResourceAction action = new GatherResourceAction(steve,
            new Task("gather", Map.of("resource", "minecraft:stripped_birch_log", "quantity", 1)));
        action.start();
        for (int tick = 0; tick < 40; tick++) action.tick();
        helper.assertTrue(action.isComplete(), "Do not chase a target beyond eight blocks from the initial origin");
        helper.assertTrue(ActionResult.ERROR_TARGET_NOT_FOUND.equals(action.getResult().getErrorCode()),
            "Out-of-radius target must not be selected");
        helper.assertTrue(helper.getLevel().getBlockState(distant).is(Blocks.STRIPPED_BIRCH_LOG),
            "Distant block must remain untouched");
        helper.succeed();
    }

    @GameTest(templateNamespace = "steve_search", template = "empty", timeoutTicks = 20)
    public static void absentResourceStopsWithoutExcavating(GameTestHelper helper) {
        SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), helper.getLevel());
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        steve.moveTo(origin, 0, 0);
        // Do not add the entity: drive only the action, not unrelated autonomy ticks.
        helper.getLevel().setBlock(origin, Blocks.DIRT.defaultBlockState(), 3);
        GatherResourceAction action = new GatherResourceAction(steve,
            new Task("gather", Map.of("resource", "minecraft:stripped_mangrove_log", "quantity", 8)));
        action.start();
        for (int tick = 0; tick < 40; tick++) action.tick();
        helper.assertTrue(action.isComplete(), "Missing resource must stop within a bounded local search");
        ActionResult result = action.getResult();
        helper.assertTrue(ActionResult.ERROR_TARGET_NOT_FOUND.equals(result.getErrorCode()),
            "Expected structured target_not_found, got " + result);
        helper.assertTrue(!result.isRetryable() && result.requiresReplanning(),
            "Do not automatically retry an exhausted search");
        helper.assertTrue("minecraft:stripped_mangrove_log".equals(result.getObservation("target_block")),
            "Failure must identify the registry target");
        helper.assertTrue(Integer.valueOf(0).equals(result.getObservation("mined")), "Report actual progress");
        helper.assertTrue(helper.getLevel().getBlockState(origin).is(Blocks.DIRT),
            "Absent surface resource must not excavate unrelated blocks");
        for (int tick = 0; tick < 1000; tick++) action.tick();
        helper.assertTrue(action.getResult() == result, "Completed search must never restart itself");
        helper.succeed();
    }
}
