# Current-main autonomy audit

Snapshot audited: `04fbd543365f522e68e8a0ca0e9cd7f72f9d35da` (`main`, equal to `origin/main` on 2026-08-20 UTC).

This audit treats `src/main` as authoritative. `README.md`, `ANALYSIS_MATRIX.md`, `TECHNICAL_DEEP_DIVE.md`, `docs/INITIAL_IMPLEMENTATION_AUDIT.md`, and the previous `GATES.md` were checked only as claims. The current main already contains a first autonomy refactor, so the next implementation must harden and connect it rather than create a second executive.

## Reproducible baseline

- Java: OpenJDK 17.0.19.
- Gradle wrapper: 8.4.
- Forge/Minecraft: Forge 47.2.0, Minecraft 1.20.1, official mappings.
- `./gradlew test --rerun-tasks --stacktrace`: passed, 132 tests in 38 suites, zero failures/errors/skips.
- `./gradlew runGameTestServer --stacktrace`: passed; authoritative log marker reports `All 15 required tests passed :)`.
- Exact Resilience4j 2.1.0 probes exhausted one permit and measured `RateLimiter.decorateCompletionStage` returning after 752 ms for a 750 ms timeout and `Bulkhead.decorateCompletionStage` returning after 766 ms for a 750 ms wait, proving caller-thread blocking before the future is returned.
- Worktree was clean before the audit.
- `graphify-out/graph.json` did not exist, so no graph query was available.

## Capability matrix from production code

| Capability | Current production status | Evidence and limitation |
|---|---|---|
| Explicit goals | Connected but incomplete | `AgentGoal`, provenance, status, parent ID, constraints, budget, metadata, and bounded NBT exist. Per-goal config budgets are not captured, attempt counters are not driven by execution, and blocked goals are resumable because `BLOCKED` is not terminal. |
| Goal queue | Connected but incomplete | `GoalQueue` is used by `AutonomyController`. Its priority ranks place normal user work ahead of an active chain's prerequisite, queue/add/cancel commands are absent, and the old `PriorityCommandQueue` remains test-only dead code. |
| Receding plans | Connected but incomplete | `Plan`/`PlanStep` feed `ActionExecutor`, but every horizon creates a new plan, step starts/attempts are not recorded, failed steps are not persisted, prior horizons are discarded, and plan NBT is used only by tests. |
| Autonomous executive | Connected | `SteveEntity.tick` calls `ActionExecutor.tick` and then `AutonomyController.tick`. Planning is polled without blocking and action results return through `ActionCompletion`. |
| State machine | Connected | Observe/plan/execute/evaluate/recover/pause/block/complete/fail states exist. `AutonomyController.moveTo` can force invalid transitions, so transition-table errors can be hidden. |
| Observation snapshot | Connected but partial | Snapshot capture is bounded and not performed every tick. Important blocks and stations are mostly aggregate names without positions, hazard representation is absent, protected facts are not target-aware, and captures are not staggered across agents. |
| Long-term memory | Connected but partial | Episodes, facts, goal history, and pending goals are bounded and versioned. TTL expiration is never applied, relevance compares the complete goal string rather than tokens, failure memory is never written, and active plan progress/autonomy mode are not persisted. |
| LLM protocol | Connected but incomplete | Stable prompt sections, action schemas, horizon bounds, and async clients exist. Active subgoal is always null, a prerequisite replaces the primary goal in context, ACT-with-empty-tasks is accepted, trailing prose around JSON is accepted, and legacy private `reasoning` is still emitted by fallback responses. |
| Cache/resilience | Connected but unsafe at the caller boundary | Cache fingerprints include prompt plus sorted request parameters and malformed operational responses are not cached. Provider failures fall directly to the pattern matcher rather than trying another configured provider, autonomous invalid-response retries have no provider backoff, and rate-limiter/bulkhead acquisition is configured to wait up to 5/10 seconds on the `sendAsync` caller before the HTTP future is returned. |
| Deterministic goal verification | Connected but unsafe | Inventory, delivery, and positions are checked. Any unsupported semantic goal is currently declared complete after one successful exhausted horizon, which lets `inspect_inventory` complete an unrelated goal. Existing simulation/GameTest coverage encodes this false-positive behavior. |
| Deterministic recovery | Connected but incomplete | `RecoveryEngine` and failure fingerprints are in the autonomous path. `requiresReplanning` is evaluated before resource/tool-specific recovery, pathing goes directly back to the LLM, protected memory stores Steve's position instead of the denied target, and a null `missing_item` can trigger `Map.of` failure. |
| Restart | Partially connected | Active/pending goals and memory survive NBT, stale `BaseAction` objects are not serialized, and the controller replans. Current plan/progress/mode are not saved and there is no restart GameTest for an in-progress autonomous goal. |
| Stop/pause/interruption | Connected but partially tested | Generation checks discard late planning results and stop clears goals. Pause has a GameTest; absolute stop, new-command interruption, queueing, and specific cancellation lack production-path tests. |
| Action registry | Connected | Nineteen actions have descriptors, schemas, permissions, capabilities, factories, parser validation, and prompt exposure. |
| Crafting | Connected but incomplete | `CraftItemAction` reports one missing ingredient and `CraftingPlanner` exists. Recipe selection can include non-crafting recipes, intermediate quantities are not propagated generally, runtime deficits lose structured details, and `CraftingGoalDecomposer` is disconnected test-only code. |
| Smelting | Connected but incomplete | Furnace/input/fuel/output handling is tick-based and registered. Missing input/fuel can become prerequisites, but no complete zero-ingot executive scenario exists and some station/container policy checks are missing. |
| Gathering/mining | Connected but incomplete | `gather` internally runs another high-level `MineBlockAction`, hiding the nested action from plan progress. Tool-tier validation is too weak, broken-tool replacement is incomplete, torch placement can bypass BUILDING permission, and successful counts are not exposed as structured goal evidence. |
| Resource search | Connected but partial | `search_resource` is bounded, non-teleporting, permission-aware, and writes world memory. Sparse parity sampling can miss resources, the found coordinates are not consumed deterministically by mining, and navigation can stall at a solid target. |
| Inventory recovery | Partial | Deposit/withdraw actions exist, but autonomous inventory-full recovery only asks for a replan. Containers in protected areas are not excluded and missing containers can chat-spam until timeout. |
| Player-dependent work | Partial | Owner/controller UUID resolution avoids choosing a random replacement when an owner exists. Give/follow actions do not return `player_offline`, and successful delivery does not emit the observations expected by `GoalEvaluator`. |
| Permissions/safety | Connected but with bypasses | Descriptor permissions and per-tick permission revocation are checked; protected block mutations are generally guarded. Mining can place free torches under gathering permission, some container mutations ignore protected regions, and legacy combat/build actions teleport. Script execution is correctly disabled. |
| Communication | Partial | Executive feedback is coalesced, but several actions send progress every 20–60 ticks directly and `sendChatMessage` broadcasts to every player. |
| Observability | Partial | `/steve status <name>`, stop, pause, resume, goal submission, cache stats, and plugin stats exist. Goal inspection, queue details, memory diagnostics, per-agent autonomy mode, current task, active subgoal, and provider health are missing. |
| GUI | Legacy-only | The K-panel sends commands and displays chat. It does not display synchronized goal/state/task/mode data. |
| Collaboration | Preserved | `CollaborativeBuildManager` remains connected to building. It is intentionally not redesigned into a shared autonomous job board in this pass. |

