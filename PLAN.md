# Plan: harden Steve AI into a truthful persistent autonomous agent

Depth: tree 4   Mode: orchestrated
Budget note: this is a cross-cutting Forge refactor with domain, runtime, LLM, persistence, gameplay, GameTest, and documentation gates.

## Contract

- Interfaces: `AutonomyController` remains the sole cognitive executive; `ActionExecutor` remains the registry-backed tick runtime; `BaseAction` remains bounded server-thread mutation. Futures return immutable planning data and are consumed only by tick.
- Goal evidence: `AgentGoal` owns bounded progress counters. `GoalEvaluator` never equates an exhausted successful horizon with goal completion. Unsupported semantic completion requires both a planner COMPLETE decision and explicit server-produced `goal_evidence`.
- Plans: one plan ID remains associated with a goal across horizon revisions. `PlanStep` records start, attempts, result, and status. A bounded checkpoint may persist, but restart discards transient action execution and replans.
- Prerequisites: actions report structured missing item/tool/station/fuel/capacity observations. The executive creates child goals and resumes the parent. No action may hide another high-level BaseAction.
- Safety: every mutation is server-thread, loaded-chunk, descriptor-permission, and protected-region aware. Derived/autonomous origins never widen permission. No scripts, commands, reflection, arbitrary Java, or teleport-based resource search.
- LLM: strict bounded operational JSON, no private reasoning, no more than one in-flight request per Steve, request-context cache fingerprint, bounded retry/backoff, and configured provider fallback before local fallback.
- Data ownership: leaf 1 owns goal/plan/memory domain files; leaf 2 owns action runtime/actions/recovery/security catalogue files; leaf 3 owns perception and LLM protocol/resilience files; leaf 4 owns executive/entity/config/commands/GUI/GameTests. Shared API names in this contract are fixed before fan-out.
- Naming: Java 17, package conventions already present, English production messages/docs, four-space indentation, no new parallel queue/planner/recovery framework.

## Tree

- 1 Persistent autonomous Steve
  - 1.1 Durable domain and progress .......... gates/node-1.1.md
    - 1.1.1 Goals, plans, memory ............. gates/leaf-1.1.1.md
  - 1.2 Safe execution and recovery .......... gates/node-1.2.md
    - 1.2.1 Actions and deterministic recovery  gates/leaf-1.2.1.md
  - 1.3 Perception and planning protocol ..... gates/node-1.3.md
    - 1.3.1 Observation, prompts, parser, LLM . gates/leaf-1.3.1.md
  - 1.4 Executive integration and UX ......... gates/node-1.4.md
    - 1.4.1 Controller, commands, GameTests ... gates/leaf-1.4.1.md

## Status log

- 2026-08-20T01:16:17Z baseline and current-main audit completed before production edits.
- 2026-08-20T01:16:17Z architecture contract fixed; implementation leaves prepared for test-first work.
- 2026-08-20T01:16:17Z leaves 1.1.1, 1.2.1, and 1.3.1 dispatched with disjoint file ownership and strict RED/GREEN gates.
