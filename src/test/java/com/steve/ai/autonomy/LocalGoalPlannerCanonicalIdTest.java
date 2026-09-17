package com.steve.ai.autonomy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Predicate;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression: the local grammar must resolve an unprefixed item name (e.g. "wooden_pickaxe")
 * to a canonical {@code minecraft:} id so that downstream equality gates (which normalize to
 * the minecraft: namespace) do not misclassify a satisfied local goal as "needs LLM".
 *
 * <p>Uses the real item predicate (mirrors {@code AutonomyController.parseLocalRequest}) so the
 * {@code ResourceLocation.tryParse} default-namespace behaviour is exercised — the existing
 * {@link LocalGoalPlannerTest} uses a literal {@code Set<String>} predicate which never accepts an
 * unprefixed token, hiding this case.
 */
class LocalGoalPlannerCanonicalIdTest {

    private final LocalGoalPlanner planner = new LocalGoalPlanner();

    private static final Predicate<String> REAL_ITEM = id -> {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null
            && BuiltInRegistries.ITEM.containsKey(rl)
            && BuiltInRegistries.ITEM.get(rl) != Items.AIR;
    };

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void unprefixedItemResolvesToCanonicalMinecraftId() {
        System.out.println("DIAG tryParse('wooden_pickaxe') = " + ResourceLocation.tryParse("wooden_pickaxe"));
        System.out.println("DIAG tryParse('minecraft:wooden_pickaxe') = "
            + ResourceLocation.tryParse("minecraft:wooden_pickaxe"));

        var resolved = planner.parse("craft 1 wooden_pickaxe", REAL_ITEM);
        assertTrue(resolved.isPresent(), "Local grammar must accept the plain craft command");
        String item = resolved.get().item();
        System.out.println("DIAG parse('craft 1 wooden_pickaxe').item() = " + item);

        assertTrue(item.contains(":"), "Resolved item must carry a minecraft: (or modded) namespace");
        assertEquals("minecraft:wooden_pickaxe", item,
            "Unprefixed item names must canonicalise to minecraft: form, not stay bare");
    }

    @Test
    void prefixedItemRoundTripsCanonical() {
        var resolved = planner.parse("craft 1 minecraft:wooden_pickaxe", REAL_ITEM);
        assertTrue(resolved.isPresent());
        assertEquals("minecraft:wooden_pickaxe", resolved.get().item());
    }
}
