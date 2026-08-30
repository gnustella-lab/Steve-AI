package com.steve.ai.action;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceReservationBoardTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clearBoard() {
        ResourceReservationBoard.getInstance().clear();
    }

    @Test
    void twoAgentsCannotReserveTheSameBlock() {
        ResourceReservationBoard board = ResourceReservationBoard.getInstance();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BlockPos ore = new BlockPos(4, 32, 8);

        assertTrue(board.tryReserve("minecraft:overworld", ore, first, 10L, 200L));
        assertFalse(board.tryReserve("minecraft:overworld", ore, second, 11L, 200L));
        assertTrue(board.isHeldByOther("minecraft:overworld", ore, second, 11L));
        assertFalse(board.isHeldByOther("minecraft:overworld", ore, first, 11L));
    }

    @Test
    void expiredAndReleasedReservationsBecomeAvailableAgain() {
        ResourceReservationBoard board = ResourceReservationBoard.getInstance();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BlockPos ore = new BlockPos(1, 64, 1);

        assertTrue(board.tryReserve("minecraft:overworld", ore, first, 1L, 5L));
        assertTrue(board.tryReserve("minecraft:overworld", ore, second, 10L, 5L));

        board.tryReserve("minecraft:the_nether", ore, first, 10L, 200L);
        board.releaseAll(first);
        assertFalse(board.isHeldByOther("minecraft:the_nether", ore, second, 10L));
    }

    @Test
    void concurrentClaimsHaveExactlyOneWinner() throws Exception {
        ResourceReservationBoard board = ResourceReservationBoard.getInstance();
        BlockPos ore = new BlockPos(12, 20, 12);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var firstResult = pool.submit(() -> {
                start.await();
                return board.tryReserve("minecraft:overworld", ore, first, 1L, 200L);
            });
            var secondResult = pool.submit(() -> {
                start.await();
                return board.tryReserve("minecraft:overworld", ore, second, 1L, 200L);
            });
            start.countDown();
            int winners = (firstResult.get(2, TimeUnit.SECONDS) ? 1 : 0)
                + (secondResult.get(2, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, winners);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void reservationsAreDimensionScoped() {
        ResourceReservationBoard board = ResourceReservationBoard.getInstance();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BlockPos ore = new BlockPos(8, 40, 8);

        assertTrue(board.tryReserve("minecraft:overworld", ore, first, 1L, 200L));
        assertTrue(board.tryReserve("minecraft:the_nether", ore, second, 1L, 200L));
    }
}
