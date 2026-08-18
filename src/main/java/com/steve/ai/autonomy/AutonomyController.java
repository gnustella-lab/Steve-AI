package com.steve.ai.autonomy;

import com.steve.ai.SteveMod;
import com.steve.ai.action.ActionExecutor;
import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.execution.AgentState;
import com.steve.ai.execution.AgentStateMachine;
import com.steve.ai.llm.AutonomyPlanner;
import com.steve.ai.llm.PlanningContext;
import com.steve.ai.llm.ResponseParser;
import com.steve.ai.llm.TaskPlanner;
import com.steve.ai.memory.EpisodicMemoryEntry;
import com.steve.ai.memory.SteveMemory;
import com.steve.ai.memory.WorldFact;
import com.steve.ai.perception.ObservationService;
import com.steve.ai.perception.ObservationSnapshot;
import com.steve.ai.planning.Plan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Server-thread executive for persistent goals. It schedules observation and async planning,
 * while ActionExecutor remains responsible only for tick-based action runtime.
 */
public final class AutonomyController {
    private final SteveEntity steve;
    private final ActionExecutor executor;
    private final AgentStateMachine stateMachine;
    private final GoalQueue goalQueue = new GoalQueue();
    private final GoalEvaluator goalEvaluator = new GoalEvaluator();
    private final RecoveryEngine recoveryEngine = new RecoveryEngine();
    private final FailureTracker failureTracker;
    private final ObservationService observationService;
    private final AutonomyPlanner injectedPlanner;

    private AutonomyPlanner planner;
    private AgentGoal activeGoal;
    private Plan currentPlan;
    private ObservationSnapshot lastObservation;
    private ActionResult lastActionResult;
    private RecoveryDecision pendingRecovery;
    private CompletableFuture<ResponseParser.ParsedResponse> planningFuture;
    private long planningGeneration;
    private long planningRequestGeneration;
    private UUID planningGoalId;
    private long localTick;
    private long nextThinkTick;
    private long idleTicks;
    private int invalidResponseAttempts;
    private String lastFailure = "";
    private String lastSummary = "";
    private String lastFeedback = "";
    private long lastFeedbackTick = Long.MIN_VALUE;
    private boolean restored;
    private boolean stoppedLatch;
    private boolean shutdown;

    public AutonomyController(SteveEntity steve, ActionExecutor executor) {
        this(steve, executor, null);
    }

    /** Injection constructor used by deterministic tests and offline simulations. */
    public AutonomyController(SteveEntity steve, ActionExecutor executor, AutonomyPlanner planner) {
        this.steve = steve;
        this.executor = executor;
        this.stateMachine = executor.getStateMachine();
        this.injectedPlanner = planner;
        this.failureTracker = new FailureTracker(SteveConfig.AUTONOMY_MAX_REPEATED_FAILURE_FINGERPRINT.get());
        this.observationService = new ObservationService(SteveConfig.AUTONOMY_PERCEPTION_INTERVAL_TICKS.get());
        this.nextThinkTick = 0L;
        this.planningGeneration = 0L;
        this.planningRequestGeneration = 0L;
        this.planningGoalId = null;
    }

