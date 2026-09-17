package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.autonomy.LocalCommands;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.execution.ActionContext;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.security.PermissionManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/**
 * LLM-invoked offline command: one local craft/gather/smelt step, no extra LLM call.
 */
public class LocalCommandAction extends BaseAction {
    private final ActionContext context;
    private BaseAction inner;

    public LocalCommandAction(SteveEntity steve, Task task, ActionContext context) {
        super(steve, task);
        this.context = context;
    }

    @Override
    protected void onStart() {
        Task expanded = LocalCommands.expand(task, LocalCommandAction::registeredItem, this::countItem);
        if (expanded == null || expanded.getAction() == null
                || "local".equalsIgnoreCase(expanded.getAction())) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                "Not a local offline command").retryable(false).build();
            return;
        }
        if (!"build".equalsIgnoreCase(expanded.getAction())
                && expanded.getIntParameter("quantity", 0) == 0) {
            result = ActionResult.success("Already satisfied: " + expanded.getAction()).build();
            return;
        }
        if (!PermissionManager.getInstance().canExecute(steve.getUUID(), steve.getSteveName(), expanded.getAction())) {
            result = ActionResult.failure(ActionResult.ERROR_PERMISSION_DENIED,
                "Not allowed to " + expanded.getAction()).build();
            return;
        }
        inner = ActionRegistry.getInstance().createAction(expanded.getAction(), steve, expanded, context);
        if (inner == null) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION,
                "No factory for " + expanded.getAction()).requiresReplanning(true).build();
            return;
        }
        inner.start();
        if (inner.isComplete()) {
            result = inner.getResult();
        }
    }

    @Override
    protected void onTick() {
        if (inner == null) {
            return;
        }
        inner.tick();
        if (inner.isComplete()) {
            result = inner.getResult();
        }
    }

    @Override
    protected void onCancel() {
        if (inner != null) {
            inner.cancel();
        }
    }

    @Override
    public String getDescription() {
        String command = task.getStringParameter("command");
        return "Local " + (command == null ? "command" : command);
    }

    private int countItem(String item) {
        ResourceLocation id = ResourceLocation.tryParse(item);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return 0;
        }
        return steve.getSteveInventory().count(BuiltInRegistries.ITEM.get(id));
    }

    private static boolean registeredItem(String item) {
        ResourceLocation id = ResourceLocation.tryParse(item);
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
            && BuiltInRegistries.ITEM.get(id) != Items.AIR;
    }
}
