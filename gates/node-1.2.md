# Gates: safe runtime integration

Scope: Integrate structured action evidence, deterministic recovery, prerequisites, and mutation guards.

- [ ] G1: Leaf 1.2.1 checks pass and every registered action still has one descriptor/schema/permission/factory.
  CHECK: ./gradlew test --tests 'com.steve.ai.action.*' --tests 'com.steve.ai.plugin.*' --tests 'com.steve.ai.security.*' --tests 'com.steve.ai.crafting.*' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G2: No action introduces scripts, commands, reflection, or resource-search teleportation.
  EVIDENCE: pending
