package com.steve.ai.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterization tests for the deterministic procedural structure catalogue. */
class StructureGeneratorsTest {

    @Test
    void wallUsesRequestedDimensionsMaterialAndUniquePositions() {
        BlockPos origin = new BlockPos(10, 64, -5);

        List<BlockPlacement> wall = StructureGenerators.generate(
            "wall", origin, 4, 3, 1, List.of(Blocks.COBBLESTONE));

        assertEquals(12, wall.size());
        assertEquals(12, new HashSet<>(wall.stream().map(placement -> placement.pos).toList()).size());
        assertTrue(wall.stream().allMatch(placement -> placement.state.is(Blocks.COBBLESTONE)));
        assertTrue(wall.stream().anyMatch(placement -> placement.pos.equals(origin)));
        assertTrue(wall.stream().anyMatch(placement -> placement.pos.equals(origin.offset(3, 2, 0))));
    }

    @Test
    void unknownStructureFallsBackToABoundedHouse() {
        List<BlockPlacement> fallback = StructureGenerators.generate(
            "not-a-real-structure", BlockPos.ZERO, 2, 2, 2, List.of(Blocks.STONE));

        assertFalse(fallback.isEmpty());
        assertTrue(fallback.size() < 1_000, "Fallback generation must remain bounded");
        assertTrue(fallback.stream().allMatch(placement -> placement.pos.getX() >= 0));
    }
}
