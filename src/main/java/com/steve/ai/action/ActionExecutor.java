package com.steve.ai.action;

import com.steve.ai.SteveMod;
import com.steve.ai.action.actions.*;
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

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

/**
 * Executes actions for a Steve entity using the plugin-based action system.
 *
 * <p><b>Architecture:</b></p>
 * <ul>
 *   <li>Uses ActionRegistry for dynamic action creation (Factory + Registry patterns)</li>
 *   <li>Uses InterceptorChain for cross-cutting concerns (logging, metrics, events)</li>
 *   <li>Uses AgentStateMachine for explicit state management</li>
 *   <li>Falls back to legacy switch statement if registry lookup fails</li>
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

    // NEW: Async planning support (non-blocking LLM calls)
    private CompletableFuture<ResponseParser.ParsedResponse> planningFuture;
    private boolean isPlanning = false;
    private String pendingCommand;  // Store command while planning

    // NEW: Plugin architecture components
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
        this.planningFuture = null;
        this.pendingCommand = null;

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
        SteveMod.LOGGER.info("Steve '{}' processing command (async): {}", steve.getSteveName(), command);

        if (command == null || command.isBlank()) {
            sendToGUI(steve.getSteveName(), "Please provide a command.");
            return;
        }

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

    /**
     * Legacy synchronous command processing (blocking).
     *
     * <p><b>Warning:</b> This method blocks the game thread for 30-60 seconds during LLM calls.
     * Use {@link #processNaturalLanguageCommand(String)} instead for non-blocking execution.</p>
     *
     * @param command The natural language command
     * @deprecated Use {@link #processNaturalLanguageCommand(String)} instead
     */
    @Deprecated
    public void processNaturalLanguageCommandSync(String command) {
        SteveMod.LOGGER.info("Steve '{}' processing command (SYNC - blocking!): {}", steve.getSteveName(), command);

        cancelCurrentAction();

        if (idleFollowAction != null) {
            idleFollowAction.cancel();
            idleFollowAction = null;
        }

        try {
            stateMachine.reset();
            stateMachine.transitionTo(AgentState.PLANNING, "synchronous command");
            // BLOCKING CALL - freezes game for 30-60 seconds!
            ResponseParser.ParsedResponse response = getTaskPlanner().planTasks(steve, command);

            if (response == null) {
                failAndResetState("planner returned no response");
                sendToGUI(steve.getSteveName(), "I couldn't understand that command.");
                return;
            }

            applyPlan(response);

            if (SteveConfig.ENABLE_CHAT_RESPONSES.get() && !taskQueue.isEmpty()) {
                sendToGUI(steve.getSteveName(), "Okay! " + currentGoal);
            }
        } catch (NoClassDefFoundError e) {
            failAndResetState("AI components unavailable");
            SteveMod.LOGGER.error("Failed to initialize AI components", e);
            sendToGUI(steve.getSteveName(), "Sorry, I'm having trouble with my AI systems!");
        }

        SteveMod.LOGGER.info("Steve '{}' queued {} tasks", steve.getSteveName(), taskQueue.size());
    }
    
    /** Envia feedback pelo chat do servidor quando habilitado. */
    private void sendToGUI(String steveName, String message) {
        if (!steve.level().isClientSide && SteveConfig.ENABLE_CHAT_RESPONSES.get()) {
            steve.sendChatMessage(message);
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
            try {
                if (action.isComplete()) {
                    ActionResult result = action.getResult();
                    SteveMod.LOGGER.info("Steve '{}' - Action completed: {} (Success: {})",
                        steve.getSteveName(), result.getMessage(), result.isSuccess());

                    steve.getMemory().addAction(action.getDescription());
                    interceptorChain.executeAfterAction(action, result, actionContext);

                    if (!result.isSuccess() && result.requiresReplanning()) {
                        // Stop the dependent remainder of this plan. A new command may replan safely.
                        taskQueue.clear();
                        failAndResetState("action requested replanning");
                        if (SteveConfig.ENABLE_CHAT_RESPONSES.get()) {
                            sendToGUI(steve.getSteveName(), "Problem: " + result.getMessage());
                        }
                    }

                    currentAction = null;
                    if (taskQueue.isEmpty()) {
                        clearCurrentGoal();
                        if (stateMachine.getCurrentState() == AgentState.EXECUTING) {
                            stateMachine.transitionTo(AgentState.COMPLETED, "plan finished");
                            stateMachine.transitionTo(AgentState.IDLE, "ready");
                        }
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
                    handleActionException("starting task " + nextTask.getAction(), error);
                }
                ticksSinceLastAction = 0;
                return;
            }
        }
        
        // When completely idle (no tasks, no goal), follow nearest player
        if (taskQueue.isEmpty() && currentAction == null && currentGoal == null) {
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
            sendToGUI(steve.getSteveName(), "I rejected an invalid action from the AI plan.");
            return;
        }

        String actionType = task.getAction();

        // Check permissions before executing
        PermissionManager permManager = PermissionManager.getInstance();
        if (!permManager.canExecute(steve.getSteveName(), actionType)) {
            SteveMod.LOGGER.warn("Steve '{}' lacks permission for action '{}', skipping task",
                steve.getSteveName(), actionType);
            sendToGUI(steve.getSteveName(), "I don't have permission to " + actionType + ".");
            return;
        }

        SteveMod.LOGGER.info("Steve '{}' executing task: {} (action type: {})", 
            steve.getSteveName(), task, task.getAction());
        
        currentAction = createAction(task);
        
        if (currentAction == null) {
            SteveMod.LOGGER.error("FAILED to create action for task: {}", task);
            return;
        }

        SteveMod.LOGGER.info("Created action: {} - starting now...", currentAction.getClass().getSimpleName());
        if (!interceptorChain.executeBeforeAction(currentAction, actionContext)) {
            cancelCurrentAction();
            sendToGUI(steve.getSteveName(), "Action was rejected by a safety interceptor.");
            return;
        }
        currentAction.start();
        SteveMod.LOGGER.info("Action started! Is complete: {}", currentAction.isComplete());
    }

    /**
     * Creates an action using the plugin registry with legacy fallback.
     *
     * <p>First attempts to create the action via ActionRegistry (plugin system).
     * If the registry doesn't have the action or creation fails, falls back
     * to the legacy switch statement for backward compatibility.</p>
     *
     * @param task Task containing action type and parameters
     * @return Created action, or null if unknown action type
     */
    private BaseAction createAction(Task task) {
        String actionType = task.getAction();

        // Try registry-based creation first (plugin architecture)
        ActionRegistry registry = ActionRegistry.getInstance();
        if (registry.hasAction(actionType)) {
            BaseAction action = registry.createAction(actionType, steve, task, actionContext);
            if (action != null) {
                SteveMod.LOGGER.debug("Created action '{}' via registry (plugin: {})",
                    actionType, registry.getPluginForAction(actionType));
                return action;
            }
        }

        // Fallback to legacy switch statement for backward compatibility
        SteveMod.LOGGER.debug("Using legacy fallback for action: {}", actionType);
        return createActionLegacy(task);
    }

    /**
     * Legacy action creation using switch statement.
     *
     * <p>Kept for backward compatibility during migration to plugin system.
     * Will be removed in a future version once all actions are registered
     * via plugins.</p>
     *
     * @param task Task containing action type and parameters
     * @return Created action, or null if unknown
     * @deprecated Use ActionRegistry instead
     */
    @Deprecated
    private BaseAction createActionLegacy(Task task) {
        return switch (task.getAction()) {
            case "pathfind" -> new PathfindAction(steve, task);
            case "mine" -> new MineBlockAction(steve, task);
            case "place" -> new PlaceBlockAction(steve, task);

            case "attack" -> new CombatAction(steve, task);
            case "follow" -> new FollowPlayerAction(steve, task);
            case "gather" -> new GatherResourceAction(steve, task);
            case "build" -> new BuildStructureAction(steve, task);
            default -> {
                SteveMod.LOGGER.warn("Unknown action type: {}", task.getAction());
                yield null;
            }
        };
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
        stopCurrentAction();
        eventBus.shutdown();
    }

    private void applyPlan(ResponseParser.ParsedResponse response) {
        taskQueue.clear();

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

        currentGoal = response.getPlan();
        steve.getMemory().setCurrentGoal(currentGoal != null ? currentGoal : "");
        if (stateMachine.getCurrentState() == AgentState.PLANNING) {
            stateMachine.transitionTo(AgentState.EXECUTING, "plan accepted");
        }
    }

    private void clearCurrentGoal() {
        currentGoal = null;
        steve.getMemory().setCurrentGoal("");
    }

    private void handleActionException(String phase, Throwable error) {
        SteveMod.LOGGER.error("Steve '{}' failed while {}", steve.getSteveName(), phase, error);
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

