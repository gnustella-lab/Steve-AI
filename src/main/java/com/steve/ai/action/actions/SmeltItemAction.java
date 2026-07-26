package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.inventory.SteveInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Smelts items in a furnace using the actual server-side furnace block entity.
 *
 * <p>Resolves the smelting recipe by the requested output item (e.g. {@code iron_ingot}),
 * consumes input items from the Steve inventory only when the furnace accepts them,
 * stacks fuel additively, and waits tick-by-tick for the server furnace to cook. Result
 * stacks are pulled into the inventory without spawning slots in mid-air.</p>
 *
 * <p>Threading: every state transition happens on the server thread. No {@code Thread.sleep}
 * is used; polling is bounded by {@link #MAX_TICKS}.</p>
 */
public class SmeltItemAction extends BaseAction {
    private String itemName;
    private int quantity;
    private int ticksRunning;
    private int smelted;
    private static final int MAX_TICKS = 12_000; // 10 minutes
    private static final int TICKS_PER_CHECK = 60;
    private BlockPos furnacePos;
    private boolean furnacePrepared;

    private static final int SLOT_INPUT = 0;
    private static final int SLOT_FUEL = 1;
    private static final int SLOT_OUTPUT = 2;

    public SmeltItemAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        itemName = task.getStringParameter("item");
        quantity = task.getIntParameter("quantity", 1);
        ticksRunning = 0;
        smelted = 0;
        furnacePos = null;
        furnacePrepared = false;

        if (itemName == null || itemName.isBlank()) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                "Missing item parameter").build();
            return;
        }

        if (!(steve.level() instanceof ServerLevel serverLevel)) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                "Must be on server side").build();
            return;
        }

        Item targetItem = parseItem(itemName);
        if (targetItem == Items.AIR) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                "Unknown item: " + itemName).build();
            return;
        }

        furnacePos = findNearbyFurnace(serverLevel);
        if (furnacePos == null && steve.getSteveInventory().count(Items.FURNACE) > 0) {
            furnacePos = placeFurnace(serverLevel);
        }

        if (furnacePos == null) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                "No furnace available. I need a furnace to smelt items.")
                .retryable(true)
                .build();
            return;
        }

        steve.sendChatMessage("I'll smelt " + quantity + " " + itemName + " in a furnace.");
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        if (ticksRunning > MAX_TICKS) {
            result = ActionResult.failure(ActionResult.ERROR_TIMEOUT, "Smelting timeout").build();
            return;
        }

        if (smelted >= quantity) {
            result = ActionResult.success("Smelted " + smelted + " " + itemName).build();
            return;
        }

        if (!(steve.level() instanceof ServerLevel serverLevel)) {
            result = ActionResult.failure(ActionResult.ERROR_UNKNOWN, "Server level required").build();
            return;
        }

        BlockEntity be = serverLevel.getBlockEntity(furnacePos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) {
            result = ActionResult.failure(ActionResult.ERROR_ENTITY_GONE,
                "Furnace not found at " + furnacePos).build();
            return;
        }

        ItemStack outputStack = furnace.getItem(SLOT_OUTPUT);
        if (!outputStack.isEmpty()) {
            int taken = Math.min(outputStack.getCount(), quantity - smelted);
            ItemStack retrieved = outputStack.copy();
            retrieved.setCount(taken);
            outputStack.shrink(taken);
            furnace.setChanged();
            ItemStack remainder = steve.getSteveInventory().insert(retrieved);
            int inserted = taken - (remainder.isEmpty() ? 0 : remainder.getCount());
            smelted += inserted;
            if (!remainder.isEmpty()) {
                steve.spawnAtLocation(remainder);
            }
            furnacePrepared = false; // re-check whether more items are queued
            return;
        }

        if (!furnacePrepared) {
            ItemStack existingInput = furnace.getItem(SLOT_INPUT);
            if (existingInput.isEmpty()) {
                int queued = queueInput(serverLevel, furnace);
                if (queued <= 0 && smelted < quantity) {
                    result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                        "I don't have any ingredient that smelts into " + itemName)
                        .retryable(true).build();
                    return;
                }
            }
            queueFuel(furnace);
            furnacePrepared = true;
            furnace.setChanged();
            return;
        }

        if (ticksRunning % TICKS_PER_CHECK == 0) {
            steve.sendChatMessage("Smelting " + itemName + ": " + smelted + "/" + quantity);
        }
    }

    @Override
    protected void onCancel() {
        steve.getNavigation().stop();
    }

    @Override
    public String getDescription() {
        return "Smelt " + quantity + " " + itemName + " (" + smelted + ")";
    }

    /**
     * Queues one stack of the smelting input that produces the requested output item.
     * Returns the number of items queued, or 0 if no ingredient is available.
     */
    private int queueInput(ServerLevel serverLevel, AbstractFurnaceBlockEntity furnace) {
        Item targetItem = parseItem(itemName);
        AbstractCookingRecipe recipe = findCookingRecipeByOutput(serverLevel, targetItem);
        if (recipe == null) {
            return 0;
        }
        Ingredient input = recipe.getIngredients().isEmpty() ? null : recipe.getIngredients().get(0);
        if (input == null) {
            return 0;
        }
        SteveInventory inventory = steve.getSteveInventory();
        int available = 0;
        Item chosenInput = null;
        for (ItemStack candidate : inventory.getContents()) {
            if (input.test(candidate)) {
                available += candidate.getCount();
                chosenInput = candidate.getItem();
            }
        }
        if (available <= 0 || chosenInput == null) {
            return 0;
        }
        int toQueue = Math.min(Math.min(available, 64), Math.max(0, quantity - smelted));
        if (toQueue <= 0) {
            return 0;
        }
        int removed = inventory.remove(chosenInput, toQueue);
        if (removed <= 0) {
            return 0;
        }
        ItemStack existing = furnace.getItem(SLOT_INPUT);
        if (existing.isEmpty()) {
            furnace.setItem(SLOT_INPUT, new ItemStack(chosenInput, removed));
        } else if (existing.getItem() == chosenInput) {
            existing.grow(removed);
        } else {
            inventory.insert(new ItemStack(chosenInput, removed));
            return 0;
        }
        return removed;
    }

    /**
     * Refills the fuel slot additively without destroying the existing fuel item.
     */
    private void queueFuel(AbstractFurnaceBlockEntity furnace) {
        ItemStack currentFuel = furnace.getItem(SLOT_FUEL);
        if (!currentFuel.isEmpty()) {
            return;
        }
        SteveInventory inv = steve.getSteveInventory();
        Item fuel = chooseFuel(inv);
        if (fuel == null) {
            return;
        }
        int available = inv.count(fuel);
        int toUse = Math.min(Math.max(1, available / 8), 16);
        if (toUse <= 0) {
            return;
        }
        int removed = inv.remove(fuel, toUse);
        if (removed <= 0) {
            return;
        }
        furnace.setItem(SLOT_FUEL, new ItemStack(fuel, removed));
    }

    private Item chooseFuel(SteveInventory inv) {
        if (inv.count(Items.COAL) > 0) return Items.COAL;
        if (inv.count(Items.CHARCOAL) > 0) return Items.CHARCOAL;
        if (inv.count(Items.OAK_PLANKS) > 0) return Items.OAK_PLANKS;
        if (inv.count(Items.STICK) > 0) return Items.STICK;
        if (inv.count(Items.OAK_LOG) > 0) return Items.OAK_LOG;
        return null;
    }

    private BlockPos findNearbyFurnace(ServerLevel level) {
        BlockPos stevePos = steve.blockPosition();
        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = stevePos.offset(x, y, z);
                    if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private BlockPos placeFurnace(ServerLevel level) {
        BlockPos stevePos = steve.blockPosition();
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos pos = stevePos.offset(x, y, z);
                    if (level.getBlockState(pos).isAir()
                            && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                        if (level.setBlock(pos, Blocks.FURNACE.defaultBlockState(), 3)) {
                            steve.getSteveInventory().remove(Items.FURNACE, 1);
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private AbstractCookingRecipe findCookingRecipeByOutput(ServerLevel level, Item outputItem) {
        List<SmeltingRecipe> smelting = level.getServer().getRecipeManager()
            .getAllRecipesFor(RecipeType.SMELTING);
        ItemStack expected = new ItemStack(outputItem);
        for (AbstractCookingRecipe candidate : smelting) {
            try {
                ItemStack candidateResult = candidate.getResultItem(level.registryAccess());
                if (!candidateResult.isEmpty() && ItemStack.isSameItemSameTags(candidateResult, expected)) {
                    return candidate;
                }
            } catch (Throwable ignored) {
                continue;
            }
        }
        return null;
    }

    private Item parseItem(String name) {
        if (name == null || name.isBlank()) return Items.AIR;
        name = name.toLowerCase().replace(" ", "_");
        if (!name.contains(":")) {
            name = "minecraft:" + name;
        }
        ResourceLocation rl = ResourceLocation.tryParse(name);
        return rl != null ? BuiltInRegistries.ITEM.get(rl) : Items.AIR;
    }
}
