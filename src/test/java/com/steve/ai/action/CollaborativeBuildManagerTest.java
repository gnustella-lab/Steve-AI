package com.steve.ai.action;

import com.steve.ai.structure.BlockPlacement;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollaborativeBuildManagerTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clearBuilds() {
        CollaborativeBuildManager.clearAllBuilds();
    }

    @Test
    void onlyCountsABlockAfterPlacementIsConfirmed() {
        BlockPlacement placement = new BlockPlacement(new BlockPos(1, 64, 1), Blocks.STONE);
        CollaborativeBuildManager.CollaborativeBuild build =
            new CollaborativeBuildManager.CollaborativeBuild("house-test", List.of(placement), BlockPos.ZERO);

        BlockPlacement claimed = CollaborativeBuildManager.getNextBlock(build, "Steve");

        assertNotNull(claimed);
        assertEquals(0, build.getBlocksPlaced());
        assertFalse(build.isComplete());

        CollaborativeBuildManager.markBlockPlaced(build, "Steve");

        assertEquals(1, build.getBlocksPlaced());
        assertTrue(build.isComplete());
    }

    @Test
    void returnedBlockCanBeClaimedAgainAfterAPlacementFailure() {
        BlockPlacement placement = new BlockPlacement(new BlockPos(1, 64, 1), Blocks.STONE);
        CollaborativeBuildManager.CollaborativeBuild build =
            new CollaborativeBuildManager.CollaborativeBuild("house-test", List.of(placement), BlockPos.ZERO);

        BlockPlacement claimed = CollaborativeBuildManager.getNextBlock(build, "Steve");
        CollaborativeBuildManager.returnBlock(build, "Steve", claimed);

        assertSame(placement, CollaborativeBuildManager.getNextBlock(build, "Steve"));
        assertEquals(0, build.getBlocksPlaced());
    }

    @Test
    void emptyBuildHasStableProgress() {
        CollaborativeBuildManager.CollaborativeBuild build =
            new CollaborativeBuildManager.CollaborativeBuild("empty", List.of(), BlockPos.ZERO);

        assertTrue(build.isComplete());
        assertEquals(100, build.getProgressPercentage());
    }

    @Test
    void oneSteveFinishesEveryQuadrant() {
        List<BlockPlacement> placements = List.of(
            new BlockPlacement(new BlockPos(0, 64, 0), Blocks.STONE),
            new BlockPlacement(new BlockPos(10, 64, 0), Blocks.STONE),
            new BlockPlacement(new BlockPos(0, 64, 10), Blocks.STONE),
            new BlockPlacement(new BlockPos(10, 64, 10), Blocks.STONE));
        CollaborativeBuildManager.CollaborativeBuild build =
            new CollaborativeBuildManager.CollaborativeBuild("quadrants", placements, BlockPos.ZERO);

        int claimed = 0;
        while (CollaborativeBuildManager.getNextBlock(build, "Steve") != null) {
            CollaborativeBuildManager.markBlockPlaced(build, "Steve");
            claimed++;
        }

        assertEquals(4, claimed);
        assertTrue(build.isComplete());
    }

    @Test
    void activeBuildsAreIsolatedByDimension() {
        List<BlockPlacement> placements = List.of(
            new BlockPlacement(new BlockPos(0, 64, 0), Blocks.STONE));
        var overworld = CollaborativeBuildManager.registerBuild(
            "house", placements, BlockPos.ZERO, "minecraft:overworld");
        var nether = CollaborativeBuildManager.registerBuild(
            "house", placements, BlockPos.ZERO, "minecraft:the_nether");

        assertSame(overworld,
            CollaborativeBuildManager.findActiveBuild("house", "minecraft:overworld"));
        assertSame(nether,
            CollaborativeBuildManager.findActiveBuild("house", "minecraft:the_nether"));
    }

    @Test
    void activeBuildLookupDoesNotJoinAProjectAcrossTheWorld() {
        List<BlockPlacement> placements = List.of(
            new BlockPlacement(new BlockPos(0, 64, 0), Blocks.STONE));
        var nearOrigin = CollaborativeBuildManager.registerBuild(
            "house", placements, BlockPos.ZERO, "minecraft:overworld");
        var farAway = CollaborativeBuildManager.registerBuild(
            "house", placements, new BlockPos(1_000, 64, 0), "minecraft:overworld");

        assertSame(nearOrigin, CollaborativeBuildManager.findActiveBuild(
            "house", "minecraft:overworld", new BlockPos(20, 64, 0), 128));
        assertSame(farAway, CollaborativeBuildManager.findActiveBuild(
            "house", "minecraft:overworld", new BlockPos(980, 64, 0), 128));
        assertNull(CollaborativeBuildManager.findActiveBuild(
            "house", "minecraft:overworld", new BlockPos(500, 64, 0), 128));
    }
}
