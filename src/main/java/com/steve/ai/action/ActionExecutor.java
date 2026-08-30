package com.steve.ai.action;

import com.steve.ai.SteveMod;
import com.steve.ai.action.actions.BaseAction;
import com.steve.ai.action.actions.IdleFollowAction;

import com.steve.ai.di.ServiceContainer;
import com.steve.ai.di.SimpleServiceContainer;
import com.steve.ai.event.EventBus;
import com.steve.ai.event.SimpleEventBus;
import com.steve.ai.execution.*;
import com.steve.ai.llm.ResponseParser;
import com.steve.ai.llm.TaskPlanner;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.plugin.PluginManager;
import com.steve.ai.security.PermissionManager;
import com.steve.ai.planning.Plan;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Executes actions for a Steve entity using the plugin-based action system.
 *
 * <p><b>Architecture:</b></p>
 * <ul>
 *   <li>Uses ActionRegistry for dynamic action creation (Factory + Registry patterns)</li>
 *   <li>Uses InterceptorChain for cross-cutting concerns (logging, metrics, events)</li>
 *   <li>Uses AgentStateMachine for explicit state management</li>
 *   <li>Requires every executable action to own one registry entry and descriptor</li>
 * </ul>
 *
 * @since 1.1.0
 */
public class ActionExecutor {
    private final SteveEntity steve;
    private TaskPlanner taskPlanner;  // Lazy-initialized to avoid loading dependencies on entity creation
    private final Queue<Task> taskQueue;

    private BaseAction currentAction;
    private String currentGoal;
    private int ticksSinceLastAction;
    private BaseAction idleFollowAction;  // Follow player when idle
    private ActionStart startedAction;

    // Async planning state. Completion is polled without blocking the server thread.
    private CompletableFuture<ResponseParser.ParsedResponse> planningFuture;
    private boolean isPlanning = false;
    private String pendingCommand;  // Store command while planning
    private UUID controllingPlayerUuid;

    // When true, AutonomyController owns goals, recovery, replanning, and completion.
    private boolean autonomyManaged;
    private ActionCompletion completedAction;
    private int commandPathAttempts;
    private static final int MAX_COMMAND_PATH_RETRIES = 3;

    /** Bounded recovery for the command path, when AutonomyController does not own the runtime. */
    public enum CommandPathDecision {
        RETRY,
        SKIP,
        ABORT
    }

    // Plugin architecture components
    private final ActionContext actionContext;
    private final InterceptorChain interceptorChain;
    private final AgentStateMachine stateMachine;
    private final SimpleEventBus eventBus;

    public ActionExecutor(SteveEntity steve) {
        this.steve = steve;
        this.taskPlanner = null;  // Will be initialized when first needed
        this.taskQueue = new LinkedList<>();
        this.ticksSinceLastAction = 0;
        this.idleFollowAction = null;
        this.startedAction = null;
        this.planningFuture = null;
        this.pendingCommand = null;
        this.autonomyManaged = false;
        this.completedAction = null;
        this.commandPathAttempts = 0;

        // Initialize plugin architecture components
        this.eventBus = new SimpleEventBus();
        this.stateMachine = new AgentStateMachine(eventBus, steve.getSteveName());
        this.interceptorChain = new InterceptorChain();

        // Setup interceptors
        interceptorChain.addInterceptor(new LoggingInterceptor());
        interceptorChain.addInterceptor(new MetricsInterceptor());
        interceptorChain.addInterceptor(new EventPublishingInterceptor(eventBus, steve.getSteveName()));

        // Build action context
        ServiceContainer container = SteveMod.getServiceContainer();
        if (container == null) {
            container = new SimpleServiceContainer();
        }
        this.actionContext = ActionContext.builder()
            .serviceContainer(container)
            .eventBus(eventBus)
            .stateMachine(stateMachine)
            .interceptorChain(interceptorChain)
            .build();

        SteveMod.LOGGER.debug("ActionExecutor initialized with plugin architecture for Steve '{}'",
            steve.getSteveName());
    }
    
