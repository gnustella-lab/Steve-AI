package com.steve.ai.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Persistent inventory for a Steve agent.
 *
 * <p>Provides main inventory slots plus equipment slots (armor, main hand, off hand).
 * All operations are non-destructive to caller-provided stacks: insertions return
 * the overflow, removals consume from existing stacks.</p>
 */
public final class SteveInventory {
    public static final int DATA_VERSION = 2;
    private static final int MAX_PERSISTED_SLOTS = 256;

    private NonNullList<ItemStack> contents;
    private final Map<EquipmentSlot, ItemStack> equipment;

    public SteveInventory(int slots) {
        if (slots <= 0 || slots > MAX_PERSISTED_SLOTS) {
            throw new IllegalArgumentException("Inventory slots must be between 1 and " + MAX_PERSISTED_SLOTS);
        }
        this.contents = NonNullList.withSize(slots, ItemStack.EMPTY);
        this.equipment = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipment.put(slot, ItemStack.EMPTY);
        }
    }

    public int getSlotCount() {
        return contents.size();
    }

    public List<ItemStack> getContents() {
        List<ItemStack> snapshot = new ArrayList<>();
        for (ItemStack stack : contents) {
            if (!stack.isEmpty()) {
                snapshot.add(stack.copy());
            }
        }
        return Collections.unmodifiableList(snapshot);
    }

    public ItemStack insert(ItemStack offered) {
        if (offered == null || offered.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = offered.copy();
        for (ItemStack existing : contents) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, remainder)) {
                int limit = Math.min(existing.getMaxStackSize(), remainder.getMaxStackSize());
                int accepted = Math.min(limit - existing.getCount(), remainder.getCount());
                if (accepted > 0) {
                    existing.grow(accepted);
                    remainder.shrink(accepted);
                }
            }
        }
        for (int slot = 0; slot < contents.size() && !remainder.isEmpty(); slot++) {
            if (contents.get(slot).isEmpty()) {
                int accepted = Math.min(remainder.getMaxStackSize(), remainder.getCount());
                ItemStack inserted = remainder.copy();
                inserted.setCount(accepted);
                contents.set(slot, inserted);
                remainder.shrink(accepted);
            }
        }
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
    }

    public boolean canInsert(ItemStack offered) {
        if (offered == null || offered.isEmpty()) {
            return false;
        }
        for (ItemStack existing : contents) {
            if (existing.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameTags(existing, offered)
                    && existing.getCount() < Math.min(existing.getMaxStackSize(), offered.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    public int remove(Item item, int amount) {
        if (item == null || amount <= 0) {
            return 0;
        }
        int remaining = amount;
        for (int slot = 0; slot < contents.size() && remaining > 0; slot++) {
            ItemStack stack = contents.get(slot);
            if (!stack.is(item)) {
                continue;
            }
            int removed = Math.min(stack.getCount(), remaining);
            stack.shrink(removed);
            remaining -= removed;
            if (stack.isEmpty()) {
                contents.set(slot, ItemStack.EMPTY);
            }
        }
        return amount - remaining;
    }

    public int count(Item item) {
        int total = 0;
        for (ItemStack stack : contents) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public List<ItemStack> drainAll() {
        List<ItemStack> drained = new ArrayList<>();
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack stack = contents.get(slot);
            if (!stack.isEmpty()) {
                drained.add(stack.copy());
                contents.set(slot, ItemStack.EMPTY);
            }
        }
        return List.copyOf(drained);
    }

    public String summarize(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        Map<String, SummaryEntry> aggregated = new TreeMap<>();
        for (ItemStack stack : contents) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String displayId = "minecraft".equals(itemId.getNamespace())
                ? itemId.getPath()
                : itemId.toString();
            int durability = stack.isDamageableItem()
                ? Math.max(0, Math.round(100.0f * (stack.getMaxDamage() - stack.getDamageValue())
                    / stack.getMaxDamage()))
                : -1;
            aggregated.merge(displayId, new SummaryEntry(stack.getCount(), durability), SummaryEntry::merge);
        }
        if (aggregated.isEmpty()) {
            return "- empty";
        }
        StringBuilder summary = new StringBuilder();
        int emitted = 0;
        for (Map.Entry<String, SummaryEntry> entry : aggregated.entrySet()) {
            if (emitted >= maxEntries) {
                break;
            }
            SummaryEntry value = entry.getValue();
            summary.append("- ").append(entry.getKey()).append(": ").append(value.count);
            if (value.durabilityPercent >= 0) {
                summary.append(", durability ").append(value.durabilityPercent).append('%');
            }
            summary.append('\n');
            emitted++;
        }
        int omitted = aggregated.size() - emitted;
        if (omitted > 0) {
            summary.append("- ... ").append(omitted).append(" more item types\n");
        }
        return summary.toString().stripTrailing();
    }

    public String summarizeEquipment() {
        StringBuilder sb = new StringBuilder();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = equipment.get(slot);
            if (!stack.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(slot.getName()).append(": ").append(formatStack(stack));
            }
        }
        return sb.isEmpty() ? "empty" : sb.toString();
    }

    public String summarizeHand(EquipmentSlot slot) {
        ItemStack stack = equipment.get(slot);
        return stack.isEmpty() ? "empty" : formatStack(stack);
    }

    private static String formatStack(ItemStack stack) {
        if (stack.isEmpty()) return "empty";
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String displayId = "minecraft".equals(itemId.getNamespace()) ? itemId.getPath() : itemId.toString();
        if (!stack.isDamageableItem()) {
            return displayId + (stack.getCount() > 1 ? " x" + stack.getCount() : "");
        }
        int durability = Math.max(0, Math.round(100.0f * (stack.getMaxDamage() - stack.getDamageValue())
            / stack.getMaxDamage()));
        return displayId + ", durability " + durability + "%";
    }

    // ── Equipment management ──────────────────────────────────────────

    public ItemStack getEquippedItem(EquipmentSlot slot) {
        return equipment.getOrDefault(slot, ItemStack.EMPTY);
    }

    public void equip(EquipmentSlot slot, ItemStack stack) {
        if (stack == null) stack = ItemStack.EMPTY;
        equipment.put(slot, stack.copy());
    }

    public ItemStack swapEquipment(EquipmentSlot slot, ItemStack incoming) {
        if (incoming == null) incoming = ItemStack.EMPTY;
        ItemStack previous = equipment.getOrDefault(slot, ItemStack.EMPTY);
        equipment.put(slot, incoming.copy());
        return previous;
    }

    public ItemStack getMainHandItem() {
        return equipment.get(EquipmentSlot.MAINHAND);
    }

    public ItemStack getOffhandItem() {
        return equipment.get(EquipmentSlot.OFFHAND);
    }

    public void setMainHandItem(ItemStack stack) {
        equip(EquipmentSlot.MAINHAND, stack);
    }

    public void setOffhandItem(ItemStack stack) {
        equip(EquipmentSlot.OFFHAND, stack);
    }

    public ItemStack getArmor(EquipmentSlot slot) {
        if (slot.getType() != EquipmentSlot.Type.ARMOR) {
            throw new IllegalArgumentException("Not an armor slot: " + slot);
        }
        return equipment.get(slot);
    }

    public void setArmor(EquipmentSlot slot, ItemStack stack) {
        if (slot.getType() != EquipmentSlot.Type.ARMOR) {
            throw new IllegalArgumentException("Not an armor slot: " + slot);
        }
        equip(slot, stack);
    }

    // ── Tool selection ──────────────────────────────────────────────────

    /**
     * Finds the best tool in the inventory for the given item type.
     * Returns a copy of the tool, or null if none found.
     * Does NOT consume the tool.
     */
    public ItemStack findBestTool(Tier requiredTier, Predicate<ItemStack> toolPredicate) {
        ItemStack best = null;
        int bestTier = -1;
        for (ItemStack stack : contents) {
            if (toolPredicate.test(stack) && stack.getItem() instanceof TieredItem) {
                int tierLevel = ((TieredItem) stack.getItem()).getTier().getLevel();
                if (tierLevel > bestTier) {
                    bestTier = tierLevel;
                    best = stack;
                }
            }
        }
        if (best != null && requiredTier != null && bestTier < requiredTier.getLevel()) {
            return null;
        }
        return best != null ? best.copy() : null;
    }

    /**
     * Returns the best available tool for mining the given block, or null if none.
     * Considers both main hand and inventory.
     */
    public ItemStack findBestToolForBlock(net.minecraft.world.level.block.Block block) {
        ItemStack mainHand = getMainHandItem();
        if (!mainHand.isEmpty() && isEffectiveTool(mainHand, block)) {
            return mainHand.copy();
        }
        return findBestTool(null, stack -> isEffectiveTool(stack, block));
    }

    /**
     * Equips the best tool from inventory into main hand, returning the previous item.
     * Returns null if no suitable tool found.
     */
    public ItemStack equipBestTool(net.minecraft.world.level.block.Block block) {
        ItemStack current = getMainHandItem();
        net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
        float bestSpeed = isEffectiveTool(current, block) ? current.getDestroySpeed(state) : 1.0F;
        int bestSlot = -1;
        int bestTier = current.getItem() instanceof TieredItem tiered
            ? tiered.getTier().getLevel() : -1;
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack candidate = contents.get(slot);
            if (!isEffectiveTool(candidate, block) || !(candidate.getItem() instanceof TieredItem tiered)) {
                continue;
            }
            int tier = tiered.getTier().getLevel();
            float speed = candidate.getDestroySpeed(state);
            if (speed > bestSpeed || (speed == bestSpeed && tier > bestTier)) {
                bestSpeed = speed;
                bestTier = tier;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) {
            return isEffectiveTool(current, block) ? current.copy() : null;
        }

        ItemStack previous = current.copy();
        ItemStack selected = contents.get(bestSlot).copy();
        contents.set(bestSlot, previous.isEmpty() ? ItemStack.EMPTY : previous.copy());
        setMainHandItem(selected);
        return previous;
    }

    /** Reverses a temporary main-hand swap without creating or losing a stack. */
    public boolean restoreMainHand(ItemStack previousMainHand) {
        ItemStack previous = previousMainHand == null ? ItemStack.EMPTY : previousMainHand;
        ItemStack current = getMainHandItem().copy();
        if (ItemStack.matches(current, previous)) {
            return true;
        }

        int restoreSlot = -1;
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack candidate = contents.get(slot);
            if (previous.isEmpty() ? candidate.isEmpty() : ItemStack.matches(candidate, previous)) {
                restoreSlot = slot;
                break;
            }
        }
        if (restoreSlot < 0) {
            return false;
        }

        contents.set(restoreSlot, current.isEmpty() ? ItemStack.EMPTY : current);
        setMainHandItem(previous);
        return true;
    }

    /**
     * Checks if a tool needs replacement (below 10% durability).
     */
    public boolean needsReplacement(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
            return false;
        }
        return (double) (stack.getMaxDamage() - stack.getDamageValue()) < stack.getMaxDamage() * 0.1;
    }

    /**
     * Checks if the currently equipped tool is broken.
     */
    public boolean isEquippedToolBroken() {
        ItemStack mainHand = getMainHandItem();
        return !mainHand.isEmpty() && mainHand.isDamageableItem()
            && mainHand.getDamageValue() >= mainHand.getMaxDamage();
    }

    // ── Transfer operations ─────────────────────────────────────────────

    /**
     * Attempts to give items to the inventory. Returns the overflow.
     */
    public ItemStack give(ItemStack stack) {
        return insert(stack);
    }

    /**
     * Attempts to deposit items into the inventory from an external source.
     * Returns the remainder that could not be deposited.
     */
    public ItemStack deposit(ItemStack stack) {
        return insert(stack);
    }

    /**
     * Withdraws up to `amount` of the given item from the inventory.
     * Returns the withdrawn items, or empty if not enough.
     */
    public ItemStack withdraw(Item item, int amount) {
        if (item == null || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int totalAvailable = count(item);
        if (totalAvailable < amount) {
            return ItemStack.EMPTY;
        }
        int removed = remove(item, amount);
        if (removed == 0) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, removed);
    }

    /**
     * Withdraws a specific ItemStack from the inventory.
     * Returns the withdrawn items, or empty if not available.
     */
    public ItemStack withdraw(ItemStack stack, int amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        return withdraw(stack.getItem(), Math.min(amount, stack.getCount()));
    }

    /**
     * Drops items from the inventory at the given position.
     * Returns the items that were actually dropped.
     */
    public List<ItemStack> drop(Item item, int amount) {
        if (item == null || amount <= 0) {
            return List.of();
        }
        int removed = remove(item, amount);
        if (removed == 0) {
            return List.of();
        }
        return List.of(new ItemStack(item, removed));
    }

    /**
     * Consumes one item from the inventory (e.g., food).
     * Returns true if an item was consumed.
     */
    public boolean consume(Item item) {
        return remove(item, 1) > 0;
    }

    /**
     * Consumes one of the given ItemStack from the inventory.
     * Returns true if consumed.
     */
    public boolean consume(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return consume(stack.getItem());
    }

    // ── Persistence ─────────────────────────────────────────────────────

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", DATA_VERSION);
        tag.putInt("Size", contents.size());
        ContainerHelper.saveAllItems(tag, contents);

        CompoundTag equipmentTag = new CompoundTag();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = equipment.get(slot);
            if (!stack.isEmpty()) {
                CompoundTag stackTag = new CompoundTag();
                stack.save(stackTag);
                equipmentTag.put(slot.getName(), stackTag);
            }
        }
        tag.put("Equipment", equipmentTag);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        int savedSize = tag.contains("Size") ? tag.getInt("Size") : contents.size();
        int targetSize = Math.max(contents.size(), Math.min(MAX_PERSISTED_SLOTS, savedSize));
        NonNullList<ItemStack> restored = NonNullList.withSize(targetSize, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, restored);
        contents = restored;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipment.put(slot, ItemStack.EMPTY);
        }

        if (tag.contains("Equipment", CompoundTag.TAG_COMPOUND)) {
            CompoundTag equipmentTag = tag.getCompound("Equipment");
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (equipmentTag.contains(slot.getName(), CompoundTag.TAG_COMPOUND)) {
                    CompoundTag stackTag = equipmentTag.getCompound(slot.getName());
                    ItemStack stack = ItemStack.of(stackTag);
                    if (!stack.isEmpty()) {
                        equipment.put(slot, stack);
                    }
                }
            }
        }
    }

    // ── Tool helpers ────────────────────────────────────────────────────

    private static boolean isEffectiveTool(ItemStack stack, net.minecraft.world.level.block.Block block) {
        if (stack.isEmpty()) return false;
        net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
        return stack.getDestroySpeed(state) > 1.0f;
    }

    private record SummaryEntry(int count, int durabilityPercent) {
        private SummaryEntry merge(SummaryEntry other) {
            int lowestDurability;
            if (durabilityPercent < 0) {
                lowestDurability = other.durabilityPercent;
            } else if (other.durabilityPercent < 0) {
                lowestDurability = durabilityPercent;
            } else {
                lowestDurability = Math.min(durabilityPercent, other.durabilityPercent);
            }
            return new SummaryEntry(count + other.count, lowestDurability);
        }
    }
}
