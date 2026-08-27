# Make Steve AI finish persistent goals through observation, recovery, and replanning

This ExecPlan is a living document. `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be updated while implementation proceeds. The repository root also contains `PLAN.md`, `GATES.md`, and leaf gates under `gates/`; those are the executable completion ledger for this plan.

## Purpose / Big Picture

After this change, a player command is durable work rather than a disposable task list. Steve can retain the root goal while pursuing child prerequisites, observe after each bounded horizon, respond to structured action failures with deterministic recovery before spending another LLM call, replan when the world changes, verify completion from server-produced evidence, survive restart by discarding stale action state and replanning, and stop absolutely when ordered. The behavior is demonstrated by offline JUnit tests and real Forge GameTests using sequential fake planners, never live provider calls.

## Progress

- [x] (2026-08-20T01:16:17Z) Froze clean `main` at `04fbd543365f522e68e8a0ca0e9cd7f72f9d35da`, confirmed Java 17/Gradle 8.4, ran 132 JUnit tests and 15 Forge GameTests successfully.
- [x] (2026-08-20T01:16:17Z) Audited current production paths and recorded live, partial, dead, and falsely documented capabilities in `docs/CURRENT_MAIN_AUTONOMY_AUDIT.md`.
- [ ] Implement durable goal intent/progress, priority, plan revision/history, mode/checkpoint persistence, and TTL-aware memory.
- [ ] Implement structured action evidence, deterministic recovery, safe prerequisites, tool/search/container/player/chunk handling, and mutation guards.
- [ ] Tighten observation, planning context, response schema, fallback/cache/provider behavior, and bounded backoff.
- [ ] Integrate root/subgoal executive behavior, queue/cancel/stop/restart semantics, diagnostics, and minimal GUI state.
- [ ] Add and pass production-path JUnit and Forge GameTests for all mandatory scenarios.
- [ ] Reconcile documentation, run independent review, final JUnit/GameTest/clean build/JAR/diff/graph gates, and record measured evidence.

## Surprises & Discoveries

- Observation: Current main already contains an autonomy refactor, but its semantic evaluator completes any unstructured goal after one successful exhausted horizon.
  Evidence: `GoalEvaluator.evaluate` returns COMPLETE for `planExhausted && lastResult.success`; `AutonomyIntegrationSimulationTest` and the protected GameTest assert that behavior.
- Observation: The previous completion ledger is not trustworthy for the current snapshot.
  Evidence: it reports 127 JUnit tests while current XML reports 132, and it claims plan/failure-memory wiring that has no production consumer.
- Observation: The executive/runtime seam is already suitable and should be evolved, not replaced.
  Evidence: `SteveEntity.tick` runs `ActionExecutor.tick` then `AutonomyController.tick`; immutable action completion is consumed on tick.

## Decision Log

- Decision: Preserve `AutonomyController -> ActionExecutor -> BaseAction` ownership and remove/evolve dead parallel abstractions instead of adding a second framework.
  Rationale: This protects plugin, permission, tick, event, and async infrastructure while correcting the live path.
  Date/Author: 2026-08-20, Deep.
- Decision: Unsupported semantic goals require a planner COMPLETE decision plus explicit server-produced `goal_evidence`; horizon exhaustion alone is never completion.
  Rationale: It prevents unrelated actions from falsely finishing a global objective while still permitting conservative semantic verification.
  Date/Author: 2026-08-20, Deep.
- Decision: Persist a bounded plan checkpoint for diagnostics/progress, but never serialize or resume a `BaseAction` continuation.
  Rationale: Restart safety is more important than replaying stale world assumptions.
  Date/Author: 2026-08-20, Deep.
- Decision: Derived prerequisites are generic structured child goals, not an iron-specific script.
  Rationale: Crafting recipes, action observations, and the registry should drive the same mechanism for arbitrary resources.
  Date/Author: 2026-08-20, Deep.
- Decision: Persisted goal/fact timestamps use the world's monotonic game time at the executive boundary; TTL facts with a negative legacy clock delta expire safely.
  Rationale: The process tick counter resets on server restart and would otherwise keep stale facts or move goal timestamps backward.
  Date/Author: 2026-08-20, Deep.

## Outcomes & Retrospective

Implementation is in progress. The audited baseline passes its existing tests but does not yet meet the mission's behavioral acceptance because it can falsely complete goals, does not persist plan checkpoints/mode, and lacks several mandatory production-path scenarios.

## Context and Orientation

`SteveEntity` is the Forge `PathfinderMob` and server-tick owner. `ActionExecutor` creates registry actions and ticks one `BaseAction` at a time. `AutonomyController` owns goals, planning futures, observations, recovery, and evaluation. `AgentGoal` and `GoalQueue` are under `autonomy`; `Plan` and `PlanStep` are under `planning`; `ObservationSnapshot` and `ObservationService` are under `perception`; `SteveMemory` and `WorldFact` are under `memory`; provider clients and the strict parser are under `llm`; built-in actions are registered by `CoreActionsPlugin`. `SteveGameTests` exercises the real Forge server.

A planning horizon means the next small sequence of executable tasks, not the entire global solution. A prerequisite goal is a child objective, such as obtaining a suitable pickaxe, whose successful completion resumes its parent. Goal evidence is a bounded structured observation produced by trusted server action code, such as delivered item/count, reached position, mined block/count, or completed structure.

## Plan of Work

First evolve the pure domain. Add explicit goal intent and bounded progress counters, snapshot configured budgets per new goal, correct priority order, make blocked work ineligible for automatic resume, and preserve one plan identity across bounded revisions with archived completed-step summaries. Extend `SteveMemory` to own a bounded plan checkpoint and per-agent autonomy mode, clear before full loads, apply data-version defaults, enforce TTL-aware relevance, and remove the unused parallel command queue.

Second harden action and recovery contracts. Bound `Task` and `ActionResult` data, expose target/progress/missing/delivery evidence, record action starts, prioritize deterministic resource/tool/inventory/player/protected handling before generic replanning, and persist failure/protected facts at the actual target. Replace the hidden gather-to-mine BaseAction nesting with shared direct mining behavior. Correct tool-tier selection, broken-tool replacement, torch authorization/material consumption, chunk/protected container guards, and player-offline results. Improve resource search so it records and returns coordinates that mining can consume without teleporting.

Third harden perception and LLM boundaries. Add positioned relevant blocks/stations/hazards while retaining strict scan and prompt budgets. Build root-goal and active-subgoal sections from the actual goal chain. Tighten `ResponseParser` decision consistency and exact JSON envelope. Update local fallback to the operational schema without reasoning, attempt another configured healthy provider before local fallback, retain dynamic cache fingerprints, and apply bounded invalid/provider backoff.

Fourth integrate the executive. Reuse a plan across revisions, consume start/completion handoffs, evaluate deterministic conditions before planning, create deterministic delivery/inventory/prerequisite horizons, propagate blocked child context back to its parent where alternatives remain, and persist checkpoint/mode on save. Add explicit queue and cancel APIs, absolute stop latching, per-agent mode commands, richer safe diagnostics, and minimal synced GUI state if it does not require a protocol rewrite.

Finally replace misleading tests and documentation. Every new behavior starts with a failing focused test. Add production-path GameTests for automatic prerequisite/replan flow, protected mutation, restart, absolute stop, malformed planner output, and verified completion. Update README, configuration example, analysis matrix, technical documentation, and the audit/ledger only after final behavior exists.

## Concrete Steps

All commands run from `/home/mello/Área de trabalho/Steve-AI` with Java 17.

For each vertical slice:

    ./gradlew test --tests '<focused test class>' --rerun-tasks

The new test must fail for the intended missing behavior, then pass after the production change. After each leaf:

    ./gradlew test --rerun-tasks

Final verification:

    ./gradlew test --rerun-tasks --stacktrace
    ./gradlew clean build --stacktrace
    ./gradlew runGameTestServer --stacktrace
    git diff --check
    graphify update .

If `graphify` has no initialized graph, initialize/update it only after source work, then query the resulting report rather than treating generated graph files as source truth.

## Validation and Acceptance

A fake planner sequence must drive a real entity-owned controller through an initial failure, deterministic recovery or child prerequisite, a second observation/horizon, action success, deterministic goal evidence, and `COMPLETED -> IDLE` without another command. A protected target must remain unchanged while the original goal replans. Saving and loading an entity mid-goal must retain the goal/queue/mode/checkpoint but execute no stale action until a fresh observation and plan. Stop must cancel current action/future/all goals and prevent late callbacks or proactive work from restarting them. Invalid JSON, unknown actions, schema errors, oversized horizons, and provider failure must be bounded and end in safe fallback or BLOCKED state. Final JUnit XML must contain zero failures/errors/skips, the GameTest log must contain `All N required tests passed`, the clean build must pass release-JAR verification, and the installable JAR must contain autonomy classes, SPI, `mods.toml`, and Jar-in-Jar metadata.

## Idempotence and Recovery

Tests and builds are repeatable and use no live provider credentials. NBT loaders tolerate missing legacy tags and unknown malformed values without widening permissions. If a slice fails, keep the failing regression and revert only that slice's production edits, never user data or unrelated work. Generated build/run/graph files are not architectural evidence and may be regenerated. No branch switch, reset, or bare stash is required.

## Artifacts and Notes

Baseline evidence:

    JUnit: suites=38 tests=132 failures=0 errors=0 skipped=0
    Forge: All 15 required tests passed :)
    Commit: 04fbd543365f522e68e8a0ca0e9cd7f72f9d35da

Audit: `docs/CURRENT_MAIN_AUTONOMY_AUDIT.md`.

## Interfaces and Dependencies

`AgentGoal` must expose bounded intent/progress APIs and NBT round-trip. `GoalEvaluator` must consume those plus trusted `ActionResult` evidence. `Plan` must keep one `planId`, increasing revision, current steps, bounded completed-step summaries, and checkpoint NBT. `ActionExecutor` must expose immutable task-start and task-completion handoffs without owning goal logic. `SteveMemory` must persist active/pending goals, plan checkpoint, mode, episodes, facts, and history under a data version. `PlanningContext` must carry root goal, active subgoal, observation, selected memory, recent completed plan steps, last result, failed strategies, policy, and remaining budget. `AutonomyPlanner` remains the fakeable asynchronous interface. `ResponseParser.ParsedResponse` remains immutable and bounded. No dependency beyond existing Forge, Gson, Caffeine, Resilience4j, and JUnit is introduced.

Revision note: created after the current-main source audit to replace the prior aspirational completion ledger with an executable hardening plan.
