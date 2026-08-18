# Steve AI Implementation Matrix

This document reflects the current Java source. Historical phase documents are not architectural authority.

| Area | Current implementation | Status | Remaining limitation |
|---|---|---|---|
| Persistent goals | `AgentGoal`, `GoalQueue`, `GoalStatus`, `GoalOrigin`, parent IDs, metadata, budgets, bounded NBT | Implemented | Goal-chain persistence is intentionally sequence-based, not a DAG |
| Autonomous executive | `AutonomyController` runs observe, plan, act, evaluate, recovery, prerequisite, and reflection phases | Implemented | Full survival reliability still depends on world navigation and recipes |
| Planning horizon | `Plan` and `PlanStep`, configurable horizon, async `AutonomyPlanner` seam | Implemented | LLM quality remains provider-dependent |
| Observation | Immutable `ObservationSnapshot`, `ObservationService`, bounded `WorldKnowledge` sampling | Implemented | Targeted cave/vein search can be improved further |
| Memory | `SteveMemory`, `WorldFact`, `EpisodicMemoryEntry`, bounded goal and failure history | Implemented | No vector database is used or required |
| Recovery | `RecoveryEngine`, `RecoveryDecision`, `FailureTracker`, error fingerprints | Implemented | Some legacy actions still need richer error observations |
| Goal verification | `GoalEvaluator` for inventory quantity, delivery result, positions, and successful semantic horizons | Implemented | Unsupported semantic goals use conservative terminal-action evidence |
| LLM protocol | Stable prompt sections, decision schema, bounded response parsing, fake planner interface | Implemented | Legacy response fields remain accepted for compatibility |
| Async infrastructure | Existing provider clients, fallback, retry, circuit breaker, rate limiter, bulkhead, cache | Preserved | The deprecated synchronous planner remains available for legacy callers |
| Cache safety | Request fingerprint includes prompt and sorted response-affecting parameters | Implemented | Cache invalidation remains TTL-based |
| Action registry | Descriptor, schema, permission, capability, factory, prompt catalog | Implemented | Plugin API versioning remains future work |
| Core actions | 19 registered actions, including crafting, smelting, inventory operations, and `search_resource` | Implemented/partial | Individual action behavior remains bounded rather than fully autonomous |
| Crafting prerequisites | Crafting reports missing ingredient observations; executive creates prerequisite goals | Implemented | Complex recipe planning is sequential, not a dependency DAG at runtime |
| Smelting prerequisites | Furnace, input, fuel, output capacity, and protected placement are reported | Implemented/partial | Furnace fuel policy is intentionally conservative |
| Search/exploration | `SearchResourceAction` uses bounded candidates, navigation, permissions, memory, and observations | Implemented | It does not teleport or perform unbounded scans |
| Permissions | UUID ownership, descriptor permissions, protected regions, origin guardrails | Implemented | Protected-region persistence is still server-lifecycle scoped |
| Restart | Active and pending goals, memory, budgets, and facts persist; transient actions are discarded | Implemented | A restart always creates a fresh observation and plan |
| Interrupts | User goals supersede active work, pause previous goals, and stop cancels without automatic resume | Implemented | Queue fairness can be expanded with aging if needed |
| Autonomy modes | `OFF`, `GOAL_DRIVEN`, `PROACTIVE` with safe defaults and maintenance opt-in | Implemented | Proactive maintenance is intentionally narrow |
| Collaboration | Existing `CollaborativeBuildManager` preserved | Preserved | Shared job board/resource reservations are future work |
| Tests | Unit tests for goals, plans, memory, parser, evaluator, recovery, state machine, and fake planner simulation | Implemented/partial | Dedicated Forge GameTests for the full executive loop remain to be added |
| Documentation | README, config example, initial audit, and this matrix updated | Implemented | Historical documents still describe prior phases and should be treated as archival |

## Verified action catalogue

The current core catalogue contains:

`pathfind`, `mine`, `gather`, `search_resource`, `place`, `build`, `attack`, `follow`, `pickup_item`, `give_item`, `deposit_item`, `withdraw_item`, `equip_item`, `unequip_item`, `drop_item`, `consume_item`, `inspect_inventory`, `craft`, `smelt`.

All are registered through `ActionDescriptor` and exposed to prompt generation only when they have an explicit schema.
