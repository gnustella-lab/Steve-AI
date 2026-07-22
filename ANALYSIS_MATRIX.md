# Steve-AI Analysis Matrix

## Phase 1 — Foundation

| # | Feature | Current State | Files Involved | Limitations | Improvement Proposed | Priority | Risk |
|---|---------|--------------|----------------|-------------|---------------------|----------|------|
| 1 | Persistent Inventory | Basic `SteveInventory` with NBT persistence, stacking, insert/remove/drainAll/summarize | `SteveInventory.java`, `SteveEntity.java` | No equipment slots, no tool selection, no durability tracking, no give/deposit/withdraw/drop/consume | Add equipment slots, auto tool selection, durability tracking, transfer methods | **High** | Low |
| 2 | Inventory Actions | None registered | `CoreActionsPlugin.java`, `ActionRegistry.java` | No LLM-plannable inventory actions | Register: pickup_item, give_item, deposit_item, withdraw_item, equip_item, unequip_item, drop_item, consume_item, inspect_inventory | **High** | Low |
| 3 | Crafting System | `CraftItemAction` is a stub returning "not implemented" | `CraftItemAction.java` | No recipe lookup, no ingredient resolution, no furnace support | Implement `CraftingPlanner`, `RecipeDependencyGraph`, `IngredientResolver`, `CraftItemAction`, `SmeltItemAction`, `UseCraftingTableAction`, `UseFurnaceAction` | **High** | Medium |
| 4 | Action Catalog | Dynamic registry with schemas, conflict detection | `ActionRegistry.java`, `CoreActionsPlugin.java`, `PromptBuilder.java`, `TaskValidator.java` | Legacy switch removed; schema-based validation in place | Already done — no switch in `ActionExecutor` | Low | Low |
| 5 | ActionResult | Basic success/message/replanning | `ActionResult.java` | Missing partialSuccess, retryable, errorCode, observations | Extend with structured fields per spec | **High** | Low |
| 6 | Error Recovery | Actions fail and clear queue | `ActionExecutor.java` | No deterministic recovery, no error categorization | Implement `RecoveryPolicy` with categorized error handling | **High** | Medium |
| 7 | Property by UUID | `SteveAccessProfile` with UUID ownership | `SteveAccessProfile.java`, `SteveEntity.java`, `SteveManager.java` | Already implemented | Already done | Low | Low |
| 8 | Config | Per-provider config, behavior settings | `SteveConfig.java` | No custom LLM provider, no cost/latency budgets | Add custom provider config, budget limits | Medium | Low |
| 9 | Tests | Unit tests for inventory, parser, schema, validator | `src/test/java/` | No crafting tests, no recovery tests | Add tests for all new components | **High** | Low |
| 10 | CI | Only release workflow | `.github/workflows/release.yml` | No test/build CI on PRs | Add CI workflow for `gradle test build` | Medium | Low |

## Phase 2 — Survival

| # | Feature | Current State | Limitations | Improvement | Priority | Risk |
|---|---------|--------------|-------------|-------------|----------|------|
| 11 | Pickup/Transfer | `SteveEntity.pickUpItem` exists | No dedicated action, no container interaction | Use new inventory actions | High | Low |
| 12 | Crafting | Stub | See #3 | See #3 | High | Medium |
| 13 | Tools | `MineBlockAction` equips iron pickaxe | No inventory-based tool management, no replacement | Use `SteveInventory` tool selection | High | Low |
| 14 | Smelting | Not implemented | No furnace interaction | `SmeltItemAction`, `UseFurnaceAction` | High | Medium |

## Phase 3 — Planning

| # | Feature | Current State | Limitations | Improvement | Priority | Risk |
|---|---------|--------------|-------------|-------------|----------|------|
| 15 | ObservationSnapshot | `WorldKnowledge` provides summaries | No immutable snapshot, no filtering | Create `ObservationSnapshot` | Medium | Low |
| 16 | Persistent Plan | `ActionExecutor` has taskQueue only | No plan ID, no dependencies, no checkpoints | Create `Plan` model with all fields | Medium | Medium |
| 17 | Command Queue | FIFO queue only | No priority, no pause/resume/cancel | Priority queue with management | Medium | Medium |

## Phase 4 — Memory & Security

| # | Feature | Current State | Limitations | Improvement | Priority | Risk |
|---|---------|--------------|-------------|-------------|----------|------|
| 18 | Memory | `SteveMemory` has goal + recentActions | No categories, no spatial knowledge | Expand with categories | Medium | Low |
| 19 | Permissions | `PermissionManager` with regions | No granular capabilities, no audit log | Expand capabilities, add audit | Medium | Medium |

## Phase 5 — Multiagent

| # | Feature | Current State | Limitations | Improvement | Priority | Risk |
|---|---------|--------------|-------------|-------------|----------|------|
| 20 | Coordination | `CollaborativeBuildManager` for builds | Only for building, no resource reservation | Generalize to `SharedJobBoard`, `ResourceReservationManager` | Low | High |

## Phase 6 — Experience & Providers

| # | Feature | Current State | Limitations | Improvement | Priority | Risk |
|---|---------|--------------|-------------|-------------|----------|------|
| 21 | Custom Provider | Only OpenAI/Groq/Gemini | No OpenAI-compatible endpoints | Add custom provider config | Medium | Low |
| 22 | Metrics | Basic cache stats | No per-provider/model/metrics | Add metrics system | Low | Low |
| 23 | Commands | spawn/remove/list/stop/tell/status | No inventory/memory/debug/queue commands | Add comprehensive commands | Medium | Low |
