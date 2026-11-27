package com.steve.ai;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
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
        // Unit tests have no data packs: bind every built-in tag to empty first so
        // component initializers can resolve tag references.
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            if (registry instanceof MappedRegistry<?> mapped) {
                mapped.bindAllTagsToEmpty();
            } else {
                // Forge's registry wrapper is package-private; invoke its public hook reflectively.
                try {
                    registry.getClass().getMethod("bindAllTagsToEmpty").invoke(registry);
                } catch (ReflectiveOperationException ignored) {
                    // Not a bindable registry.
                }
            }
        }
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