    public void tick() {
        if (shutdown) return;
        localTick++;
        long now = currentTick();
        AutonomyMode mode = getMode();
        if (!SteveConfig.AUTONOMY_ENABLED.get() || mode == AutonomyMode.OFF) return;

        restorePersistedGoal(now);

        if (stoppedLatch || stateMachine.getCurrentState() == AgentState.PAUSED) {
            return;
        }

        if (planningFuture != null) {
            if (planningFuture.isDone()) {
                CompletableFuture<ResponseParser.ParsedResponse> completedFuture = planningFuture;
                planningFuture = null;
                boolean currentRequest = planningRequestGeneration == planningGeneration
                    && activeGoal != null && activeGoal.getId().equals(planningGoalId)
                    && !stoppedLatch && stateMachine.getCurrentState() != AgentState.PAUSED;
                planningGoalId = null;
                if (currentRequest) {
                    try {
                        handlePlanningResult(completedFuture.getNow(null), now);
                    } catch (java.util.concurrent.CancellationException ignored) {
                        // A cancelled request is intentionally discarded.
                    } catch (RuntimeException exception) {
                        handlePlanningResult(null, now);
                    }
                }
            } else {
                return;
            }
        }

        ActionExecutor.ActionCompletion completion = executor.consumeCompletedAction();
        if (completion != null) {
            handleActionCompletion(completion, now);
        }

        if (stoppedLatch) return;

        if (activeGoal == null) {
            activeGoal = goalQueue.pollNext();
            if (activeGoal == null) {
                maybeCreateProactiveGoal(now, mode);
                return;
            }
            SteveMemory memory = steve.getMemory();
            memory.setActiveGoal(activeGoal);
            applyControllerIdentity(activeGoal);
            idleTicks = 0;
            moveTo(AgentState.OBSERVING, "goal activated");
            sendFeedback("Starting: " + activeGoal.getDescription(), now);
        }

        if (activeGoal.isTerminal()) {
            activeGoal = null;
            moveTo(AgentState.IDLE, "terminal goal drained");
            return;
        }

        if (executor.hasPendingAutonomousTasks()) {
            moveTo(AgentState.EXECUTING, "action runtime owns the current step");
            return;
        }

        if (pendingRecovery != null) {
            applyRecovery(now);
            return;
        }

        if (stateMachine.getCurrentState() == AgentState.EVALUATING) {
            evaluateGoal(now);
            return;
        }

        if (stateMachine.getCurrentState() == AgentState.PAUSED) return;
        if (stateMachine.getCurrentState() == AgentState.BLOCKED) return;

        if (now < nextThinkTick) return;

        moveTo(AgentState.OBSERVING, "refresh before next horizon");
        lastObservation = observationService.capture(steve, activeGoal, null,
            executor.getCurrentActionDescription(), lastActionResult, true);
        startPlanning(now);
    }

    public AgentGoal submitUserGoal(String description, UUID controllerUuid) {
        if (description == null || description.isBlank()) {
            sendFeedback("Please provide a goal.", currentTick());
            return null;
        }
        if (!SteveConfig.AUTONOMY_ENABLED.get() || getMode() == AutonomyMode.OFF) {
            executor.processNaturalLanguageCommand(description, controllerUuid);
            return null;
        }

        long now = currentTick();
        stoppedLatch = false;
        cancelPlanning();
        executor.setControllingPlayerUuid(controllerUuid);
        invalidResponseAttempts = 0;
        UUID interruptedGoal = null;
        if (activeGoal != null && !activeGoal.isTerminal()) {
            interruptedGoal = activeGoal.getId();
            steve.getMemory().rememberGoal(activeGoal);
            goalQueue.pauseActive(now);
            activeGoal.pause(now);
            executor.stopAutonomousExecution();
        }

        AgentGoal goal = AgentGoal.create(description, GoalOrigin.USER,
            interruptedGoal == null ? GoalPriority.USER : GoalPriority.USER_INTERRUPT,
            null, now);
        goal.setConstraints(GoalConstraints.fromDescription(description));
        if (controllerUuid != null) goal.putMetadata("controllerUuid", controllerUuid.toString());
        if (interruptedGoal != null) goal.putMetadata("interruptedGoalId", interruptedGoal.toString());
        goalQueue.enqueue(goal);
        activeGoal = null;
        currentPlan = null;
        lastActionResult = null;
        lastFailure = "";
        failureTracker.clear();
        steve.getMemory().setActiveGoal(goal);
        nextThinkTick = now;
        moveTo(AgentState.OBSERVING, "new user goal");
        sendFeedback("Starting: " + description, now);
        return goal;
    }

    /** Absolute stop. A stopped goal is cancelled and never automatically resumed. */
    public void stop() {
        long now = currentTick();
        stoppedLatch = true;
        cancelPlanning();
        executor.stopAutonomousExecution();
        goalQueue.cancelAll(now);
        steve.getMemory().clearPersistedGoals();
        if (activeGoal != null) {
            activeGoal.cancel(now);
            steve.getMemory().recordGoalOutcome(activeGoal, "cancelled by stop", now);
        }
        activeGoal = null;
        currentPlan = null;
        steve.getMemory().clearActiveGoal();
        moveTo(AgentState.IDLE, "absolute stop");
        sendFeedback("Stopped. The cancelled goal will not resume automatically.", now);
    }

