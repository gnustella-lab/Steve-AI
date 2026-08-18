package com.steve.ai.entity;

import com.steve.ai.SteveMod;
import com.steve.ai.action.ActionExecutor;
import com.steve.ai.autonomy.AutonomyController;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.inventory.SteveInventory;
import com.steve.ai.memory.SteveMemory;
import com.steve.ai.security.PermissionManager;
import com.steve.ai.security.SteveAccessProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class SteveEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> STEVE_NAME = 
        SynchedEntityData.defineId(SteveEntity.class, EntityDataSerializers.STRING);

    private SteveMemory memory;
    private final SteveInventory inventory;
    private final SteveAccessProfile accessProfile;
    private ActionExecutor actionExecutor;
    private AutonomyController autonomyController;
    private int tickCounter = 0;
    private boolean isFlying = false;
    private boolean isInvulnerable = false;

    public SteveEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.memory = new SteveMemory(this);
        this.inventory = new SteveInventory(SteveConfig.INVENTORY_SLOTS.get());
        this.accessProfile = new SteveAccessProfile();
        this.actionExecutor = null;
        this.autonomyController = null;
        this.setCustomNameVisible(true);
        this.setCanPickUpLoot(true);
        
        this.isInvulnerable = true;
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.25D)
            .add(Attributes.ATTACK_DAMAGE, 8.0D)
            .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(STEVE_NAME, "Steve");
    }

    @Override
    public void tick() {
        super.tick();
        
        if (!this.level().isClientSide) {
            tickCounter++;
            if (tickCounter % 20 == 0) {
                syncEquipmentToInventory();
            }
            getActionExecutor().tick();
            getAutonomyController().tick();
        }
    }

    public void setSteveName(String name) {
        this.entityData.set(STEVE_NAME, name);
        this.setCustomName(Component.literal(name));
    }

    public String getSteveName() {
        return entityData.get(STEVE_NAME);
    }

    public SteveMemory getMemory() {
        return this.memory;
    }

    /** Returns the single inventory abstraction used by actions and persistence. */
    public SteveInventory getSteveInventory() {
        return inventory;
    }

    /**
     * Syncs the entity's equipment (main hand, off hand, armor) into the SteveInventory.
     * Called when the entity's equipment may have changed outside the inventory.
     */
    public void syncEquipmentToInventory() {
        inventory.setMainHandItem(this.getMainHandItem());
        inventory.setOffhandItem(this.getOffhandItem());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                inventory.setArmor(slot, this.getItemBySlot(slot));
            }
        }
    }

    /**
     * Syncs the SteveInventory's equipment back to the entity.
     * Called after the inventory has been modified (e.g., after crafting or equipping).
     */
    public void syncEquipmentFromInventory() {
        this.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, inventory.getMainHandItem());
        this.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, inventory.getOffhandItem());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                this.setItemSlot(slot, inventory.getArmor(slot));
            }
        }
    }

    /** Returns UUID-based ownership and sharing metadata. */
    public SteveAccessProfile getAccessProfile() {
        return accessProfile;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return accessProfile.getOwnerUuid();
    }

    public void setOwnerUuid(UUID ownerUuid) {
        accessProfile.transferOwnership(ownerUuid);
    }

    public boolean canBeControlledBy(UUID playerUuid, boolean administrator) {
        return accessProfile.canControl(playerUuid, administrator);
    }

    public ActionExecutor getActionExecutor() {
        if (this.actionExecutor == null) {
            this.actionExecutor = new ActionExecutor(this);
        }
        return this.actionExecutor;
    }

    /** Returns the persistent goal-driven executive for this entity. */
    public AutonomyController getAutonomyController() {
        if (this.autonomyController == null) {
            this.autonomyController = new AutonomyController(this, getActionExecutor());
        }
        return this.autonomyController;
    }

    /**
     * Resolves the player for controller-relative behavior without selecting unrelated nearby players.
     * Ownerless legacy Steves retain nearest-player fallback when no controller is available.
     */
    @Nullable
    public Player getPreferredPlayer() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        UUID controllerUuid = actionExecutor == null ? null : actionExecutor.getControllingPlayerUuid();
        if (controllerUuid != null) {
            Player controller = serverLevel.getPlayerByUUID(controllerUuid);
            if (isUsablePlayer(controller)) {
                return controller;
            }
        }
        if (accessProfile.getOwnerUuid() != null) {
            Player owner = serverLevel.getPlayerByUUID(accessProfile.getOwnerUuid());
            return isUsablePlayer(owner) ? owner : null;
        }
        return serverLevel.players().stream()
            .filter(SteveEntity::isUsablePlayer)
            .min(java.util.Comparator.comparingDouble(this::distanceToSqr))
            .orElse(null);
    }

    private static boolean isUsablePlayer(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SteveName", getSteveName());
        
        CompoundTag memoryTag = new CompoundTag();
        this.memory.saveToNBT(memoryTag);
        tag.put("Memory", memoryTag);
        tag.put("SteveInventory", inventory.save());
        tag.put("AccessProfile", accessProfile.save());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SteveName")) {
            this.setSteveName(tag.getString("SteveName"));
        }
        
        if (tag.contains("Memory")) {
            this.memory.loadFromNBT(tag.getCompound("Memory"));
        }
        if (tag.contains("SteveInventory", Tag.TAG_COMPOUND)) {
            inventory.load(tag.getCompound("SteveInventory"));
        }
        if (tag.contains("AccessProfile", Tag.TAG_COMPOUND)) {
            accessProfile.load(tag.getCompound("AccessProfile"));
        }
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        if (level().isClientSide || itemEntity == null || itemEntity.isRemoved()) {
            return;
        }
        ItemStack offered = itemEntity.getItem();
        int offeredCount = offered.getCount();
        ItemStack remainder = inventory.insert(offered);
        int accepted = offeredCount - remainder.getCount();
        if (accepted <= 0) {
            return;
        }

        onItemPickup(itemEntity);
        take(itemEntity, accepted);
        if (remainder.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(remainder);
        }
    }

    @Override
    public boolean canHoldItem(ItemStack stack) {
        return inventory.canInsert(stack);
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return inventory.canInsert(stack);
    }

    /**
     * Breaks one block on the server thread and commits its loot to the inventory exactly once.
     * Overflow remains in the world instead of being deleted or duplicated.
     *
     * <p>Applies per-block tool damage when a damageable tool is held in the main hand,
     * mirroring vanilla {@code ItemStack#mineBlock} behaviour. Broken tools are removed
     * from the main hand without duplication.</p>
     */
    public boolean breakBlockIntoInventory(BlockPos position) {
        if (!(level() instanceof ServerLevel serverLevel)
                || !serverLevel.getServer().isSameThread()
                || !serverLevel.isLoaded(position)
                || PermissionManager.getInstance().isProtected(serverLevel, position)) {
            return false;
        }
        BlockState state = serverLevel.getBlockState(position);
        if (state.isAir()) {
            return false;
        }
        BlockEntity blockEntity = serverLevel.getBlockEntity(position);
        ItemStack tool = getMainHandItem();
        java.util.List<ItemStack> drops = Block.getDrops(
            state, serverLevel, position, blockEntity, this, tool);
        if (!serverLevel.destroyBlock(position, false, this)) {
            return false;
        }
        state.spawnAfterBreak(serverLevel, position, tool, true);
        applyToolWear(state, tool);
        for (ItemStack drop : drops) {
            ItemStack remainder = inventory.insert(drop);
            if (!remainder.isEmpty()) {
                Block.popResource(serverLevel, position, remainder);
            }
        }
        return true;
    }

    /**
     * Applies one block of wear to the currently held tool when it is damageable.
     * Swaps broken tools out of the main hand atomically without dropping duplicates.
     * Operates on the entity's actual equipped stack, then syncs the change to the
     * SteveInventory equipment map.
     */
    private void applyToolWear(BlockState state, ItemStack tool) {
        if (tool.isEmpty() || !tool.isDamageableItem()) {
            return;
        }
        ItemStack entityHand = this.getMainHandItem();
        ItemStack target = entityHand.isDamageableItem() ? entityHand : tool;
        try {
            target.hurtAndBreak(1, this, item -> {});
        } catch (Throwable ignored) {
            target.setDamageValue(target.getDamageValue() + 1);
        }
        if (target.getDamageValue() >= target.getMaxDamage()) {
            target.shrink(1);
            if (target.isEmpty()) {
                setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                inventory.setMainHandItem(ItemStack.EMPTY);
            }
        } else {
            inventory.setMainHandItem(target.copy());
        }
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                       MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                       @Nullable CompoundTag tag) {
        spawnData = super.finalizeSpawn(level, difficulty, spawnType, spawnData, tag);
        return spawnData;
    }

    public void sendChatMessage(String message) {
        if (this.level().isClientSide) return;
        
        Component chatComponent = Component.literal("<" + getSteveName() + "> " + message);
        this.level().players().forEach(player -> player.sendSystemMessage(chatComponent));
    }

    @Override
    public void remove(RemovalReason reason) {
        if (autonomyController != null) {
            autonomyController.shutdown();
        }
        if (actionExecutor != null) {
            actionExecutor.shutdown();
        }
        super.remove(reason);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source,
            int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        dropInventoryContents();
    }

    /**
     * Atomically drains the custom inventory into item entities on the server thread.
     * Repeated calls are safe because a successful call leaves the inventory empty.
     *
     * @return true when the inventory was safely drained or was already empty
     */
    public boolean dropInventoryContents() {
        if (!(level() instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread()) {
            SteveMod.LOGGER.error("Refusing to drain Steve '{}' inventory outside the server thread",
                getSteveName());
            return false;
        }
        for (ItemStack stack : inventory.drainAll()) {
            spawnAtLocation(stack);
        }
        return true;
    }

    public void setFlying(boolean flying) {
        this.isFlying = flying;
        this.setNoGravity(flying);
        this.setInvulnerableBuilding(flying);
    }

    public boolean isFlying() {
        return this.isFlying;
    }

    /**
     * Set invulnerability for building (immune to ALL damage: fire, lava, suffocation, fall, etc.)
     */
    public void setInvulnerableBuilding(boolean invulnerable) {
        this.isInvulnerable = invulnerable;
        this.setInvulnerable(invulnerable); // Minecraft's built-in invulnerability
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public void travel(net.minecraft.world.phys.Vec3 travelVector) {
        if (this.isFlying && !this.level().isClientSide) {
            double motionY = this.getDeltaMovement().y;
            
            if (this.getNavigation().isInProgress()) {
                super.travel(travelVector);
                
                // But add ability to move vertically freely
                if (Math.abs(motionY) < 0.1) {
                    // Small upward force to prevent falling
                    this.setDeltaMovement(this.getDeltaMovement().add(0, 0.05, 0));
                }
            } else {
                super.travel(travelVector);
            }
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float damageMultiplier, DamageSource source) {
        // No fall damage when flying
        if (this.isFlying) {
            return false;
        }
        return super.causeFallDamage(distance, damageMultiplier, source);
    }
}