## Registered action catalogue

The production plugin registers exactly these 19 plannable actions:

`pathfind`, `mine`, `gather`, `search_resource`, `place`, `build`, `attack`, `follow`, `pickup_item`, `give_item`, `deposit_item`, `withdraw_item`, `equip_item`, `unequip_item`, `drop_item`, `consume_item`, `inspect_inventory`, `craft`, `smelt`.

`craft`, `smelt`, and `search_resource` are genuinely registered and prompt-visible. They must be evolved in place, not reimplemented under parallel action names.

## Dead or disconnected implementation

- `PriorityCommandQueue` has tests but no production consumer.
- `CraftingGoalDecomposer` has tests but no production consumer.
- `Plan.save/load` is covered by tests but not owned by entity/controller persistence.
- `WorldFact.isExpired` is never used during recall.
- `SteveMemory.rememberFailure` is never called.
- `PlanningContext.activeSubgoal` is always supplied as null.
- The blocking `processNaturalLanguageCommandSync` has no caller but remains a dangerous public compatibility method.
- `CodeExecutionEngine` is intentionally disabled and must remain disabled.

## Documentation claims that are not currently true

- The prior `GATES.md` claims deterministic semantic completion is conservative, but an unrelated successful terminal step completes an unstructured goal.
- It claims plan progress is durable, but the controller never persists its `currentPlan`.
- It claims failed approaches are long-term memory, but `rememberFailure` is disconnected.
- It claims active subgoals are sent to the planner, but the field is always null.
- It claims all required stop/restart/executive scenarios are tested, while the current integration simulation bypasses the real controller and the iron GameTest begins with 15 of 16 ingots.
- Its JUnit total is stale (127 versus the audited 132).
- `README.md` overstates autonomous mining/location quality and documents successful semantic horizons as completion evidence.

## Implementation direction

The existing seam is correct and will be preserved:

`AutonomyController` owns goals, observations, planning, recovery, prerequisites, verification, budgets, persistence, and interrupts. `ActionExecutor` owns registry-backed tick execution and immutable start/completion handoff. `BaseAction` owns bounded server-thread Minecraft operations.

The hardening pass must make the existing end-to-end path honest: preserve root/subgoal context, persist plan checkpoints and per-agent mode, require explicit completion evidence, perform deterministic recovery before LLM replanning, expose structured action progress, connect crafting/smelting/search prerequisites, prevent permission bypasses, and replace simulation-only assurances with production-path GameTests.
