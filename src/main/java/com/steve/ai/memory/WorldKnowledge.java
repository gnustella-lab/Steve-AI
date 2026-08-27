package com.steve.ai.memory;

import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded, read-only world perception used to build an observation snapshot.
 *
 * <p>The scan deliberately uses a fixed number of deterministic samples rather than
 * iterating the complete cubic volume around the agent. It must remain safe to call
 * from a planning request and never mutates the world.</p>
 */
public class WorldKnowledge {
    public static final int DEFAULT_SCAN_RADIUS = 16;
    public static final int MAX_SCAN_RADIUS = 32;
    public static final int DEFAULT_MAX_BLOCK_SAMPLES = 256;
    public static final int MAX_BLOCK_SAMPLES = 2_048;
    public static final int MAX_POSITIONED_OBSERVATIONS = 48;
    private static final int MIN_BLOCK_SAMPLES = 32;

    private final SteveEntity steve;
    private final int scanRadius;
    private final int maxBlockSamples;
    private Map<Block, Integer> nearbyBlocks = Map.of();
    private List<Entity> nearbyEntities = List.of();
    private String biomeName = "unknown";
    private Map<BlockPos, Block> nearbyBlockPositions = Map.of();
    private List<BlockPos> nearbyResourcePositions = List.of();
    private List<BlockPos> nearbyStationPositions = List.of();
    private List<BlockPos> nearbyContainerPositions = List.of();
    private List<BlockPos> nearbyHazardPositions = List.of();
    private int sampledBlockCount;

    public WorldKnowledge(SteveEntity steve) {
        this(steve, DEFAULT_SCAN_RADIUS, DEFAULT_MAX_BLOCK_SAMPLES);
    }

    /** Creates a bounded observation scan. The default is deliberately not a cubic full scan. */
    public WorldKnowledge(SteveEntity steve, int scanRadius, int maxBlockSamples) {
        this.steve = steve;
        this.scanRadius = Math.max(2, Math.min(scanRadius, MAX_SCAN_RADIUS));
        this.maxBlockSamples = Math.max(MIN_BLOCK_SAMPLES, Math.min(maxBlockSamples, MAX_BLOCK_SAMPLES));
        scan();
    }

    private void scan() {
        if (steve == null || steve.level() == null) {
            return;
        }
        scanBiome();
        scanBlocks();
        scanEntities();
    }

    private void scanBiome() {
        Level level = steve.level();
        BlockPos pos = steve.blockPosition();
        Biome biome = level.getBiome(pos).value();
        var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        ResourceLocation biomeKey = biomeRegistry.getKey(biome);
        biomeName = biomeKey == null ? "unknown" : bounded(biomeKey.getPath(), 128);
    }

    /**
     * Samples adjacent blocks first, then uses a deterministic pseudo-random sequence.
     * The loop count is bounded by {@link #maxBlockSamples}; there is no radius cubed loop.
     */
    private void scanBlocks() {
        Level level = steve.level();
        BlockPos stevePos = steve.blockPosition();
        Map<Block, Integer> counts = new LinkedHashMap<>();
        Map<BlockPos, Block> positions = new LinkedHashMap<>();
        sampledBlockCount = 0;

        for (int x = -1; x <= 1 && sampledBlockCount < maxBlockSamples; x++) {
            for (int y = -1; y <= 1 && sampledBlockCount < maxBlockSamples; y++) {
                for (int z = -1; z <= 1 && sampledBlockCount < maxBlockSamples; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    recordBlock(level, stevePos.offset(x, y, z), counts, positions);
                }
            }
        }

        int sampleIndex = 0;
        int maxAdditionalSamples = maxBlockSamples - sampledBlockCount;
        while (sampleIndex < maxAdditionalSamples) {
            int x = sampledCoordinate(sampleIndex, scanRadius, 0x13579BDFL);
            int y = sampledCoordinate(sampleIndex, scanRadius, 0x2468ACE1L);
            int z = sampledCoordinate(sampleIndex, scanRadius, 0x5A5A5A5AL);
            sampleIndex++;
            if (x == 0 && y == 0 && z == 0) {
                continue;
            }
            recordBlock(level, stevePos.offset(x, y, z), counts, positions);
        }

        nearbyBlocks = immutableMap(counts);
        nearbyBlockPositions = immutablePositionMap(positions);
        nearbyResourcePositions = categorizedPositions(positions, WorldKnowledge::isResource);
        nearbyStationPositions = categorizedPositions(positions, WorldKnowledge::isStation);
        nearbyContainerPositions = categorizedPositions(positions, WorldKnowledge::isContainer);
        nearbyHazardPositions = categorizedPositions(positions, WorldKnowledge::isHazard);
    }

