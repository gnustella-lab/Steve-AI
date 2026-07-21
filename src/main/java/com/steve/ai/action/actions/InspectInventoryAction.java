package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;

/**
 * Inspects the Steve inventory and reports a summary. This action always succeeds
 * and produces an observation containing the inventory summary for the planner.
 */
public class InspectInventoryAction extends BaseAction {
    private int ticksRunning;
    private static final int MAX_TICKS = 10;

    public InspectInventoryAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        ticksRunning = 0;
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        if (ticksRunning > MAX_TICKS) {
            result = ActionResult.failure(ActionResult.ERROR_TIMEOUT, "Inspect timeout").build();
            return;
        }

        String summary = steve.getSteveInventory().summarize(20);
        String equipment = steve.getSteveInventory().summarizeEquipment();

        result = ActionResult.success("Inventory inspected")
            .observation("inventory", summary)
            .observation("equipment", equipment)
            .build();
    }

    @Override
    protected void onCancel() {
    }

    @Override
    public String getDescription() {
        return "Inspect inventory";
    }
}