    public void pause() {
        long now = currentTick();
        if (activeGoal == null || activeGoal.isTerminal()) return;
        cancelPlanning();
        activeGoal.pause(now);
        goalQueue.pauseActive(now);
        executor.stopAutonomousExecution();
        steve.getMemory().setActiveGoal(activeGoal);
        moveTo(AgentState.PAUSED, "user pause");
        sendFeedback("Paused: " + activeGoal.getDescription(), now);
    }

    public void resume() {
        long now = currentTick();
        stoppedLatch = false;
        AgentGoal goal = activeGoal != null ? activeGoal : steve.getMemory().getActiveGoal();
        if (goal == null || goal.isTerminal()) return;
        if (goalQueue.getActive() == null) goalQueue.enqueue(goal);
        activeGoal = null;
        nextThinkTick = now;
        moveTo(AgentState.OBSERVING, "user resume");
    }

    public void cancelGoal() {
        stop();
    }

    /** Replaces the planner before the first autonomous request, primarily for deterministic tests. */
    public void setPlanner(AutonomyPlanner planner) {
        if (planningFuture != null && !planningFuture.isDone()) {
            throw new IllegalStateException("Cannot replace an in-flight planner");
        }
        this.planner = planner;
    }

    public AutonomyMode getMode() {
        if (!SteveConfig.AUTONOMY_ENABLED.get()) return AutonomyMode.OFF;
        return AutonomyMode.parse(SteveConfig.AUTONOMY_MODE.get());
    }

    public AgentState getState() { return stateMachine.getCurrentState(); }
    public AgentGoal getActiveGoal() { return activeGoal != null ? activeGoal : goalQueue.getActive(); }
    public GoalQueue getGoalQueue() { return goalQueue; }
    public Plan getCurrentPlan() { return currentPlan; }
    public ObservationSnapshot getLastObservation() { return lastObservation; }
    public ActionResult getLastActionResult() { return lastActionResult; }
    public String getLastFailure() { return lastFailure; }
    public boolean isPlanning() { return planningFuture != null && !planningFuture.isDone(); }
    public boolean isStopped() { return stoppedLatch; }
    public int getLlmCallsUsed() {
        AgentGoal goal = getActiveGoal();
        return goal == null ? 0 : goal.getBudget().getLlmCalls();
    }
    public int getReplansUsed() {
        AgentGoal goal = getActiveGoal();
        return goal == null ? 0 : goal.getReplanCount();
    }
    public String getStatusSummary() {
        AgentGoal goal = getActiveGoal();
        return "mode=" + getMode() + ", state=" + getState()
            + ", goal=" + (goal == null ? "none" : goal.getDescription())
            + ", plan=" + (currentPlan == null ? "none" : currentPlan.getProgress())
            + ", queued=" + goalQueue.size()
            + ", replans=" + getReplansUsed()
            + ", llmCalls=" + getLlmCallsUsed()
            + ", lastFailure=" + lastFailure;
    }

    public void prepareForSave() {
        SteveMemory memory = steve.getMemory();
        AgentGoal goal = activeGoal != null ? activeGoal : goalQueue.getActive();
        if (goal != null && !goal.isTerminal()) {
            memory.setActiveGoal(goal);
        }
        for (AgentGoal pending : goalQueue.getPendingGoals()) {
            memory.rememberGoal(pending);
        }
        for (AgentGoal paused : goalQueue.getPausedGoals()) {
            memory.rememberGoal(paused);
        }
    }

    public void shutdown() {
        prepareForSave();
        shutdown = true;
        cancelPlanning();
        if (activeGoal != null && !activeGoal.isTerminal()) {
            activeGoal.pause(currentTick());
            steve.getMemory().setActiveGoal(activeGoal);
        }
        executor.stopAutonomousExecution();
        observationService.clear();
    }

