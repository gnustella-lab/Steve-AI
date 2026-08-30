package com.steve.ai.action;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded, timed reservations that prevent two Steves from claiming the same world resource.
 * Locks never survive process restart because the board is not persisted.
 */
public final class ResourceReservationBoard {
    public static final long DEFAULT_TTL_TICKS = 200L;
    private static final ResourceReservationBoard INSTANCE = new ResourceReservationBoard();

    private record Key(String dimension, long packedPos) { }

    private record Lease(UUID owner, long expiresAtTick) { }

    private final Map<Key, Lease> leases = new ConcurrentHashMap<>();

    private ResourceReservationBoard() {
    }

    public static ResourceReservationBoard getInstance() {
        return INSTANCE;
    }

    public synchronized boolean tryReserve(String dimension, BlockPos position, UUID owner, long nowTick, long ttlTicks) {
        if (dimension == null || dimension.isBlank() || position == null || owner == null) {
            return false;
        }
        long now = Math.max(0L, nowTick);
        expire(now);
        Key key = key(dimension, position);
        long ttl = Math.max(1L, ttlTicks);
        long expiresAt = now > Long.MAX_VALUE - ttl ? Long.MAX_VALUE : now + ttl;
        Lease existing = leases.get(key);
        if (existing != null && now < existing.expiresAtTick() && !owner.equals(existing.owner())) {
            return false;
        }
        leases.put(key, new Lease(owner, expiresAt));
        return true;
    }

    public synchronized boolean isHeldByOther(String dimension, BlockPos position, UUID owner, long nowTick) {
        if (dimension == null || position == null) {
            return false;
        }
        long now = Math.max(0L, nowTick);
        expire(now);
        Lease existing = leases.get(key(dimension, position));
        return existing != null && now < existing.expiresAtTick() && !Objects.equals(owner, existing.owner());
    }

    public synchronized void release(String dimension, BlockPos position, UUID owner) {
        if (dimension == null || position == null || owner == null) {
            return;
        }
        Key key = key(dimension, position);
        Lease existing = leases.get(key);
        if (existing != null && owner.equals(existing.owner())) {
            leases.remove(key, existing);
        }
    }

    public synchronized void releaseAll(UUID owner) {
        if (owner == null) {
            return;
        }
        leases.entrySet().removeIf(entry -> owner.equals(entry.getValue().owner()));
    }

    public synchronized void clear() {
        leases.clear();
    }

    private void expire(long nowTick) {
        leases.entrySet().removeIf(entry -> nowTick >= entry.getValue().expiresAtTick());
    }

    private static Key key(String dimension, BlockPos position) {
        return new Key(dimension, position.asLong());
    }
}
