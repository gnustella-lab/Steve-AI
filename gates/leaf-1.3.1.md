# Gates: bounded perception and strict LLM protocol

Scope: Improve relevant immutable observations and enforce a safe, cache-correct, fallback-aware operational planning boundary.

- [ ] G1: Observation exposes compact positioned resources/stations/containers/hazards/navigation/inventory data and stays within explicit scan and prompt bounds.
  CHECK: ./gradlew test --tests 'com.steve.ai.perception.*' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G2: Root goal and active subgoal are distinct stable prompt sections; relevant memory uses token/dimension/time/proximity selection rather than whole-string matching.
  EVIDENCE: pending

- [ ] G3: Parser rejects prose-wrapped JSON, ACT without tasks, inconsistent decisions, unknown fields/actions/parameters, gigantic horizons, and malformed primitive values.
  CHECK: ./gradlew test --tests 'com.steve.ai.llm.ResponseParserTest' --tests 'com.steve.ai.llm.AutonomousResponseSchemaTest' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G4: Fallback emits the current schema without reasoning; configured provider fallback and dynamic cache-key tests pass; invalid/provider failures back off.
  CHECK: ./gradlew test --tests 'com.steve.ai.llm.resilience.*' --tests 'com.steve.ai.llm.PromptBuilderTest' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending
