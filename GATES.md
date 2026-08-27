# Steve AI Autonomy Hardening Gates

Snapshot baseline: `04fbd543365f522e68e8a0ca0e9cd7f72f9d35da` on `main`.

- [x] G1: Current `main` was audited before production edits, with live code separated from stale documentation claims.
  EVIDENCE: `docs/CURRENT_MAIN_AUTONOMY_AUDIT.md`; baseline JUnit 132/132 and GameTest 15/15 passed.

- [ ] G2: Goal lifecycle, configured budgets, priorities, progress evidence, queue semantics, and bounded legacy-safe NBT are authoritative.
  CHECK: ./gradlew test --tests 'com.steve.ai.autonomy.*' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G3: Plan/PlanStep retain revision, step attempts/results, bounded completed-step history, and a restart-safe checkpoint without resuming BaseAction.
  CHECK: ./gradlew test --tests 'com.steve.ai.planning.*' --tests 'com.steve.ai.memory.*' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G4: Observation and memory expose compact relevant positions/stations/hazards, enforce TTL/bounds, and persist per-agent autonomy mode.
  CHECK: ./gradlew test --tests 'com.steve.ai.perception.*' --tests 'com.steve.ai.memory.*' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G5: Strict LLM decisions reject malformed/oversized/inconsistent output, preserve dynamic cache safety, use provider fallback/backoff, and never request or emit private reasoning.
  CHECK: ./gradlew test --tests 'com.steve.ai.llm.*' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G6: Action results expose bounded progress, missing prerequisites, delivery evidence, and denied target positions; deterministic recovery precedes LLM replanning and cannot crash on missing observations.
  CHECK: ./gradlew test --tests 'com.steve.ai.action.*' --tests 'com.steve.ai.autonomy.Recovery*' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G7: Crafting, smelting, tool replacement, inventory recovery, and bounded resource search compose through executive-visible goals without hidden high-level nested actions.
  EVIDENCE: pending

- [ ] G8: Permissions remain authoritative at mutation time, protected containers/blocks are avoided, gathering cannot place free blocks, chunks fail closed, and no command/script/reflection bypass exists.
  EVIDENCE: pending

- [ ] G9: Interrupt, queue, pause, resume, cancel, absolute stop, stale-future invalidation, OFF, GOAL_DRIVEN, and PROACTIVE semantics are explicit and tested.
  EVIDENCE: pending

- [ ] G10: Production-path integration proves failure -> deterministic recovery -> new observation -> replan -> success without another user command.
  EVIDENCE: pending

- [ ] G11: Forge GameTests cover autonomous item acquisition from prerequisites, protected replanning, restart replanning, absolute stop, malformed planner output, and no unauthorized mutation.
  CHECK: ./gradlew runGameTestServer --stacktrace
  EXPECT: All /[0-9]+/ required tests passed
  EVIDENCE: pending

- [ ] G12: Commands/status expose mode, state, primary goal, subgoal, plan revision, action, queue, failure, budgets, memory counts, and provider health without secrets.
  EVIDENCE: pending

- [ ] G13: Documentation and example config match final production behavior and state remaining limitations honestly.
  EVIDENCE: pending

- [ ] G14: Final JUnit suite passes from the final source snapshot.
  CHECK: ./gradlew test --rerun-tasks --stacktrace
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G15: Final clean production build and release-JAR verification pass with Java 17/Gradle 8.4.
  CHECK: ./gradlew clean build --stacktrace
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G16: Final repository hygiene, graph update, JAR contents, and measured test/artifact totals are recorded.
  CHECK: git diff --check
  EXPECT: /^$/
  EVIDENCE: pending
