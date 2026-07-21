package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Smelts items in a furnace.
 *
 * <p>Locates or creates a furnace, adds fuel and items to smelt,
 * waits for processing, and retrieves the result.</p>
 */
public class SmeltItemAction extends BaseAction {
    private String itemName;
    private int quantity;
    private int ticksRunning;
    private int smelted;
    private static final int MAX_TICKS = 6000; // 5 minutes
    private static final int TICKS_PER_CHECK = 100;
    private BlockPos furnacePos;
    private int itemsToAdd;
    private int fuelToAdd;
    private boolean furnacePrepared;
    private boolean waitingForResults;

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
        itemsToAdd = 0;
        fuelToAdd = 0;
        furnacePrepared = false;
        waitingForResults = false;

        if (itemName == null || itemName.isBlank()) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Missing item parameter").build();
            return;
        }

        if (!(steve.level() instanceof ServerLevel serverLevel)) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Must be on server side").build();
            return;
        }

        // Find the smelting recipe for the requested item
        Item targetItem = parseItem(itemName);
        if (targetItem == Items.AIR) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Unknown item: " + itemName).build();
            return;
        }

        // Find a furnace or create one
        furnacePos = findNearbyFurnace(serverLevel);
        if (furnacePos == null) {
            if (steve.getSteveInventory().count(Items.FURNACE) > 0) {
                // Place a furnace
                furnacePos = placeFurnace(serverLevel);
            }
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
        if (!(be instanceof FurnaceBlockEntity furnace)) {
            result = ActionResult.failure(ActionResult.ERROR_UNKNOWN, "Furnace not found").build();
            return;
        }

        if (!furnacePrepared) {
            // Add fuel
            if (fuelToAdd > 0) {
                addFuel(furnace, fuelToAdd);
            }

            // Add items to smelt
            Item targetItem = parseItem(itemName);
            int available = steve.getSteveInventory().count(targetItem);
            itemsToAdd = Math.min(quantity - smelted, available);

            if (itemsToAdd <= 0) {
                result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                    "I don't have any " + itemName + " to smelt")
                    .retryable(true)
                    .build();
                return;
            }

            // Find the smelting recipe to know what to put in
            SmeltingRecipe recipe = findSmeltingRecipe(serverLevel, targetItem);
            if (recipe == null) {
                result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                    "No smelting recipe for " + itemName).build();
                return;
            }

            // Add items to the top slot
            ItemStack toSmelt = new ItemStack(targetItem, itemsToAdd);
            furnace.getItem(0).setCount(toSmelt.getCount());
            steve.getSteveInventory().remove(targetItem, itemsToAdd);

            // Add fuel
            addFuel(furnace, itemsToAdd * 2); // 2 fuel per item

            furnacePrepared = true;
            waitingForResults = true;
            return;
        }

        if (waitingForResults) {
            // Check if smelting is complete
            ItemStack smeltedOutput = furnace.getItem(2);
            if (!smeltedOutput.isEmpty()) {
                // Retrieve the result
                ItemStack retrieved = new ItemStack(smeltedOutput.getItem(), smeltedOutput.getCount());
                furnace.getItem(2).setCount(0);
                ItemStack remainder = steve.getSteveInventory().insert(retrieved);
                smelted += retrieved.getCount() - remainder.getCount();

                if (!remainder.isEmpty()) {
                    steve.spawnAtLocation(remainder);
                }

                // Reset for next batch
                furnacePrepared = false;
                waitingForResults = false;
                fuelToAdd = 0;

                if (smelted < quantity) {
                    // Prepare next batch
                    return;
                }
            }

            if (ticksRunning % TICKS_PER_CHECK == 0) {
                steve.sendChatMessage("Smelting " + itemName + ": " + smelted + "/" + quantity);
            }
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

    private BlockPos findNearbyFurnace(ServerLevel level) {
        BlockPos stevePos = steve.blockPosition();
        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = stevePos.offset(x, y, z);
                    if (level.getBlockEntity(pos) instanceof FurnaceBlockEntity) {
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
                    if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolid()) {
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

    private void addFuel(FurnaceBlockEntity furnace, int amount) {
        Item fuel = Items.COAL;
        if (steve.getSteveInventory().count(fuel) < amount) {
            fuel = Items.CHARCOAL;
            if (steve.getSteveInventory().count(fuel) < amount) {
                fuel = Items.OAK_LOG;
                if (steve.getSteveInventory().count(fuel) < 1) return;
            }
        }

        int available = steve.getSteveInventory().count(fuel);
        int toUse = Math.min(amount, available);
        steve.getSteveInventory().remove(fuel, toUse);

        ItemStack fuelStack = new ItemStack(fuel, toUse);
        furnace.getItem(1).setCount(fuelStack.getCount());
    }

    private SmeltingRecipe findSmeltingRecipe(ServerLevel level, Item item) {
        var recipeManager = level.getServer().getRecipeManager();
        List<SmeltingRecipe> recipes = recipeManager.getAllRecipesFor(RecipeType.SMELTING);

        for (SmeltingRecipe recipe : recipes) {
            for (var ingredient : recipe.getIngredients()) {
                if (ingredient.test(new ItemStack(item))) {
                    return recipe;
                }
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
