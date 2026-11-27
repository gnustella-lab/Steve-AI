package com.steve.ai;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;

/** Test-only Minecraft bootstrap for 26.3 unit tests. */
public final class TestBootstrap {
    private TestBootstrap() {
    }

    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Unit tests have no data packs, so registry component initialization is
        // unavailable. Bind empty maps just to let ItemStack construction work;
        // the domain code supplies vanilla-like defaults when components are absent.
        for (Holder.Reference<Item> reference : BuiltInRegistries.ITEM.listElements().toList()) {
            if (!reference.areComponentsBound()) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }
}
