# Initial Implementation Audit

This audit records the implementation that existed on `main` before the autonomy refactor. The Java source under `src/main` was treated as authoritative. README files and historical phase documents were used only to identify claims to verify.

## Baseline

- Repository: `gnustella-lab/Steve-AI`
- Baseline commit: `95c8d2376f63dfe4b0cf4f07d8baa32f12dd8e61`
- Runtime target: Minecraft 1.20.1, Forge 47.2.x, Java 17
- Existing test baseline: `./gradlew test --rerun-tasks`, successful before the refactor
- Existing untracked generated data: `graphify-out/`

## Verified architecture before the refactor

| Area | Source evidence | Status before refactor | Finding |
|---|---|---|---|
| Entity lifecycle | `SteveEntity.tick`, `addAdditionalSaveData`, `readAdditionalSaveData` | Implemented | Entity persistence, inventory, access profile, and tick execution existed. The cognitive controller did not exist. |
| Runtime actions | `ActionExecutor`, `BaseAction`, `ActionResult` | Implemented/partial | Tick execution, interceptors, registry creation, async planning, and structured results existed. Recovery and goal evaluation were still owned by the executor. |
| Goal representation | `ActionExecutor.currentGoal`, `SteveMemory.currentGoal` | Partial | The primary goal was a string summary. No persistent goal identity, provenance, parent relation, or goal status existed. |
| Plans | `planning.Plan`, `PriorityCommandQueue` | Partial/disconnected | These models existed and had NBT support, but normal entity execution used a private `Queue<Task>` in `ActionExecutor`. |
| State machine | `AgentState`, `AgentStateMachine` | Partial | Explicit planning/executing/paused/completed/failed states existed, but observation, evaluation, and recovery phases were absent. |
| Perception | `WorldKnowledge`, `ObservationSnapshot` | Partial | A snapshot DTO existed, but `WorldKnowledge` was a temporary scan and was not historical memory. No scheduled observation service existed. |
| Memory | `SteveMemory`, `WorldKnowledge` | Partial | Recent action strings and one current goal were persisted. Episodic, spatial, procedural failure, and goal history were absent. |
| LLM context | `TaskPlanner`, `PromptBuilder` | Partial | The prompt exposed current player/world context and action schemas. It did not expose persistent goal state, failed approaches, budgets, or plan progress. |
| Response protocol | `ResponseParser` | Implemented/partial | Parsing was bounded and schema-validated for `summary` and `tasks`. Decision and goal-status fields did not exist. |
| Async safety | `AsyncLLMClient`, `ResilientLLMClient`, `LLMExecutorService`, `ActionExecutor.tick` | Implemented | HTTP calls were asynchronous and results were consumed from the tick path. The legacy synchronous method remained available and was explicitly marked blocking. |
| Cache | `LLMCache`, `ResilientLLMClient` | Implemented | The request fingerprint included prompt and sorted request parameters. Existing cache-key regression coverage passed. |
| Permissions | `ActionRegistry`, `ActionDescriptor`, `PermissionManager`, `SteveAccessProfile` | Implemented/partial | Action schemas and descriptor permissions were authoritative for registered actions. Autonomous provenance and origin-specific guardrails were absent. |
| Inventory | `SteveInventory`, `SteveEntity` | Implemented | Canonical bounded inventory, equipment, tool swaps, NBT persistence, and server-thread mutations existed. |
| Crafting | `CraftingPlanner`, `IngredientResolver`, `CraftItemAction` | Partial | Recipe planning and actions existed and were registered. Missing ingredients stopped the action instead of producing an executive-visible prerequisite goal. |
| Smelting | `SmeltItemAction`, core descriptor | Partial | Furnace interaction was registered and tick-based, but missing furnace/input/fuel recovery was not connected to an executive. |
| Exploration/search | `MineBlockAction`, `GatherResourceAction` | Partial | Mining searched a narrow forward/tunnel strategy and did not provide a bounded general resource-search primitive. |
| Collaboration | `CollaborativeBuildManager`, `BuildStructureAction` | Implemented/limited | Collaborative building and block claims existed. It was intentionally preserved rather than rewritten into the first autonomy pass. |
| Persistence after restart | Entity NBT and `SteveMemory` | Partial | Inventory, access profile, recent actions, and string goal state persisted. Transient actions were not safely rehydrated, but there was no controller to discard them and replan. |
| Commands | `SteveCommands` | Partial | Spawn, tell, stop, list, remove, and global status existed. Goal, pause/resume, and per-agent autonomy diagnostics were absent. |
| Documentation | README, phase documents, analysis matrix | Stale in places | Several documents described aspirational or pre-Phase-2 behavior, including no crafting, direct one-shot planning, and memory reset claims that no longer matched `main`. |

## Registered action catalogue at baseline

The core plugin registered these 18 actions before the new bounded search action was added:

- `pathfind`
- `mine`
- `gather`
- `place`
- `build`
- `attack`
- `follow`
- `pickup_item`
- `give_item`
- `deposit_item`
- `withdraw_item`
- `equip_item`
- `unequip_item`
- `drop_item`
- `consume_item`
- `inspect_inventory`
- `craft`
- `smelt`

The registry, descriptors, schemas, prompt exposure, validator, and permission lookup were connected through `ActionRegistry`. The old documentation claim that crafting was not registered was false for the audited `main` source.

## Dead or disconnected claims found

1. The documented direct one-shot planning model was an accurate description of the old runtime, not the desired architecture.
2. `Plan` and `PriorityCommandQueue` existed but were not the authoritative progress path of `ActionExecutor`.
3. `ObservationSnapshot` existed, but there was no scheduler or integration with persistent goal evaluation.
4. `SteveMemory` was not a long-term world memory despite documentation describing world features and persistent context.
5. Crafting/smelting classes and descriptors existed, but missing materials did not create executive-visible prerequisite work.
6. `RecoveryPolicy` existed, but `requiresReplanning` could still lead to clearing the queue and returning to idle or restarting the original command.
7. `StructureGeneratorsTest` was a placeholder and therefore was not evidence of structure-generation correctness.
8. Historical documentation used old action names such as `MoveToAction` and `AttackAction`, while the source used `PathfindAction` and `CombatAction`.

## Refactor target

The refactor adds a separate `AutonomyController` and keeps `ActionExecutor` as the server-thread action runtime. Goals, plans, observations, memories, failures, recovery decisions, and verification are now explicit bounded components. The final verification report must be generated from the post-refactor worktree, not from this baseline document.