    private void cancelPlanning() {
        planningGeneration++;
        if (planningFuture != null) planningFuture.cancel(true);
        planningFuture = null;
        planningGoalId = null;
        planningRequestGeneration = planningGeneration;
    }

    private void restorePersistedGoal(long now) {
        if (restored) return;
        restored = true;
        AgentGoal persisted = steve.getMemory().getActiveGoal();
        for (AgentGoal pending : steve.getMemory().getPersistedGoals()) {
            goalQueue.enqueue(pending);
        }
        if (persisted != null && !persisted.isTerminal()) {
            persisted.pause(now);
            goalQueue.activate(persisted, now);
            activeGoal = persisted;
            applyControllerIdentity(activeGoal);
            sendFeedback("Resuming safely after restart: " + persisted.getDescription(), now);
        }
    }

    private void startPlanning(long now) {
        if (planningFuture != null || activeGoal == null) return;
        if (activeGoal.getReplanCount() >= SteveConfig.AUTONOMY_MAX_REPLANS_PER_GOAL.get()
                || activeGoal.getBudget().getLlmCalls() >= SteveConfig.AUTONOMY_MAX_LLM_CALLS_PER_GOAL.get()) {
            blockGoal("Autonomy budget exhausted", now);
            return;
        }

        AutonomyPlanner selectedPlanner = planner;
        if (selectedPlanner == null) {
            selectedPlanner = injectedPlanner;
            if (selectedPlanner == null) {
                try {
                    selectedPlanner = new TaskPlanner();
                } catch (RuntimeException exception) {
                    blockGoal("LLM planner unavailable", now);
                    return;
                }
            }
            planner = selectedPlanner;
        }

        SteveMemory memory = steve.getMemory();
        if (createDeterministicPrerequisitePlan(now)) {
            return;
        }
        PlanningContext context = new PlanningContext(
            activeGoal,
            null,
            lastObservation,
            memory.getRelevantFacts(activeGoal.getDescription(), 8).stream()
                .map(fact -> fact.kind().name().toLowerCase() + ":" + fact.key()).toList(),
            memory.getRecentActions(8),
            lastActionResult == null ? "" : formatResult(lastActionResult),
            failureTracker.failedApproaches(),
            SteveConfig.AUTONOMY_MAX_PLAN_HORIZON.get(),
            SteveConfig.AUTONOMY_MAX_LLM_CALLS_PER_GOAL.get() - activeGoal.getBudget().getLlmCalls(),
            SteveConfig.AUTONOMY_MAX_REPLANS_PER_GOAL.get() - activeGoal.getReplanCount());

        activeGoal.getBudget().recordLlmCall();
        planningRequestGeneration = planningGeneration;
        planningGoalId = activeGoal.getId();
        moveTo(AgentState.PLANNING, "request bounded horizon");
        try {
            planningFuture = selectedPlanner.plan(context);
        } catch (RuntimeException exception) {
            planningFuture = CompletableFuture.completedFuture(null);
        }
        nextThinkTick = now + SteveConfig.AUTONOMY_THINK_COOLDOWN_TICKS.get();
    }