    /**
     * Fail-closed chunk guard. Unloaded server chunks are never sampled, so
     * {@link Level#getBlockState(BlockPos)} cannot force-load or generate terrain.
     */
    public static boolean canSample(Level level, BlockPos position) {
        if (level == null || position == null) {
            return false;
        }
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.isLoaded(position);
        }
        return true;
    }

    private void recordBlock(Level level, BlockPos position, Map<Block, Integer> counts,
            Map<BlockPos, Block> positions) {
        sampledBlockCount++;
        if (!canSample(level, position)) {
            return;
        }
        BlockState state = level.getBlockState(position);
        Block block = state.getBlock();
        if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
            return;
        }
        counts.merge(block, 1, Integer::sum);
        if (positions.size() < MAX_POSITIONED_OBSERVATIONS * 4) {
            positions.putIfAbsent(position, block);
        }
    }

    private void scanEntities() {
        Level level = steve.level();
        AABB searchBox = steve.getBoundingBox().inflate(scanRadius);
        nearbyEntities = List.copyOf(new ArrayList<>(level.getEntities(steve, searchBox)));
    }

    private static int sampledCoordinate(int sampleIndex, int radius, long salt) {
        long value = (sampleIndex + 1L) * 1_103_515_245L + salt;
        int span = radius * 2 + 1;
        return Math.floorMod(value, span) - radius;
    }

    private static Map<Block, Integer> immutableMap(Map<Block, Integer> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<BlockPos, Block> immutablePositionMap(Map<BlockPos, Block> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static List<BlockPos> categorizedPositions(Map<BlockPos, Block> positions,
            java.util.function.Predicate<Block> predicate) {
        return positions.entrySet().stream()
            .filter(entry -> predicate.test(entry.getValue()))
            .map(Map.Entry::getKey)
            .limit(MAX_POSITIONED_OBSERVATIONS)
            .toList();
    }

    public int getScanRadius() {
        return scanRadius;
    }

    public int getMaxBlockSamples() {
        return maxBlockSamples;
    }

    public int getSampledBlockCount() {
        return sampledBlockCount;
    }

    public String getBiomeName() {
        return biomeName;
    }

    public String getNearbyBlocksSummary() {
        if (nearbyBlocks.isEmpty()) {
            return "none";
        }

        List<Map.Entry<Block, Integer>> sorted = nearbyBlocks.entrySet().stream()
            .sorted(Comparator.<Map.Entry<Block, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(entry -> blockName(entry.getKey())))
            .limit(5)
            .toList();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(blockName(sorted.get(i).getKey()));
        }
        return sb.toString();
    }

    public String getNearbyEntitiesSummary() {
        if (nearbyEntities.isEmpty()) {
            return "none";
        }

        Map<String, Integer> entityCounts = new LinkedHashMap<>();
        for (Entity entity : nearbyEntities) {
            String name = entity.getType().toString();
            entityCounts.merge(name, 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Integer> entry : entityCounts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                    .thenComparing(Map.Entry::getKey))
                .limit(5).toList()) {
            if (count++ > 0) sb.append(", ");
            sb.append(entry.getValue()).append(' ').append(entry.getKey());
        }
        return sb.toString();
    }

    public Map<Block, Integer> getNearbyBlocks() {
        return nearbyBlocks;
    }

    /** Returns bounded block positions without exposing mutable scan state. */
    public Map<BlockPos, Block> getNearbyBlockPositions() {
        return nearbyBlockPositions;
    }

    public List<BlockPos> getNearbyResourcePositions() {
        return nearbyResourcePositions;
    }

    public List<BlockPos> getNearbyStationPositions() {
        return nearbyStationPositions;
    }

    public List<BlockPos> getNearbyContainerPositions() {
        return nearbyContainerPositions;
    }

    public List<BlockPos> getNearbyHazardPositions() {
        return nearbyHazardPositions;
    }

    public List<Entity> getNearbyEntities() {
        return nearbyEntities;
    }

    public String getNearbyPlayerNames() {
        List<String> playerNames = new ArrayList<>();
        for (Entity entity : nearbyEntities) {
            if (entity instanceof Player player) {
                playerNames.add(player.getName().getString());
            }
        }
        if (playerNames.isEmpty()) {
            return "none";
        }
        return String.join(", ", playerNames.stream().limit(12).toList());
    }

    public static boolean isResource(Block block) {
        return block != null && isResourceName(blockName(block));
    }

    public static boolean isResourceName(String name) {
        if (name == null) return false;
        return name.endsWith("_ore")
            || name.equals("ancient_debris")
            || name.equals("clay")
            || name.equals("gravel")
            || name.equals("sand")
            || name.endsWith("_log")
            || name.endsWith("_stem");
    }

    public static boolean isStation(Block block) {
        return block != null && isStationName(blockName(block));
    }

    public static boolean isStationName(String name) {
        return name != null && switch (name) {
            case "crafting_table", "furnace", "blast_furnace", "smoker", "anvil",
                "chipped_anvil", "damaged_anvil", "stonecutter", "loom",
                "cartography_table", "fletching_table", "grindstone", "smithing_table" -> true;
            default -> false;
        };
    }

    public static boolean isContainer(Block block) {
        return block != null && isContainerName(blockName(block));
    }

    public static boolean isContainerName(String name) {
        return name != null && switch (name) {
            case "chest", "trapped_chest", "barrel", "shulker_box", "hopper",
                "dropper", "dispenser" -> true;
            default -> false;
        };
    }

    public static boolean isHazard(Block block) {
        return block != null && isHazardName(blockName(block));
    }

    public static boolean isHazardName(String name) {
        return name != null && switch (name) {
            case "lava", "fire", "soul_fire", "cactus", "magma_block", "campfire",
                "soul_campfire", "sweet_berry_bush" -> true;
            default -> false;
        };
    }

    private static String blockName(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? "unknown" : id.getPath();
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