    /** Immutable start handoff consumed by the executive on the server tick. */
    public record ActionStart(Task task, String description) {
        public ActionStart {
            java.util.Objects.requireNonNull(task, "task");
            description = BoundedData.boundedString(description);
        }
    }

    /** Immutable completion event consumed by the executive on the server tick. */
    public record ActionCompletion(Task task, ActionResult result, String description) {
        public ActionCompletion {
            java.util.Objects.requireNonNull(task, "task");
            java.util.Objects.requireNonNull(result, "result");
            description = BoundedData.boundedString(description);
        }
    }

    /** Hands a bounded plan horizon to the runtime without transferring cognitive ownership to it. */
    public void acceptAutonomousPlan(Plan plan) {
        if (plan == null) return;
        autonomyManaged = true;
        if (currentAction != null) cancelCurrentAction();
        taskQueue.clear();
        completedAction = null;
        startedAction = null;
        for (Task task : plan.getTasks()) {
            if (task != null) taskQueue.offer(task);
        }
        currentGoal = plan.getSummary() == null ? plan.getOriginalCommand() : plan.getSummary();
        if (stateMachine.getCurrentState() == AgentState.PLANNING) {
            stateMachine.transitionTo(AgentState.EXECUTING, "autonomous horizon accepted");
        }
    }

    public boolean isAutonomyManaged() {
        return autonomyManaged;
    }

    public ActionCompletion consumeCompletedAction() {
        ActionCompletion completion = completedAction;
        completedAction = null;
        return completion;
    }

    /** Returns and clears the most recently accepted action start, if any. */
    public ActionStart consumeStartedAction() {
        ActionStart start = startedAction;
        startedAction = null;
        return start;
    }

    public String getCurrentActionDescription() {
        return currentAction == null ? "" : currentAction.getDescription();
    }

    public boolean hasPendingAutonomousTasks() {
        return autonomyManaged && (currentAction != null || !taskQueue.isEmpty());
    }

    /** Cancels transient runtime state without cancelling the persisted goal itself. */
    public void stopAutonomousExecution() {
        if (planningFuture != null) planningFuture.cancel(true);
        planningFuture = null;
        isPlanning = false;
        pendingCommand = null;
        cancelCurrentAction();
        taskQueue.clear();
        completedAction = null;
        startedAction = null;
        autonomyManaged = false;
        currentGoal = null;
        commandPathAttempts = 0;
    }

    private void publishCompletion(Task task, ActionResult result, String description) {
        completedAction = new ActionCompletion(task, result, description);
    }

    private TaskPlanner getTaskPlanner() {
        if (taskPlanner == null) {
            SteveMod.LOGGER.info("Initializing TaskPlanner for Steve '{}'", steve.getSteveName());
            taskPlanner = new TaskPlanner();
        }
        return taskPlanner;
    }

    /**
     * Processes a natural language command using ASYNC non-blocking LLM calls.
     *
     * <p>This method returns immediately and does NOT block the game thread.
     * The LLM response is processed in tick() when the CompletableFuture completes.</p>
     *
     * <p><b>Non-blocking flow:</b></p>
     * <ol>
     *   <li>User sends command</li>
     *   <li>This method starts async LLM call, returns immediately</li>
     *   <li>Game continues running normally (no freeze!)</li>
     *   <li>tick() checks if planning is done</li>
     *   <li>When done, tasks are queued and execution begins</li>
     * </ol>
     *
     * @param command The natural language command from the user
     */
    public void processNaturalLanguageCommand(String command) {
        processNaturalLanguageCommand(command, null);
    }