    private boolean createDeterministicPrerequisitePlan(long now) {
        if (activeGoal == null || activeGoal.getOrigin() != GoalOrigin.PREREQUISITE) return false;
        String description = activeGoal.getDescription().trim();
        String lower = description.toLowerCase(java.util.Locale.ROOT);
        String action;
        if (lower.startsWith("craft ")) {
            action = "craft";
        } else if (lower.startsWith("gather ") || lower.startsWith("mine ")) {
            action = "gather";
        } else {
            return false;
        }

        GoalConstraints constraints = activeGoal.getConstraints();
        String target = constraints.targetItem();
        if (target.isBlank()) {
            String[] tokens = description.substring(action.length()).trim().split("\\s+");
            target = tokens.length == 0 ? "" : tokens[tokens.length - 1];
        }
        if (target.isBlank()) return false;
        int quantity = constraints.targetQuantity() > 0 ? constraints.targetQuantity() : 1;
        Task task = "craft".equals(action)
            ? new Task("craft", Map.of("item", target, "quantity", quantity))
            : new Task("gather", Map.of("resource", target, "quantity", quantity));
        currentPlan = new Plan(activeGoal.getId(), activeGoal.getDescription(),
            executor.getControllingPlayerUuid(), steve.getUUID(),
            SteveConfig.AUTONOMY_MAX_RETRIES_PER_STEP.get(),
            SteveConfig.AUTONOMY_MAX_REPLANS_PER_GOAL.get(),
            SteveConfig.AUTONOMY_MAX_LLM_CALLS_PER_GOAL.get(), 0, now);
        currentPlan.loadHorizon(List.of(task), "Deterministic prerequisite: " + description,
            "recipe/resource prerequisite", now);
        executor.acceptAutonomousPlan(currentPlan);
        moveTo(AgentState.EXECUTING, "deterministic prerequisite plan");
        sendFeedback("Preparing prerequisite: " + description, now);
        return true;
    }

    private void handlePlanningResult(ResponseParser.ParsedResponse response, long now) {
        if (response == null) {
            invalidResponseAttempts++;
            lastActionResult = ActionResult.failure(ActionResult.ERROR_LLM_INVALID,
                "Planner returned no valid bounded response").build();
            lastFailure = lastActionResult.getMessage();
            if (invalidResponseAttempts > 2) {
                blockGoal("LLM output remained invalid after bounded retries", now);
            } else {
                moveTo(AgentState.RECOVERING, "invalid LLM output");
                pendingRecovery = RecoveryDecision.replan("Invalid LLM output", Map.of());
            }
            return;
        }
        invalidResponseAttempts = 0;
        lastSummary = response.getSummary();

        if (response.getDecision() == ResponseParser.Decision.BLOCKED
                || response.getDecision() == ResponseParser.Decision.ASK_USER
                || "blocked".equals(response.getGoalStatus())) {
            blockGoal(response.getSummary().isBlank() ? "Planner reported no safe strategy" : response.getSummary(), now);
            return;
        }

        if (response.getDecision() == ResponseParser.Decision.COMPLETE) {
            GoalEvaluator.Evaluation evaluation = goalEvaluator.evaluate(activeGoal, steve,
                steve.blockPosition(), lastActionResult, true);
            if (evaluation.status() == GoalEvaluator.Status.COMPLETE) {
                completeGoal(evaluation.reason(), now);
                return;
            }
            if (response.getTasks().isEmpty()) {
                pendingRecovery = RecoveryDecision.replan("Planner claimed completion without deterministic proof", Map.of());
                moveTo(AgentState.RECOVERING, "unverified completion");
                return;
            }
        }

        if (response.getTasks().isEmpty()) {
            pendingRecovery = RecoveryDecision.replan("Planner returned an empty action horizon", Map.of());
            moveTo(AgentState.RECOVERING, "empty horizon");
            return;
        }
        if (!originAllowsTasks(activeGoal, response.getTasks())) {
            blockGoal("The goal origin is not authorized for the requested action", now);
            return;
        }

        currentPlan = new Plan(activeGoal.getId(), activeGoal.getDescription(),
            executor.getControllingPlayerUuid(), steve.getUUID(),
            SteveConfig.AUTONOMY_MAX_RETRIES_PER_STEP.get(),
            SteveConfig.AUTONOMY_MAX_REPLANS_PER_GOAL.get(),
            SteveConfig.AUTONOMY_MAX_LLM_CALLS_PER_GOAL.get(), 0, now);
        currentPlan.loadHorizon(response.getTasks(), response.getSummary(), "new observation", now);
        executor.acceptAutonomousPlan(currentPlan);
        moveTo(AgentState.EXECUTING, "horizon accepted");
        if (response.getSummary() != null && !response.getSummary().isBlank()) {
            sendFeedback(response.getSummary(), now);
        }
    }

