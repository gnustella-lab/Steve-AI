package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Drops items from the Steve inventory at the Steve's current position.
 */
public class DropItemAction extends BaseAction {
    private String itemName;
    private int quantity;
    private int dropped;
    private int ticksRunning;
    private static final int MAX_TICKS = 100;

    public DropItemAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        itemName = task.getStringParameter("item");
        quantity = task.getIntParameter("quantity", 1);
        dropped = 0;
        ticksRunning = 0;

        if (itemName == null || itemName.isBlank()) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Missing item parameter").build();
            return;
        }
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        if (ticksRunning > MAX_TICKS) {
            result = ActionResult.failure(ActionResult.ERROR_TIMEOUT, "Drop timeout").build();
            return;
        }

        if (dropped >= quantity) {
            result = ActionResult.success("Dropped " + dropped + " " + itemName).build();
            return;
        }

        Item targetItem = parseItem(itemName);
        if (targetItem == Items.AIR) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Unknown item: " + itemName).build();
            return;
        }

        int remaining = quantity - dropped;
        int available = steve.getSteveInventory().count(targetItem);
        if (available < remaining) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                "Need " + remaining + " more " + itemName + " but only have " + available)
                .retryable(true)
                .observation("missing_item", itemName)
                .observation("missing_quantity", Math.max(1, remaining - available))
                .build();
            return;
        }

        java.util.List<ItemStack> droppedItems = steve.getSteveInventory().drop(targetItem, remaining);
        if (droppedItems.isEmpty()) {
            result = ActionResult.failure(ActionResult.ERROR_UNKNOWN, "Could not drop items").build();
            return;
        }

        int droppedNow = 0;
        for (ItemStack stack : droppedItems) {
            droppedNow += stack.getCount();
            steve.spawnAtLocation(stack);
        }

        dropped += droppedNow;
        if (dropped < quantity) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                "Dropped only " + dropped + " of " + quantity + " " + itemName)
                .partialSuccess(dropped > 0).requiresReplanning(true)
                .observation("dropped", dropped).observation("requested", quantity).build();
            return;
        }
        result = ActionResult.success("Dropped " + dropped + " " + itemName)
            .observation("dropped", dropped).observation("requested", quantity).build();
    }

    @Override
    protected void onCancel() {
    }

    @Override
    public String getDescription() {
        return "Drop " + quantity + " " + itemName + " (" + dropped + ")";
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
