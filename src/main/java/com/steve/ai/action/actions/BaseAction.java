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
        try {
            onStart();
        } catch (RuntimeException exception) {
            failFromUnhandledException(exception);
        } finally {
            finishIfComplete();
        }
    }

    public void tick() {
        if (!started || isComplete()) return;
        try {
            onTick();
        } catch (RuntimeException exception) {
            failFromUnhandledException(exception);
        } finally {
            finishIfComplete();
        }
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

    private void failFromUnhandledException(RuntimeException exception) {
        String type = exception == null ? "RuntimeException" : exception.getClass().getSimpleName();
        result = ActionResult.failure(ActionResult.ERROR_UNKNOWN,
            "Action failed with " + type).requiresReplanning(true).build();
        try {
            onCancel();
        } catch (RuntimeException ignored) {
            // Preserve the original structured failure. onFinish still gets one chance to cleanup.
        }
    }
    
    public abstract String getDescription();
}