    private boolean originAllowsTasks(AgentGoal goal, List<Task> tasks) {
        if (goal == null || goal.getOrigin() == GoalOrigin.USER
                || goal.getOrigin() == GoalOrigin.COLLABORATION) {
            return true;
        }
        for (Task task : tasks) {
            String action = task.getAction();
            boolean allowed = switch (goal.getOrigin()) {
                case PREREQUISITE, RECOVERY -> switch (action) {
                    case "craft", "smelt", "gather", "mine", "search_resource", "pathfind",
                        "equip_item", "withdraw_item", "pickup_item" -> true;
                    default -> false;
                };
                case MAINTENANCE, AUTONOMOUS -> switch (action) {
                    case "deposit_item", "inspect_inventory", "equip_item", "withdraw_item", "follow" -> true;
                    default -> false;
                };
                default -> true;
            };
            if (!allowed) return false;
        }
        return true;
    }

    private void handleActionCompletion(ActionExecutor.ActionCompletion completion, long now) {
        if (activeGoal == null) return;
        lastActionResult = completion.result();
        lastFailure = completion.result().isSuccess() ? "" : completion.result().getMessage();
        steve.getMemory().addEpisode(new EpisodicMemoryEntry(activeGoal.getId(),
            completion.task() == null ? "unknown" : completion.task().getAction(),
            completion.result().isSuccess() ? "success" : completion.result().getErrorCode(),
            completion.result().getMessage(), dimension(), steve.blockPosition(), now));
        steve.getMemory().addAction(completion.description());

        if (completion.result().isSuccess()) {
            activeGoal.getBudget().recordProgress();
            if (currentPlan != null) {
                currentPlan.recordCurrentStepResult(completion.result(), now);
                currentPlan.advanceToNextTask(now);
            }
            moveTo(AgentState.EVALUATING, "action completed; verify progress");
            GoalEvaluator.Evaluation evaluation = goalEvaluator.evaluate(activeGoal, steve,
                steve.blockPosition(), completion.result(), !executor.hasPendingAutonomousTasks());
            if (evaluation.status() == GoalEvaluator.Status.COMPLETE) {
                completeGoal(evaluation.reason(), now);
            } else if (!executor.hasPendingAutonomousTasks()) {
                moveTo(AgentState.EVALUATING, "horizon step succeeded");
            }
            return;
        }

        if (completion.result().getErrorCode() == null
                || !ActionResult.ERROR_PROTECTED.equals(completion.result().getErrorCode())) {
            activeGoal.getBudget().recordFailure();
        }
        if (ActionResult.ERROR_PROTECTED.equals(completion.result().getErrorCode())) {
            steve.getMemory().rememberWorldFact(new WorldFact(WorldFact.Kind.PROTECTED,
                completion.task() == null ? "unknown" : completion.task().getAction(),
                dimension(), steve.blockPosition(), now, 1.0, 0L, Map.of("message", completion.result().getMessage())));
        }
        executor.stopAutonomousExecution();
        currentPlan = null;
        moveTo(AgentState.RECOVERING, "action result requires evaluation");
        pendingRecovery = recoveryEngine.decide(activeGoal, completion.task(), completion.result(),
            failureTracker, steve.blockPosition());
        sendFailureFeedback(completion.result(), now);
        if (activeGoal.getBudget().getConsecutiveFailures()
                >= SteveConfig.AUTONOMY_MAX_CONSECUTIVE_FAILURES.get()) {
            pendingRecovery = RecoveryDecision.blocked("Consecutive failure budget exhausted");
        }
    }

    private void evaluateGoal(long now) {
        GoalEvaluator.Evaluation evaluation = goalEvaluator.evaluate(activeGoal, steve,
            steve.blockPosition(), lastActionResult, true);
        if (evaluation.status() == GoalEvaluator.Status.COMPLETE) {
            completeGoal(evaluation.reason(), now);
            return;
        }
        moveTo(AgentState.OBSERVING, "goal remains incomplete");
        observationService.clear();
        nextThinkTick = now;
    }

