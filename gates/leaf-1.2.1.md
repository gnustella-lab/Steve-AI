# Gates: safe actions and deterministic recovery

Scope: Produce structured action evidence and prerequisites, eliminate safety bypasses, and make bounded deterministic recovery execute before LLM replanning.

- [ ] G1: Resource/tool/inventory/pathing/protected/player/chunk/validation failures produce deterministic bounded decisions without null crashes or infinite repeats.
  CHECK: ./gradlew test --tests 'com.steve.ai.autonomy.RecoveryEngineTest' --tests 'com.steve.ai.action.recovery.RecoveryPolicyTest' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G2: Gather no longer runs a hidden nested high-level BaseAction; crafting/smelting emit exact missing item/quantity/station/fuel observations; delivery emits verifiable evidence.
  EVIDENCE: pending

- [ ] G3: Correct tool tier, broken-tool replacement, loaded chunks, protected containers/blocks, and BUILDING permission for torch placement fail closed.
  EVIDENCE: pending

- [ ] G4: Search is bounded, non-teleporting, target-position aware, records memory, and hands coordinates back for later mining.
  EVIDENCE: pending

- [ ] G5: Action/runtime-focused tests pass.
  CHECK: ./gradlew test --tests 'com.steve.ai.action.*' --tests 'com.steve.ai.crafting.*' --tests 'com.steve.ai.inventory.*' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending
