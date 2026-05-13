package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;

public class GatherResourceAction extends BaseAction {
    private String resourceType;
    private int quantity;

    public GatherResourceAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        resourceType = task.getStringParameter("resource");
        quantity = task.getIntParameter("quantity", 1);
        
        // This is essentially a smart wrapper around mining that:
        // - Mines them
        
        result = ActionResult.failure("Resource gathering not yet fully implemented", false);
    }

    @Override
    protected void onTick() {
    }

    @Override
    protected void onCancel() {
        steve.getNavigation().stop();
    }

    @Override
    public String getDescription() {
        return "Gather " + quantity + " " + resourceType;
    }
}

