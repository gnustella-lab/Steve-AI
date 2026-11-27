package com.steve.ai;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;

/**
 * Test-only Minecraft bootstrap for 26.3. The dedicated-server bootstrap registers
 * the built-in registries, but unit tests also need item components bound to holders
 * before constructing ItemStacks.
 */
public final class TestBootstrap {
    private TestBootstrap() {
    }

    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try {
            var pending = BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            int count = 0;
            for (DataComponentInitializers.PendingComponents<?> component : pending) {
                component.apply();
                count++;
            }
            System.out.println("[SteveAI tests] applied component initializer groups: " + count);
        } catch (RuntimeException e) {
            System.out.println("[SteveAI tests] component initializer build failed: " + e);
        }
        for (Holder.Reference<Item> reference : BuiltInRegistries.ITEM.listElements().toList()) {
            if (!reference.areComponentsBound()) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }
}