    /**
     * Processes a command while retaining its authorized controller for player-relative actions.
     *
     * @param command natural-language command
     * @param controllerUuid authorized player UUID, or null for console and legacy callers
     */
    public void processNaturalLanguageCommand(String command, UUID controllerUuid) {
        SteveMod.LOGGER.info("Steve '{}' processing command (async): {}", steve.getSteveName(), command);

        if (command == null || command.isBlank()) {
            sendToGUI(steve.getSteveName(), "Please provide a command.");
            return;
        }

        controllingPlayerUuid = controllerUuid;
        autonomyManaged = false;
        completedAction = null;
        commandPathAttempts = 0;

        // A newer command supersedes any plan still in flight.
        if (isPlanning) {
            SteveMod.LOGGER.info("Steve '{}' replacing the pending plan with a newer command",
                steve.getSteveName());
            if (planningFuture != null) {
                planningFuture.cancel(true);
            }
            planningFuture = null;
            pendingCommand = null;
            isPlanning = false;
        }

        // Cancel any current actions
        cancelCurrentAction();

        if (idleFollowAction != null) {
            idleFollowAction.cancel();
            idleFollowAction = null;
        }

        try {
            // Store command and start async planning
            // Uma nova ordem substitui integralmente o plano anterior.
            taskQueue.clear();
            clearCurrentGoal();
            stateMachine.reset();
            stateMachine.transitionTo(AgentState.PLANNING, "new command");
            this.pendingCommand = command;
            this.isPlanning = true;

            // Send immediate feedback to user
            sendToGUI(steve.getSteveName(), "Thinking...");

            // Start async LLM call - returns immediately!
            planningFuture = getTaskPlanner().planTasksAsync(steve, command);

            SteveMod.LOGGER.info("Steve '{}' started async planning for: {}", steve.getSteveName(), command);

        } catch (NoClassDefFoundError e) {
            failAndResetState("AI components unavailable");
            SteveMod.LOGGER.error("Failed to initialize AI components", e);
            sendToGUI(steve.getSteveName(), "Sorry, I'm having trouble with my AI systems!");
            isPlanning = false;
            planningFuture = null;
        } catch (Exception e) {
            failAndResetState("planning startup failed");
            SteveMod.LOGGER.error("Error starting async planning", e);
            sendToGUI(steve.getSteveName(), "Oops, something went wrong!");
            isPlanning = false;
            planningFuture = null;
        }
    }

    /** Returns the player whose accepted command owns the current plan, if any. */
    public UUID getControllingPlayerUuid() {
        return controllingPlayerUuid;
    }

    /** Sets the identity associated with the currently accepted goal. */
    public void setControllingPlayerUuid(UUID controllerUuid) {
        this.controllingPlayerUuid = controllerUuid;
    }

    /**
     * Legacy entry point retained for compatibility. Delegates to the non-blocking planner.
     *
     * @param command The natural language command
     * @deprecated Use {@link #processNaturalLanguageCommand(String)} instead
     */
    @Deprecated
    public void processNaturalLanguageCommandSync(String command) {
        processNaturalLanguageCommand(command);
    }
    
    /** Envia feedback pelo chat do servidor quando habilitado. */
    private void sendToGUI(String steveName, String message) {
        if (!steve.level().isClientSide && SteveConfig.ENABLE_CHAT_RESPONSES.get()) {
            steve.sendFeedback(message);
        }
    }

