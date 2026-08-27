# Gates: executive and Forge integration

Scope: Integrate all leaves into the entity tick, persistence, commands, status, and real Forge scenarios.

- [ ] G1: Leaf 1.4.1 checks and the full JUnit suite pass after all interfaces are integrated.
  CHECK: ./gradlew test --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G2: Forge reports every required test passed on the final source snapshot.
  CHECK: ./gradlew runGameTestServer --stacktrace
  EXPECT: All /[0-9]+/ required tests passed
  EVIDENCE: pending

- [ ] G3: No production or resource edit occurs after the final JUnit/GameTest/build evidence without rerunning affected gates.
  EVIDENCE: pending
