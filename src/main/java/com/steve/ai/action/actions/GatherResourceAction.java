package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class GatherResourceAction extends BaseAction {
    private String resourceType;
    private int quantity;
    private MineBlockAction miningAction;

    public GatherResourceAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        resourceType = task.getStringParameter("resource");
        quantity = task.getIntParameter("quantity", 1);

        Map<String, Object> miningParameters = new HashMap<>();
        miningParameters.put("block", normalizeResource(resourceType));
        miningParameters.put("quantity", quantity);
        miningAction = new MineBlockAction(steve, new Task("mine", miningParameters));
        miningAction.start();
    }

    @Override
    protected void onTick() {
        if (miningAction == null) {
            result = ActionResult.failure("Gathering did not initialize");
            return;
        }
        miningAction.tick();
        if (miningAction.isComplete()) {
            result = miningAction.getResult();
        }
    }

    @Override
    protected void onCancel() {
        if (miningAction != null) {
            miningAction.cancel();
        }
        steve.getNavigation().stop();
    }

    @Override
    public String getDescription() {
        int requestedQuantity = quantity > 0
            ? quantity
            : task.getIntParameter("quantity", 1);
        String resource = resourceType != null
            ? resourceType
            : task.getStringParameter("resource");
        return "Gather " + requestedQuantity + " " + resource;
    }

    private String normalizeResource(String resource) {
        String normalized = resource.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return switch (normalized) {
            case "diamond", "diamonds" -> "diamond_ore";
            case "iron" -> "iron_ore";
            case "coal" -> "coal_ore";
            case "gold" -> "gold_ore";
            case "redstone" -> "redstone_ore";
            case "lapis", "lapis_lazuli" -> "lapis_ore";
            case "emerald", "emeralds" -> "emerald_ore";
            default -> normalized;
        };
    }
}

