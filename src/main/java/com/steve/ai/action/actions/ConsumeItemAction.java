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
 * Consumes one item from the Steve inventory (e.g., food for healing).
 */
public class ConsumeItemAction extends BaseAction {
    private String itemName;
    private int ticksRunning;
    private static final int MAX_TICKS = 100;

    public ConsumeItemAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        itemName = task.getStringParameter("item");
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
            result = ActionResult.failure(ActionResult.ERROR_TIMEOUT, "Consume timeout").build();
            return;
        }

        Item targetItem = parseItem(itemName);
        if (targetItem == Items.AIR) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Unknown item: " + itemName).build();
            return;
        }

        if (!steve.getSteveInventory().consume(targetItem)) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE, "I don't have any " + itemName).build();
            return;
        }

        result = ActionResult.success("Consumed " + itemName).build();
    }

    @Override
    protected void onCancel() {
    }

    @Override
    public String getDescription() {
        return "Consume " + itemName;
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
