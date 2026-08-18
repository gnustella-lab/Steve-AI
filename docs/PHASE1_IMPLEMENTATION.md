# Phase 1 Historical Record

This file records the earlier inventory, ownership, and action-catalogue phase. It is archival, not the current architecture contract. The Java source under `src/main` and `README.md` describe the current implementation.

## Features carried forward

- `SteveInventory` remains the canonical bounded inventory abstraction with equipment slots and versioned NBT.
- `SteveAccessProfile` remains UUID-based and fails closed for unowned legacy entities.
- `ActionRegistry` remains the single source for action factories, descriptors, schemas, prompt exposure, and action permissions.
- Async provider clients, fallback, retry, circuit breaker, cache, interceptors, and tick-based action execution remain in place.
- Crafting, smelting, inventory transfer actions, and bounded resource search are now registered and connected to the autonomous executive.

## Superseded by the autonomy refactor

- A string-only current goal is no longer the primary goal model.
- `ActionExecutor` is no longer the cognitive owner of recovery and replanning.
- Crafting and smelting failures report prerequisite observations to `AutonomyController`.
- Memory is no longer limited to a current string and recent action descriptions.
- Transient `BaseAction` instances are not resumed blindly after restart.

## Current verification

Run the verification commands from the repository root after the final source edit:

```text
./gradlew test --rerun-tasks
./gradlew clean build
./gradlew runGameTestServer

git diff --check
```

The final report must quote fresh results from the current worktree. Historical test counts, hashes, and GameTest claims from earlier phases must not be reused as evidence for the autonomy refactor.