    public void tick() {
        ticksSinceLastAction++;

        // Check if async planning is complete (non-blocking check!)
        if (isPlanning && planningFuture != null && planningFuture.isDone()) {
            try {
                ResponseParser.ParsedResponse response = planningFuture.get();

                if (response != null) {
                    applyPlan(response);

                    if (SteveConfig.ENABLE_CHAT_RESPONSES.get() && !taskQueue.isEmpty()) {
                        sendToGUI(steve.getSteveName(), "Okay! " + currentGoal);
                    }

                    SteveMod.LOGGER.info("Steve '{}' async planning complete: {} tasks queued",
                        steve.getSteveName(), taskQueue.size());
                } else {
                    failAndResetState("planner returned no response");
                    sendToGUI(steve.getSteveName(), "I couldn't understand that command.");
                    SteveMod.LOGGER.warn("Steve '{}' async planning returned null response", steve.getSteveName());
                }

            } catch (java.util.concurrent.CancellationException e) {
                stateMachine.reset();
                SteveMod.LOGGER.info("Steve '{}' planning was cancelled", steve.getSteveName());
                sendToGUI(steve.getSteveName(), "Planning cancelled.");
            } catch (Exception e) {
                failAndResetState("planning failed");
                SteveMod.LOGGER.error("Steve '{}' failed to get planning result", steve.getSteveName(), e);
                sendToGUI(steve.getSteveName(), "Oops, something went wrong while planning!");
            } finally {
                isPlanning = false;
                planningFuture = null;
                pendingCommand = null;
            }
        }

        if (currentAction != null) {
            BaseAction action = currentAction;
            Task actionTask = action.getTask();
            if (actionTask != null
                    && !PermissionManager.getInstance().canExecute(
                        steve.getUUID(), steve.getSteveName(), actionTask.getAction())) {
                String description = action.getDescription();
                cancelCurrentAction();
                ActionResult denied = ActionResult.failure(ActionResult.ERROR_PERMISSION_DENIED,
                    "Permission revoked while action was running").build();
                if (autonomyManaged) {
                    publishCompletion(actionTask, denied, description);
                } else {
                    taskQueue.clear();
                    clearCurrentGoal();
                    failAndResetState("permission revoked");
                }
                return;
            }
            try {
                if (action.isComplete()) {
                    ActionResult result = action.getResult();
                    SteveMod.LOGGER.info("Steve '{}' - Action completed: {} (Success: {})",
                        steve.getSteveName(), result.getMessage(), result.isSuccess());
                    interceptorChain.executeAfterAction(action, result, actionContext);

                    if (autonomyManaged) {
                        publishCompletion(action.getTask(), result, action.getDescription());
                        currentAction = null;
                        return;
                    }

                    steve.getMemory().addAction(action.getDescription());
                    if (result.isSuccess()) {
                        currentAction = null;
                        commandPathAttempts = 0;
                        if (taskQueue.isEmpty()) {
                            clearCurrentGoal();
                            if (stateMachine.getCurrentState() == AgentState.EXECUTING) {
                                stateMachine.transitionTo(AgentState.COMPLETED, "plan finished");
                                stateMachine.transitionTo(AgentState.IDLE, "ready");
                            }
                        }
                    } else {
                        handleCommandPathFailure(action.getTask(), result);
                    }
                } else {
                    if (ticksSinceLastAction % 100 == 0) {
                        SteveMod.LOGGER.info("Steve '{}' - Ticking action: {}",
                            steve.getSteveName(), action.getDescription());
                    }
                    action.tick();
                    return;
                }
            } catch (Throwable error) {
                handleActionException("running " + action.getClass().getSimpleName(), error);
                return;
            }
        }

        if (ticksSinceLastAction >= SteveConfig.ACTION_TICK_DELAY.get()) {
            if (!taskQueue.isEmpty()) {
                Task nextTask = taskQueue.poll();
                try {
                    executeTask(nextTask);
                } catch (Throwable error) {
                    handleActionStartException(nextTask, error);
                }
                ticksSinceLastAction = 0;
                return;
            }
        }
        
        // When completely idle (no tasks, no goal), follow nearest player
        if (!autonomyManaged
                && stateMachine.getCurrentState() != AgentState.PAUSED
                && taskQueue.isEmpty() && currentAction == null && currentGoal == null) {
            try {
                if (idleFollowAction == null || idleFollowAction.isComplete()) {
                    idleFollowAction = new IdleFollowAction(steve);
                    idleFollowAction.start();
                } else {
                    idleFollowAction.tick();
                }
            } catch (Throwable error) {
                SteveMod.LOGGER.error("Steve '{}' idle follow action failed", steve.getSteveName(), error);
                if (idleFollowAction != null) {
                    idleFollowAction.cancel();
                }
                idleFollowAction = null;
            }
        } else if (idleFollowAction != null) {
            idleFollowAction.cancel();
            idleFollowAction = null;
        }
    }

