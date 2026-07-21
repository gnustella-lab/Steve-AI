package com.steve.ai.action;

import com.steve.ai.plugin.ActionRegistry;

/**
 * Valida tarefas geradas por LLM antes que elas alcancem a API do Minecraft.
 */
public final class TaskValidator {
    private TaskValidator() {
    }

    /** Validates against the same descriptor that owns the action factory and prompt metadata. */
    public static boolean isValid(Task task) {
        return ActionRegistry.getInstance().validate(task).isValid();
    }
}
