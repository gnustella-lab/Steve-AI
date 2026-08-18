package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;

public abstract class BaseAction {
    protected final SteveEntity steve;
    protected final Task task;
    protected ActionResult result;
    protected boolean started = false;
    protected boolean cancelled = false;
    private boolean completionHandled = false;

    public BaseAction(SteveEntity steve, Task task) {
        this.steve = steve;
        this.task = task;
    }

    public void start() {
        if (started) return;
        started = true;
        onStart();
        finishIfComplete();
    }

    public void tick() {
        if (!started || isComplete()) return;
        onTick();
        finishIfComplete();
    }

    public void cancel() {
        if (cancelled) return;
        cancelled = true;
        result = ActionResult.failure(ActionResult.ERROR_CANCELLED, "Action cancelled").build();
        try {
            onCancel();
        } finally {
            finishIfComplete();
        }
    }

    public boolean isComplete() {
        return result != null || cancelled;
    }

    public ActionResult getResult() {
        return result;
    }

    /** Returns the task that created this action. */
    public Task getTask() {
        return task;
    }

    protected abstract void onStart();
    protected abstract void onTick();
    protected abstract void onCancel();

    /** Called exactly once after success, failure, or cancellation. */
    protected void onFinish() {
    }

    private void finishIfComplete() {
        if (!completionHandled && isComplete()) {
            completionHandled = true;
            onFinish();
        }
    }
    
    public abstract String getDescription();
}