    private void executeTask(Task task) {
        if (!TaskValidator.isValid(task)) {
            SteveMod.LOGGER.warn("Steve '{}' rejected invalid task: {}", steve.getSteveName(), task);
            if (autonomyManaged) {
                publishCompletion(task, ActionResult.failure(ActionResult.ERROR_VALIDATION,
                    "Invalid autonomous task").build(), String.valueOf(task));
            } else {
                handleCommandPathFailure(task, ActionResult.failure(ActionResult.ERROR_VALIDATION,
                    "Invalid task").build());
            }
            sendToGUI(steve.getSteveName(), "I rejected an invalid action from the AI plan.");
            return;
        }

        String actionType = task.getAction();

        // Check permissions before executing
        PermissionManager permManager = PermissionManager.getInstance();
        if (!permManager.canExecute(steve.getUUID(), steve.getSteveName(), actionType)) {
            SteveMod.LOGGER.warn("Steve '{}' lacks permission for action '{}', skipping task",
                steve.getSteveName(), actionType);
            if (autonomyManaged) {
                publishCompletion(task, ActionResult.failure(ActionResult.ERROR_PERMISSION_DENIED,
                    "Permission denied for autonomous action").build(), task.toString());
            } else {
                handleCommandPathFailure(task, ActionResult.failure(ActionResult.ERROR_PERMISSION_DENIED,
                    "Permission denied").build());
            }
            sendToGUI(steve.getSteveName(), "I don't have permission to " + actionType + ".");
            return;
        }

        SteveMod.LOGGER.info("Steve '{}' executing task: {} (action type: {})", 
            steve.getSteveName(), task, task.getAction());
        
        currentAction = createAction(task);
        
        if (currentAction == null) {
            SteveMod.LOGGER.error("FAILED to create action for task: {}", task);
            if (autonomyManaged) {
                publishCompletion(task, ActionResult.failure(ActionResult.ERROR_VALIDATION,
                    "No registered action factory").requiresReplanning(true).build(), task.toString());
            } else {
                handleCommandPathFailure(task, ActionResult.failure(ActionResult.ERROR_VALIDATION,
                    "No registered action factory").requiresReplanning(true).build());
            }
            return;
        }

        SteveMod.LOGGER.info("Created action: {} - starting now...", currentAction.getClass().getSimpleName());
        if (!interceptorChain.executeBeforeAction(currentAction, actionContext)) {
            Task rejectedTask = currentAction.getTask();
            String rejectedDescription = currentAction.getDescription();
            cancelCurrentAction();
            if (autonomyManaged) {
                publishCompletion(rejectedTask, ActionResult.failure(ActionResult.ERROR_PERMISSION_DENIED,
                    "Safety interceptor rejected the action").build(), rejectedDescription);
            } else {
                handleCommandPathFailure(rejectedTask, ActionResult.failure(ActionResult.ERROR_PERMISSION_DENIED,
                    "Safety interceptor rejected the action").build());
            }
            sendToGUI(steve.getSteveName(), "Action was rejected by a safety interceptor.");
            return;
        }
        currentAction.start();
        startedAction = new ActionStart(currentAction.getTask(), currentAction.getDescription());
        SteveMod.LOGGER.info("Action started! Is complete: {}", currentAction.isComplete());
    }

    /** Creates an action exclusively from the registry's factory and descriptor entry. */
    private BaseAction createAction(Task task) {
        String actionType = task.getAction();
        ActionRegistry registry = ActionRegistry.getInstance();
        if (!registry.hasAction(actionType)) {
            SteveMod.LOGGER.warn("No registered action factory for '{}'", actionType);
            return null;
        }
        BaseAction action = registry.createAction(actionType, steve, task, actionContext);
        if (action != null) {
            SteveMod.LOGGER.debug("Created action '{}' via registry (plugin: {})",
                actionType, registry.getPluginForAction(actionType));
        }
        return action;
    }