    private void applyRecovery(long now) {
        RecoveryDecision decision = pendingRecovery;
        pendingRecovery = null;
        if (activeGoal == null || decision == null) return;

        switch (decision.kind()) {
            case RETRY -> {
                if (decision.retryTask() == null) {
                    moveTo(AgentState.OBSERVING, "retry lacks a task");
                    return;
                }
                Plan retryPlan = new Plan(activeGoal.getId(), activeGoal.getDescription(),
                    executor.getControllingPlayerUuid(), steve.getUUID(),
                    SteveConfig.AUTONOMY_MAX_RETRIES_PER_STEP.get(),
                    SteveConfig.AUTONOMY_MAX_REPLANS_PER_GOAL.get(),
                    SteveConfig.AUTONOMY_MAX_LLM_CALLS_PER_GOAL.get(), 0, now);
                retryPlan.loadHorizon(List.of(decision.retryTask()), "Retrying with the same bounded task", "deterministic recovery", now);
                currentPlan = retryPlan;
                executor.acceptAutonomousPlan(retryPlan);
                moveTo(AgentState.EXECUTING, "deterministic retry");
            }
            case PREREQUISITE -> createPrerequisite(decision.prerequisiteDescription(), now);
            case REPLAN -> {
                if (activeGoal.getReplanCount() >= SteveConfig.AUTONOMY_MAX_REPLANS_PER_GOAL.get()) {
                    blockGoal("Replan budget exhausted", now);
                } else {
                    activeGoal.incrementReplan();
                    moveTo(AgentState.OBSERVING, "deterministic recovery requests a new observation");
                    observationService.clear();
                    nextThinkTick = now;
                }
            }
            case PAUSE -> {
                activeGoal.pause(now);
                steve.getMemory().setActiveGoal(activeGoal);
                moveTo(AgentState.PAUSED, decision.reason());
                sendFeedback("Paused: " + decision.reason(), now);
            }
            case BLOCKED -> blockGoal(decision.reason(), now);
        }
    }

    private void createPrerequisite(String description, long now) {
        description = refinePrerequisite(description);
        AgentGoal parent = activeGoal;
        steve.getMemory().rememberGoal(parent);
        goalQueue.pauseActive(now);
        parent.pause(now);
        executor.stopAutonomousExecution();
        AgentGoal prerequisite = AgentGoal.create(description, GoalOrigin.PREREQUISITE,
            GoalPriority.PREREQUISITE, parent.getId(), now);
        prerequisite.setConstraints(GoalConstraints.fromDescription(description));
        prerequisite.putMetadata("parentDescription", parent.getDescription());
        goalQueue.enqueue(prerequisite);
        activeGoal = null;
        currentPlan = null;
        steve.getMemory().setActiveGoal(prerequisite);
        moveTo(AgentState.OBSERVING, "created prerequisite goal");
        sendFeedback("I need a prerequisite first: " + description, now);
    }

    private String refinePrerequisite(String description) {
        if (description == null || !(steve.level() instanceof ServerLevel level)
                || !description.toLowerCase(java.util.Locale.ROOT).startsWith("gather ")) {
            return description;
        }
        GoalConstraints constraints = GoalConstraints.fromDescription(description);
        String item = constraints.targetItem();
        if (item.isBlank() || level.getServer() == null) return description;
        String normalized = item.contains(":") ? item : "minecraft:" + item;
        net.minecraft.resources.ResourceLocation target =
            net.minecraft.resources.ResourceLocation.tryParse(normalized);
        if (target == null) return description;
        net.minecraft.world.item.Item targetItem =
            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(target);
        if (targetItem == net.minecraft.world.item.Items.AIR) return description;
        boolean craftable = level.getServer().getRecipeManager().getRecipes().stream()
            .anyMatch(recipe -> recipe.getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING
                && recipe.getResultItem(level.registryAccess()).is(targetItem));
        return craftable ? "Craft " + item : description;
    }

