# Gates: executive integration, commands, and Forge behavior

Scope: Wire the hardened domain/runtime/protocol into the real entity tick, persistence, interruption semantics, diagnostics, and GameTests.

- [ ] G1: The real controller performs root/subgoal observe-plan-act-evaluate-recover cycles, reuses plan revisions, consumes start/completion handoffs, and never completes an unrelated goal.
  EVIDENCE: pending

- [ ] G2: Interrupt, queued command, specific cancel, pause/resume, absolute stop, stale planning result, OFF, GOAL_DRIVEN, and opt-in PROACTIVE behavior are production-path tested.
  EVIDENCE: pending

- [ ] G3: Restart restores goal/queue/mode/plan evidence, discards transient action/future state, observes, and replans.
  EVIDENCE: pending

- [ ] G4: Commands expose goal, queue, memory, mode, state, plan revision, current action, failure, budget, and provider health without secrets; GUI receives minimal synchronized status if safe.
  EVIDENCE: pending

- [ ] G5: Forge GameTests prove prerequisite acquisition, protected replanning, restart, stop, malformed output, and verified completion without another user command.
  CHECK: ./gradlew runGameTestServer --stacktrace
  EXPECT: All /[0-9]+/ required tests passed
  EVIDENCE: pending