    public void stopCurrentAction() {
        if (planningFuture != null) {
            planningFuture.cancel(true);
            planningFuture = null;
        }
        isPlanning = false;
        pendingCommand = null;

        cancelCurrentAction();
        if (idleFollowAction != null) {
            idleFollowAction.cancel();
            idleFollowAction = null;
        }
        taskQueue.clear();
        clearCurrentGoal();

        // Reset state machine
        stateMachine.reset();
    }

    public boolean isExecuting() {
        return isPlanning || currentAction != null || !taskQueue.isEmpty();
    }

    public String getCurrentGoal() {
        return currentGoal;
    }

    /**
     * Returns the event bus for subscribing to action events.
     *
     * @return EventBus instance
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Returns the agent state machine.
     *
     * @return AgentStateMachine instance
     */
    public AgentStateMachine getStateMachine() {
        return stateMachine;
    }

    /**
     * Returns the interceptor chain for adding custom interceptors.
     *
     * @return InterceptorChain instance
     */
    public InterceptorChain getInterceptorChain() {
        return interceptorChain;
    }

    /**
     * Returns the action context.
     *
     * @return ActionContext instance
     */
    public ActionContext getActionContext() {
        return actionContext;
    }

    /**
     * Checks if the agent is currently planning (async LLM call in progress).
     *
     * @return true if planning
     */
    public boolean isPlanning() {
        return isPlanning;
    }

    public void shutdown() {
        if (steve.getMemory().getActiveGoal() != null
                && !steve.getMemory().getActiveGoal().isTerminal()) {
            stopAutonomousExecution();
        } else {
            stopCurrentAction();
        }
        eventBus.shutdown();
    }

    /**
     * Command-path recovery: retry a bounded number of times, skip one non-retryable
     * step, and abort only for permission/cancel/invalid-LLM failures.
     */
    public static CommandPathDecision decideCommandPathFailure(ActionResult result, int attempts) {
        return decideCommandPathFailure(result, attempts, MAX_COMMAND_PATH_RETRIES);
    }

    public static CommandPathDecision decideCommandPathFailure(ActionResult result, int attempts,
            int maxRetries) {
        if (result == null) {
            return CommandPathDecision.ABORT;
        }
        String code = result.getErrorCode();
        if (ActionResult.ERROR_PERMISSION_DENIED.equals(code)
                || ActionResult.ERROR_CANCELLED.equals(code)
                || ActionResult.ERROR_LLM_INVALID.equals(code)) {
            return CommandPathDecision.ABORT;
        }
        int limit = Math.max(0, maxRetries);
        if (result.isRetryable()
                && attempts < limit
                && !ActionResult.ERROR_PROTECTED.equals(code)
                && !ActionResult.ERROR_VALIDATION.equals(code)) {
            return CommandPathDecision.RETRY;
        }
        return CommandPathDecision.SKIP;
    }

    private void handleCommandPathFailure(Task task, ActionResult result) {
        currentAction = null;
        CommandPathDecision decision = decideCommandPathFailure(result, commandPathAttempts);
        switch (decision) {
            case RETRY -> {
                commandPathAttempts++;
                if (task != null) {
                    ((LinkedList<Task>) taskQueue).addFirst(task);
                }
                sendToGUI(steve.getSteveName(), "Retrying: " + result.getMessage());
            }
            case SKIP -> {
                commandPathAttempts = 0;
                sendToGUI(steve.getSteveName(), "Skipping: "
                    + (result == null ? "failed action" : result.getMessage()));
            }
            case ABORT -> {
                commandPathAttempts = 0;
                taskQueue.clear();
                failAndResetState("action failed: "
                    + (result == null ? ActionResult.ERROR_UNKNOWN : result.getErrorCode()));
                clearCurrentGoal();
            }
        }
    }

