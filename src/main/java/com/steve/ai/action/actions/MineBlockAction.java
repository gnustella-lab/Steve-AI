package com.steve.ai.action.actions;

import com.steve.ai.SteveMod;
import com.steve.ai.action.ActionResult;
import com.steve.ai.action.ResourceReservationBoard;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.security.PermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MineBlockAction extends BaseAction {
    private Block targetBlock;
    private int targetQuantity;
    private int minedCount;
    private BlockPos currentTarget;
    private int searchRadius = 8; // Small search radius - stay near player
    private int ticksRunning;
    private int ticksSinceLastTorch = 0;
    private BlockPos miningStartPos; // Fixed mining spot in front of player
    private BlockPos currentTunnelPos; // Current position in the tunnel
    private int miningDirectionX = 0; // Direction to mine (-1, 0, or 1)
    private int miningDirectionZ = 0; // Direction to mine (-1, 0, or 1)
    private int ticksSinceLastMine = 0; // Delay between mining blocks
    private ItemStack previousMainHandItem;
    private boolean temporaryToolEquipped;
    private static final int MAX_TICKS = 24000; // 20 minutes for deep mining
    private static final int TORCH_INTERVAL = 100; // Place torch every 5 seconds (100 ticks)
    private static final int MIN_LIGHT_LEVEL = 8;
    private static final int MINING_DELAY = 10;
    private static final int MAX_MINING_RADIUS = 5;
    
    // Ore depth mappings for intelligent mining
    private static final Map<String, Integer> ORE_DEPTHS = new HashMap<>() {{
        put("iron_ore", 64);  // Iron spawns well at Y=64 and below
        put("deepslate_iron_ore", -16); // Deep iron
        put("coal_ore", 96);
        put("copper_ore", 48);
        put("gold_ore", 32);
        put("deepslate_gold_ore", -16);
        put("diamond_ore", -59);
        put("deepslate_diamond_ore", -59);
        put("redstone_ore", 16);
        put("deepslate_redstone_ore", -32);
        put("lapis_ore", 0);
        put("deepslate_lapis_ore", -16);
        put("emerald_ore", 256); // Mountain biomes
    }};

    public MineBlockAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        String blockName = task.getStringParameter("block");
        targetQuantity = task.getIntParameter("quantity", 8); // Mine reasonable amount by default
        minedCount = 0;
        ticksRunning = 0;
        ticksSinceLastTorch = 0;
        ticksSinceLastMine = 0;
        
        targetBlock = parseBlock(blockName);
        
        if (targetBlock == null || targetBlock == Blocks.AIR) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                "Invalid block type: " + blockName).build();
            return;
        }
        if (targetQuantity <= 0) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                "Mining quantity must be positive").build();
            return;
        }

        if (targetBlock.defaultBlockState().requiresCorrectToolForDrops()
                && steve.getSteveInventory().findBestToolForBlock(targetBlock) == null) {
            String requiredTool = targetBlock.getName().getString().toLowerCase(java.util.Locale.ROOT).contains("ore")
                ? "stone_pickaxe" : "wooden_pickaxe";
            result = ActionResult.failure(ActionResult.ERROR_TOOL_MISSING,
                "A suitable tool is required before mining " + blockName)
                .observation("required_tool", requiredTool)
                .observation("target_block", blockName)
                .build();
            return;
        }
        
        miningStartPos = steve.blockPosition().immutable();
        currentTunnelPos = miningStartPos;

        equipBestToolForMining();
        
        SteveMod.LOGGER.info("Steve '{}' mining {} - staying at {} [SLOW & VISIBLE]", 
            steve.getSteveName(), targetBlock.getName().getString(), miningStartPos);
        
        // Look for ore nearby
        findNextBlock();
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        ticksSinceLastTorch++;
        ticksSinceLastMine++;
        
        if (ticksRunning > MAX_TICKS) {
            steve.setFlying(false);
            result = ActionResult.failure(ActionResult.ERROR_PATHING,
                "Mining search timeout - only found " + minedCount + " blocks")
                .retryable(true).requiresReplanning(true)
                .observation("mined", minedCount).observation("requested", targetQuantity).build();
            return;
        }
        
        if (ticksSinceLastTorch >= TORCH_INTERVAL) {
            placeTorchIfDark();
            ticksSinceLastTorch = 0;
        }
        
        if (ticksSinceLastMine < MINING_DELAY) {
            return; // Still waiting
        }
        
        if (currentTarget == null) {
            findNextBlock();
            
            if (currentTarget == null) {
                if (minedCount >= targetQuantity) {
                    // Found enough ore, mission accomplished
                    steve.setFlying(false);
                    result = ActionResult.success("Mined " + minedCount + " " + targetBlock.getName().getString()).build();
                    return;
                } else {
                    result = searchFailure("No matching block found in the local search area");
                    return;
                }
            }
        }
        
        if (steve.level().getBlockState(currentTarget).getBlock() == targetBlock) {
            if (!ResourceReservationBoard.getInstance().tryReserve(
                    dimension(), currentTarget, steve.getUUID(), currentTick(),
                    ResourceReservationBoard.DEFAULT_TTL_TICKS)) {
                currentTarget = null;
                return;
            }
            if (isProtected(currentTarget)) {
                result = ActionResult.failure(ActionResult.ERROR_PROTECTED,
                    "Target block is inside a protected region")
                    .requiresReplanning(true).build();
                return;
            }

            if (!steve.blockPosition().closerThan(currentTarget, 2.0)) {
                steve.getNavigation().moveTo(currentTarget.getX() + 0.5, currentTarget.getY(),
                    currentTarget.getZ() + 0.5, 1.0);
                return;
            }
            
            steve.swing(InteractionHand.MAIN_HAND, true);

            if (!steve.breakBlockIntoInventory(currentTarget)) {
                SteveMod.LOGGER.warn("Minecraft rejected mining at {}", currentTarget);
                releaseCurrentTarget();
                currentTarget = null;
                return;
            }
            ResourceReservationBoard.getInstance().release(dimension(), currentTarget, steve.getUUID());
            minedCount++;
            ticksSinceLastMine = 0; // Reset delay timer

            if (steve.getSteveInventory().isEquippedToolBroken()
                    || steve.getSteveInventory().needsReplacement(steve.getMainHandItem())) {
                equipBestToolForMining();
                if (steve.getSteveInventory().isEquippedToolBroken()
                        && steve.getMainHandItem().isEmpty()) {
                    SteveMod.LOGGER.warn("Steve '{}' ran out of usable tools for {}",
                        steve.getSteveName(), targetBlock.getName().getString());
                }
            }

            SteveMod.LOGGER.info("Steve '{}' moved to ore and mined {} at {} - Total: {}/{}",
                steve.getSteveName(), targetBlock.getName().getString(), currentTarget,
                minedCount, targetQuantity);
            
            if (minedCount >= targetQuantity) {
                steve.setFlying(false);
                result = ActionResult.success("Mined " + minedCount + " " + targetBlock.getName().getString()).build();
                return;
            }
            
            currentTarget = null;
        } else {
            releaseCurrentTarget();
            currentTarget = null;
        }
    }

    @Override
    protected void onCancel() {
        steve.setFlying(false);
        steve.getNavigation().stop();
        ResourceReservationBoard.getInstance().releaseAll(steve.getUUID());
    }

    @Override
    protected void onFinish() {
        steve.setFlying(false);
        steve.getNavigation().stop();
        ResourceReservationBoard.getInstance().releaseAll(steve.getUUID());
        restorePreviousMainHandItem();
    }

    @Override
    public String getDescription() {
        String blockName = targetBlock != null
            ? targetBlock.getName().getString()
            : task.getStringParameter("block");
        int quantity = targetQuantity > 0
            ? targetQuantity
            : task.getIntParameter("quantity", 1);
        return "Mine " + quantity + " " + blockName + " (" + minedCount + " found)";
    }

    /**
     * Check light level and place torch if too dark
     */
    private void placeTorchIfDark() {
        if (steve.getSteveInventory().count(Items.TORCH) <= 0) return;
        BlockPos stevePos = steve.blockPosition();
        int lightLevel = steve.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, stevePos);
        
        if (lightLevel < MIN_LIGHT_LEVEL) {
            BlockPos torchPos = findTorchPosition(stevePos);
            
            if (torchPos != null && steve.level().getBlockState(torchPos).isAir()
                    && !isProtected(torchPos)) {
                if (steve.level().setBlock(torchPos, Blocks.TORCH.defaultBlockState(), 3)) {
                    int consumed = steve.getSteveInventory().remove(Items.TORCH, 1);
                    if (consumed != 1) {
                        steve.level().removeBlock(torchPos, false);
                        return;
                    }
                    SteveMod.LOGGER.info("Steve '{}' placed torch at {} (light level was {})",
                        steve.getSteveName(), torchPos, lightLevel);
                    steve.swing(InteractionHand.MAIN_HAND, true);
                }
            }
        }
    }
    
    /**
     * Find a good position to place a torch (on floor or wall)
     */
    private BlockPos findTorchPosition(BlockPos center) {
        BlockPos floorPos = center.below();
        if (steve.level().getBlockState(floorPos).isSolid() && 
            steve.level().getBlockState(center).isAir()) {
            return center;
        }
        
        BlockPos[] wallPositions = {
            center.north(), center.south(), center.east(), center.west()
        };
        
        for (BlockPos wallPos : wallPositions) {
            if (steve.level().getBlockState(wallPos).isSolid() && 
                steve.level().getBlockState(center).isAir()) {
                return center;
            }
        }
        
        return null;
    }

    /**
     * Mine forward in ONE DIRECTION - creates a straight tunnel!
     * Steve progresses forward block by block
     */
    private void mineNearbyBlock() {
        BlockPos centerPos = currentTunnelPos;
        BlockPos abovePos = centerPos.above();
        if (!steve.blockPosition().closerThan(centerPos, 2.5)) {
            steve.getNavigation().moveTo(centerPos.getX() + 0.5, centerPos.getY(),
                centerPos.getZ() + 0.5, 1.0);
            return;
        }
        
        BlockState centerState = steve.level().getBlockState(centerPos);
        if (!centerState.isAir()) {
            if (centerState.getBlock() == Blocks.BEDROCK) {
                result = ActionResult.failure(ActionResult.ERROR_PATHING,
                    "Tunnel is blocked by bedrock").retryable(true).requiresReplanning(true).build();
                return;
            }
            if (isProtected(centerPos)) {
                result = ActionResult.failure(ActionResult.ERROR_PROTECTED,
                    "Tunnel reached a protected region").requiresReplanning(true).build();
                return;
            }
            steve.swing(InteractionHand.MAIN_HAND, true);
            if (!steve.breakBlockIntoInventory(centerPos)) {
                SteveMod.LOGGER.warn("Minecraft rejected tunnel mining at {}", centerPos);
                result = ActionResult.failure(ActionResult.ERROR_BLOCKED,
                    "Minecraft rejected tunnel mining at " + centerPos).retryable(true).build();
                return;
            }
            SteveMod.LOGGER.info("Steve '{}' mining tunnel at {}", steve.getSteveName(), centerPos);
        }

        BlockState aboveState = steve.level().getBlockState(abovePos);
        if (!aboveState.isAir()) {
            if (aboveState.getBlock() == Blocks.BEDROCK) {
                result = ActionResult.failure(ActionResult.ERROR_PATHING,
                    "Tunnel ceiling is blocked by bedrock").retryable(true).requiresReplanning(true).build();
                return;
            }
            if (isProtected(abovePos)) {
                result = ActionResult.failure(ActionResult.ERROR_PROTECTED,
                    "Tunnel reached a protected region").requiresReplanning(true).build();
                return;
            }
            steve.swing(InteractionHand.MAIN_HAND, true);
            if (!steve.breakBlockIntoInventory(abovePos)) {
                SteveMod.LOGGER.warn("Minecraft rejected tunnel mining at {}", abovePos);
                result = ActionResult.failure(ActionResult.ERROR_BLOCKED,
                    "Minecraft rejected tunnel mining at " + abovePos).retryable(true).build();
                return;
            }
        }

        steve.getNavigation().moveTo(centerPos.getX() + 0.5, centerPos.getY(), centerPos.getZ() + 0.5, 1.0);
        
        currentTunnelPos = currentTunnelPos.offset(miningDirectionX, 0, miningDirectionZ);
        
        ticksSinceLastMine = 0; // Reset delay
    }

    /**
     * Find ore blocks in the tunnel ahead
     * Searches forward in the mining direction
     */
    private void findNextBlock() {
        List<BlockPos> foundBlocks = new ArrayList<>();
        
        for (BlockPos position : BlockPos.betweenClosed(
                miningStartPos.offset(-searchRadius, -searchRadius, -searchRadius),
                miningStartPos.offset(searchRadius, searchRadius, searchRadius))) {
            if (position.distSqr(miningStartPos) > searchRadius * searchRadius
                    || !steve.level().hasChunkAt(position)) continue;
            if (steve.level().getBlockState(position).getBlock() == targetBlock
                    && !isProtected(position)
                    && !ResourceReservationBoard.getInstance().isHeldByOther(
                        dimension(), position, steve.getUUID(), currentTick())) {
                foundBlocks.add(position.immutable());
            }
        }

        if (!foundBlocks.isEmpty()) {
            foundBlocks.sort((a, b) -> Double.compare(a.distSqr(currentTunnelPos), b.distSqr(currentTunnelPos)));
            currentTarget = null;
            for (BlockPos candidate : foundBlocks) {
                if (ResourceReservationBoard.getInstance().tryReserve(
                        dimension(), candidate, steve.getUUID(), currentTick(),
                        ResourceReservationBoard.DEFAULT_TTL_TICKS)) {
                    currentTarget = candidate;
                    break;
                }
            }
            
            if (currentTarget != null) {
                SteveMod.LOGGER.info("Steve '{}' found {} ahead in tunnel at {}", 
                    steve.getSteveName(), targetBlock.getName().getString(), currentTarget);
            }
        }
    }

    /**
     * Equips the best tool from inventory for mining the target block.
     */
    private void equipBestToolForMining() {
        steve.syncEquipmentToInventory();
        previousMainHandItem = steve.getMainHandItem().copy();
        ItemStack previousTool = steve.getSteveInventory().equipBestTool(targetBlock);
        if (previousTool != null) {
            temporaryToolEquipped = true;
            steve.syncEquipmentFromInventory();
            SteveMod.LOGGER.info("Steve '{}' equipped best tool for mining {}", steve.getSteveName(), targetBlock);
        } else {
            // Check if current main hand item is already usable
            ItemStack currentMainHand = steve.getMainHandItem();
            if (!currentMainHand.isEmpty()) {
                SteveMod.LOGGER.info("Steve '{}' using current main hand item for mining", steve.getSteveName());
            } else {
                SteveMod.LOGGER.warn("Steve '{}' has no dedicated tool in inventory for {}", steve.getSteveName(), targetBlock);
            }
        }
    }

    private void restorePreviousMainHandItem() {
        if (!temporaryToolEquipped) {
            return;
        }
        if (!steve.getSteveInventory().restoreMainHand(previousMainHandItem)) {
            SteveMod.LOGGER.error(
                "Steve '{}' could not restore its previous main-hand item without risking item loss",
                steve.getSteveName());
            return;
        }
        steve.syncEquipmentFromInventory();
        previousMainHandItem = null;
        temporaryToolEquipped = false;
    }

    /**
     * Finds the authorized player used to determine mining direction.
     */
    private net.minecraft.world.entity.player.Player findPreferredPlayer() {
        return steve.getPreferredPlayer();
    }

    private Block parseBlock(String blockName) {
        if (blockName == null || blockName.isBlank()) {
            return Blocks.AIR;
        }
        blockName = blockName.toLowerCase().replace(" ", "_");
        
        Map<String, String> resourceToOre = new HashMap<>() {{
            put("iron", "iron_ore");
            put("diamond", "diamond_ore");
            put("coal", "coal_ore");
            put("gold", "gold_ore");
            put("copper", "copper_ore");
            put("redstone", "redstone_ore");
            put("lapis", "lapis_ore");
            put("emerald", "emerald_ore");
        }};
        
        if (resourceToOre.containsKey(blockName)) {
            blockName = resourceToOre.get(blockName);
        }
        
        if (!blockName.contains(":")) {
            blockName = "minecraft:" + blockName;
        }
        
        ResourceLocation resourceLocation = ResourceLocation.tryParse(blockName);
        return resourceLocation != null ? BuiltInRegistries.BLOCK.get(resourceLocation) : Blocks.AIR;
    }

    private ActionResult searchFailure(String message) {
        return ActionResult.failure(ActionResult.ERROR_TARGET_NOT_FOUND, message)
            .retryable(false).requiresReplanning(true).partialSuccess(minedCount > 0)
            .observation("target_block", BuiltInRegistries.BLOCK.getKey(targetBlock).toString())
            .observation("mined", minedCount).observation("requested", targetQuantity)
            .observation("search_exhausted", true).build();
    }

    private boolean isProtected(BlockPos pos) {
        return steve.level() instanceof ServerLevel serverLevel
            && PermissionManager.getInstance().isProtected(serverLevel, pos);
    }

    private void releaseCurrentTarget() {
        if (currentTarget != null) {
            ResourceReservationBoard.getInstance().release(dimension(), currentTarget, steve.getUUID());
        }
    }

    private long currentTick() {
        return steve.level() instanceof ServerLevel level && level.getServer() != null
            ? level.getServer().getTickCount() : 0L;
    }

    private String dimension() {
        return steve.level() instanceof ServerLevel level
            ? level.dimension().location().toString() : "unknown";
    }
}
