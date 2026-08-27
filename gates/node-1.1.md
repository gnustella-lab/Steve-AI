# Gates: durable domain integration

Scope: Integrate goal, plan, queue, and memory persistence as one restart-safe domain.

- [ ] G1: Leaf 1.1.1 checks pass with no duplicate progress authority.
  CHECK: ./gradlew test --tests 'com.steve.ai.autonomy.*' --tests 'com.steve.ai.planning.*' --tests 'com.steve.ai.memory.*' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G2: Controller-facing APIs compile without persisting or resuming BaseAction.
  EVIDENCE: pending