    private void completeGoal(String reason, long now) {
        if (activeGoal == null) return;
        UUID parentId = activeGoal.getParentGoalId();
        String interruptedId = stringMetadata(activeGoal, "interruptedGoalId");
        String description = activeGoal.getDescription();
        UUID completedId = activeGoal.getId();
        activeGoal.complete(now);
        steve.getMemory().removeGoal(completedId);
        steve.getMemory().recordGoalOutcome(activeGoal, "completed: " + reason, now);
        steve.getMemory().clearActiveGoal();
        executor.stopAutonomousExecution();
        currentPlan = null;
        sendFeedback("Done: " + description, now);
        activeGoal = null;
        moveTo(AgentState.COMPLETED, "goal verified");
        moveTo(AgentState.IDLE, "ready for the next goal");
        if (parentId != null) goalQueue.resume(parentId, now);
        if (interruptedId != null) {
            try { goalQueue.resume(UUID.fromString(interruptedId), now); }
            catch (IllegalArgumentException ignored) { }
        }
    }

    private void blockGoal(String reason, long now) {
        if (activeGoal == null) return;
        activeGoal.block(reason, now);
        lastFailure = reason;
        steve.getMemory().recordGoalOutcome(activeGoal, "blocked: " + reason, now);
        steve.getMemory().clearActiveGoal();
        executor.stopAutonomousExecution();
        currentPlan = null;
        sendFeedback("I couldn't continue safely: " + reason, now);
        activeGoal = null;
        moveTo(AgentState.BLOCKED, reason);
    }

    private void maybeCreateProactiveGoal(long now, AutonomyMode mode) {
        idleTicks++;
        if (mode != AutonomyMode.PROACTIVE || !SteveConfig.AUTONOMY_PROACTIVE_MAINTENANCE.get()
                || idleTicks < SteveConfig.AUTONOMY_IDLE_THINK_INTERVAL.get()) return;
        idleTicks = 0;
        AgentGoal maintenance = AgentGoal.create("Deposit excess inventory into an authorized nearby container",
            GoalOrigin.MAINTENANCE, GoalPriority.MAINTENANCE, null, now);
        goalQueue.enqueue(maintenance);
    }

    private void sendFailureFeedback(ActionResult result, long now) {
        String code = result.getErrorCode() == null ? "unknown" : result.getErrorCode();
        String message = switch (code) {
            case ActionResult.ERROR_PATHING -> "That route is blocked; I'm trying another approach.";
            case ActionResult.ERROR_PROTECTED -> "I couldn't continue because the area is protected.";
            case ActionResult.ERROR_RESOURCE -> "I need another prerequisite before continuing.";
            default -> "The last step failed; I'm checking the world again.";
        };
        sendFeedback(message, now);
    }

    private void sendFeedback(String message, long now) {
        if (message == null || message.isBlank() || !SteveConfig.ENABLE_CHAT_RESPONSES.get()) return;
        if (message.equals(lastFeedback) && now - lastFeedbackTick < 40) return;
        if (now - lastFeedbackTick < 20) return;
        lastFeedback = message;
        lastFeedbackTick = now;
        steve.sendChatMessage(message);
    }

    private void moveTo(AgentState target, String reason) {
        if (target == null || stateMachine.getCurrentState() == target) return;
        if (!stateMachine.transitionTo(target, reason)) {
            stateMachine.forceTransition(target, reason);
        }
    }

    private void applyControllerIdentity(AgentGoal goal) {
        if (goal == null) return;
        Object value = goal.getMetadata().get("controllerUuid");
        UUID controllerUuid = null;
        if (value != null) {
            try {
                controllerUuid = UUID.fromString(String.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                // Invalid persisted controller metadata fails closed to the owner/preferred player.
            }
        }
        executor.setControllingPlayerUuid(controllerUuid);
    }

    private long currentTick() {
        if (steve.level() instanceof ServerLevel level && level.getServer() != null) {
            return level.getServer().getTickCount();
        }
        return localTick;
    }

    private String dimension() {
        return steve.level() instanceof ServerLevel level
            ? level.dimension().location().toString() : "unknown";
    }

    private static String formatResult(ActionResult result) {
        return (result.getErrorCode() == null ? "success" : result.getErrorCode()) + ": " + result.getMessage();
    }

    private static String stringMetadata(AgentGoal goal, String key) {
        Object value = goal.getMetadata().get(key);
        return value == null ? null : String.valueOf(value);
    }
}
