package com.steve.ai.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Represents a block to be placed at a specific position.
 * Shared class used across structure generation, building actions, and collaborative builds.
 */
public class BlockPlacement {
    public final BlockPos pos;
    public final Block block;
    public final BlockState state;

    public BlockPlacement(BlockPos pos, Block block) {
        this(pos, block.defaultBlockState());
    }

    public BlockPlacement(BlockPos pos, BlockState state) {
        this.pos = pos;
        this.state = state;
        this.block = state.getBlock();
    }
}
