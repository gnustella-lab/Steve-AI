package com.steve.ai.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Bridges calls to the 26.3 registry API, where {@code get} returns an Optional holder. */
public final class ModApi {
    private ModApi() {
    }

    public static Item item(Identifier id) {
        if (id == null) {
            return Items.AIR;
        }
        return BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
    }

    public static Block block(Identifier id) {
        if (id == null) {
            return Blocks.AIR;
        }
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.AIR);
    }
}
