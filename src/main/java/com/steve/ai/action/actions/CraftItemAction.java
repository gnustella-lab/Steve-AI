package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.crafting.CraftingPlanner;
import com.steve.ai.crafting.IngredientResolver;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.inventory.SteveInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Crafts an item using the Minecraft recipe system.
 *
 * <p>Plans the crafting steps via {@link CraftingPlanner}, locates or crafts a crafting table
 * when needed, consumes ingredients in the exact quantities and order dictated by the recipe
 * graph, and commits the {@link Recipe#getResultItem} output to the inventory. Consume and
 * produce happen on the server thread; partial runs are rolled back atomically so no material
 * is ever duplicated or lost on failure or cancellation.</p>
 */
public class CraftItemAction extends BaseAction {
    private String itemName;
    private int quantity;
    private int ticksRunning;
    private int crafted;
    private CraftingPlanner.CraftPlan plan;
    private int currentStepIndex;
    private static final int MAX_TICKS = 6_000;
    private static final int TICKS_PER_STEP = 20;

    public CraftItemAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        itemName = task.getStringParameter("item");
        quantity = task.getIntParameter("quantity", 1);
        ticksRunning = 0;
        crafted = 0;
        currentStepIndex = 0;

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

        plan = CraftingPlanner.plan(itemName, quantity, steve.getSteveInventory(), serverLevel);

        if (plan == null || !plan.achievable()) {
            String reason = plan != null ? plan.failureReason() : "No plan generated";
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                "Cannot craft " + itemName + ": " + reason)
                .retryable(false)
                .build();
            return;
        }

        steve.sendChatMessage("I'll craft " + quantity + " " + itemName
            + " in " + plan.getTotalSteps() + " steps.");
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        if (ticksRunning > MAX_TICKS) {
            steve.setFlying(false);
            result = ActionResult.failure(ActionResult.ERROR_TIMEOUT, "Crafting timeout").build();
            return;
        }

        if (crafted >= quantity) {
            result = ActionResult.success("Crafted " + crafted + " " + itemName).build();
            return;
        }

        if (plan == null || plan.steps().isEmpty()) {
            result = ActionResult.failure(ActionResult.ERROR_UNKNOWN, "No crafting plan").build();
            return;
        }

        if (currentStepIndex >= plan.steps().size()) {
            if (crafted > 0) {
                result = ActionResult.success("Crafted " + crafted + " " + itemName)
                    .partialSuccess(crafted < quantity).build();
            } else {
                result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                    "Ran out of steps before producing any " + itemName).build();
            }
            return;
        }

        CraftingPlanner.CraftStep step = plan.steps().get(currentStepIndex);

        if (!(steve.level() instanceof ServerLevel serverLevel)) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                "Server level required").build();
            return;
        }

        SteveInventory inventory = steve.getSteveInventory();

        int totalNeeded = step.timesToCraft();
        IngredientResolver.Resolution resolution =
            IngredientResolver.resolve(inventory, step.ingredients());
        int canCraftNow = computeMaxIterations(inventory, step.ingredients(), totalNeeded);

        if (canCraftNow <= 0) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                "Missing ingredients for " + step.resultItem())
                .retryable(true)
                .build();
            return;
        }

        int iterations = Math.min(canCraftNow, totalNeeded);
        List<ItemStack> consumed = IngredientResolver.consume(
            inventory, step.ingredients(), iterations);
        if (consumed.isEmpty() && iterations > 0) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                "Could not commit ingredients for " + step.resultItem())
                .retryable(true)
                .build();
            return;
        }

        if (step.needsCraftingTable() && !findOrCreateCraftingTable()) {
            IngredientResolver.restore(inventory, consumed);
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                "No crafting table available")
                .retryable(true)
                .build();
            return;
        }

        Item resultItem = parseItem(step.resultItem());
        if (resultItem == Items.AIR) {
            IngredientResolver.restore(inventory, consumed);
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                "Unknown result item: " + step.resultItem()).build();
            return;
        }

        int producedPerIteration = step.resultCount();
        ItemStack authoritative = computeAuthoritativeOutput(step, serverLevel);
        if (!authoritative.isEmpty() && authoritative.getCount() > 0) {
            producedPerIteration = authoritative.getCount();
        }
        int totalProduced = producedPerIteration * iterations;
        if (totalProduced <= 0) {
            IngredientResolver.restore(inventory, consumed);
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                "Recipe for " + step.resultItem() + " produced no output").build();
            return;
        }

        ItemStack resultStack = authoritative.isEmpty()
            ? new ItemStack(resultItem, totalProduced)
            : authoritative.copy();
        resultStack.setCount(totalProduced);

        ItemStack remainder = inventory.insert(resultStack);
        int added = totalProduced - (remainder.isEmpty() ? 0 : remainder.getCount());
        int actuallyProduced = added;
        crafted += actuallyProduced;
        currentStepIndex++;

        if (!remainder.isEmpty()) {
            steve.spawnAtLocation(remainder);
        }

        if (step.resultItem().equalsIgnoreCase(itemName)
                || step.resultItem().equalsIgnoreCase(normalize(itemName))) {
            int targetTicks = totalProduced == 0 ? TICKS_PER_STEP
                : Math.max(1, TICKS_PER_STEP);
            if (ticksRunning % targetTicks == 0) {
                steve.sendChatMessage("Crafting " + itemName + ": "
                    + crafted + "/" + quantity);
            }
        }
    }

    @Override
    protected void onCancel() {
        steve.setFlying(false);
        steve.getNavigation().stop();
    }

    @Override
    public String getDescription() {
        return "Craft " + quantity + " " + itemName + " (" + crafted + ")";
    }

    private int computeMaxIterations(SteveInventory inventory,
            List<IngredientResolver.IngredientQuantity> ingredients, int desired) {
        int max = desired;
        for (IngredientResolver.IngredientQuantity iq : ingredients) {
            int available = countIngredient(inventory, iq);
            int perIteration = iq.quantity();
            if (perIteration <= 0) continue;
            int can = available / perIteration;
            if (can < max) {
                max = can;
            }
        }
        return Math.max(0, max);
    }

    private int countIngredient(SteveInventory inventory,
            IngredientResolver.IngredientQuantity iq) {
        if (iq.ingredient() == null) return 0;
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (iq.ingredient().test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private ItemStack computeAuthoritativeOutput(CraftingPlanner.CraftStep step,
            ServerLevel serverLevel) {
        Recipe<?> recipe = step.recipe();
        if (recipe != null) {
            try {
                ItemStack result = recipe.getResultItem(serverLevel.registryAccess());
                if (!result.isEmpty()) {
                    return result;
                }
            } catch (Throwable ignored) {
                // Fall through to the synthetic ItemStack below.
            }
        }
        Item item = parseItem(step.resultItem());
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, step.resultCount());
    }

    private boolean findOrCreateCraftingTable() {
        if (!(steve.level() instanceof ServerLevel serverLevel)) return false;

        if (steve.getSteveInventory().count(Items.CRAFTING_TABLE) > 0) {
            return true;
        }

        BlockPos stevePos = steve.blockPosition();
        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = stevePos.offset(x, y, z);
                    if (serverLevel.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                        return true;
                    }
                }
            }
        }

        if (steve.getSteveInventory().count(Items.OAK_PLANKS) >= 4
            || steve.getSteveInventory().count(Items.SPRUCE_PLANKS) >= 4
            || steve.getSteveInventory().count(Items.BIRCH_PLANKS) >= 4) {
            Item planks = Items.OAK_PLANKS;
            if (steve.getSteveInventory().count(planks) < 4) {
                planks = Items.SPRUCE_PLANKS;
                if (steve.getSteveInventory().count(planks) < 4) {
                    planks = Items.BIRCH_PLANKS;
                }
            }
            steve.getSteveInventory().remove(planks, 4);
            steve.getSteveInventory().insert(new ItemStack(Items.CRAFTING_TABLE));
            return true;
        }

        return false;
    }

    private String normalize(String name) {
        if (name == null) return "";
        String normalized = name.toLowerCase().replace(" ", "_");
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
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