    private void applyPlan(ResponseParser.ParsedResponse response) {
        taskQueue.clear();
        commandPathAttempts = 0;

        int rejectedTasks = 0;
        for (Task task : response.getTasks()) {
            if (TaskValidator.isValid(task)) {
                taskQueue.add(task);
            } else {
                rejectedTasks++;
                SteveMod.LOGGER.warn("Steve '{}' rejected invalid planned task: {}",
                    steve.getSteveName(), task);
            }
        }

        if (rejectedTasks > 0) {
            sendToGUI(steve.getSteveName(),
                "I ignored " + rejectedTasks + " invalid action(s) from the AI plan.");
        }

        if (taskQueue.isEmpty()) {
            failAndResetState("plan contained no valid tasks");
            clearCurrentGoal();
            return;
        }

        currentGoal = response.getSummary();
        steve.getMemory().setCurrentGoal(currentGoal != null ? currentGoal : "");
        if (stateMachine.getCurrentState() == AgentState.PLANNING) {
            stateMachine.transitionTo(AgentState.EXECUTING, "plan accepted");
        }
    }

    private void clearCurrentGoal() {
        currentGoal = null;
        steve.getMemory().setCurrentGoal("");
    }

    private void handleActionStartException(Task task, Throwable error) {
        if (currentAction != null) {
            handleActionException("starting task " + task.getAction(), error);
            return;
        }
        SteveMod.LOGGER.error("Steve '{}' failed while starting task {}",
            steve.getSteveName(), task, error);
        ActionResult failure = ActionResult.failure(ActionResult.ERROR_UNKNOWN,
            "Action factory/start exception: " + error.getClass().getSimpleName())
            .retryable(true).requiresReplanning(true).build();
        if (autonomyManaged) {
            publishCompletion(task, failure, task == null ? "unknown task" : task.toString());
        } else {
            handleCommandPathFailure(task, failure);
        }
    }

    private void handleActionException(String phase, Throwable error) {
        SteveMod.LOGGER.error("Steve '{}' failed while {}", steve.getSteveName(), phase, error);
        if (autonomyManaged && currentAction != null) {
            Task failedTask = currentAction.getTask();
            String description = currentAction.getDescription();
            ActionResult failure = ActionResult.failure(ActionResult.ERROR_UNKNOWN,
                "Action exception: " + error.getClass().getSimpleName())
                .retryable(true).requiresReplanning(true).build();
            interceptorChain.executeOnError(currentAction,
                error instanceof Exception exception ? exception : new RuntimeException(error), actionContext);
            try {
                currentAction.cancel();
            } catch (Throwable cancelError) {
                error.addSuppressed(cancelError);
            }
            currentAction = null;
            publishCompletion(failedTask, failure, description);
            return;
        }
        if (currentAction != null) {
            Exception interceptorError = error instanceof Exception exception
                ? exception
                : new RuntimeException(error);
            interceptorChain.executeOnError(currentAction, interceptorError, actionContext);
            try {
                currentAction.cancel();
            } catch (Throwable cancelError) {
                error.addSuppressed(cancelError);
            }
            currentAction = null;
        }
        sendToGUI(steve.getSteveName(), "Action failed safely: " + error.getClass().getSimpleName());
        taskQueue.clear();
        failAndResetState("action failed");
        clearCurrentGoal();
    }

    private void cancelCurrentAction() {
        BaseAction action = currentAction;
        if (action == null) {
            return;
        }

        try {
            action.cancel();
            interceptorChain.executeAfterAction(action, action.getResult(), actionContext);
        } catch (Throwable error) {
            Exception interceptorError = error instanceof Exception exception
                ? exception
                : new RuntimeException(error);
            interceptorChain.executeOnError(action, interceptorError, actionContext);
            SteveMod.LOGGER.error("Steve '{}' failed to cancel action {}",
                steve.getSteveName(), action.getClass().getSimpleName(), error);
        } finally {
            currentAction = null;
        }
    }

    private void failAndResetState(String reason) {
        AgentState state = stateMachine.getCurrentState();
        if (state == AgentState.PLANNING || state == AgentState.EXECUTING) {
            stateMachine.transitionTo(AgentState.FAILED, reason);
        }
        if (stateMachine.getCurrentState() == AgentState.FAILED) {
            stateMachine.transitionTo(AgentState.IDLE, "recovered");
        }
    }
}
