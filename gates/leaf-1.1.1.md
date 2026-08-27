# Gates: durable goal, plan, and memory domain

Scope: Make goal/plan progress, queue order, bounded persistence, and restart checkpoints authoritative without executing stale actions.

- [ ] G1: Goal intent/progress/configured budget and priority behavior are covered by tests that fail on the audited baseline and pass after implementation.
  CHECK: ./gradlew test --tests 'com.steve.ai.autonomy.AgentGoalTest' --tests 'com.steve.ai.autonomy.GoalQueueTest' --tests 'com.steve.ai.autonomy.GoalEvaluatorTest' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G2: Plan revisions preserve bounded completed-step history and record starts/attempts/failures.
  CHECK: ./gradlew test --tests 'com.steve.ai.planning.PlanTest' --tests 'com.steve.ai.planning.PlanStepTest' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G3: Memory round-trips active goal, pending/paused goals, plan checkpoint, per-agent mode, facts, episodes, and old NBT with hard bounds and TTL-aware recall.
  CHECK: ./gradlew test --tests 'com.steve.ai.memory.*' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G4: Dead `PriorityCommandQueue` is removed or converted into a single non-parallel authority, and no stale production consumer remains.
  EVIDENCE: pending
