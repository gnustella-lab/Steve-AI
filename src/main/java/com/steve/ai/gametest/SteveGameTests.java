package com.steve.ai.gametest;

import com.steve.ai.SteveMod;
import com.steve.ai.plugin.ActionRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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
}
