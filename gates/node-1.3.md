# Gates: perception and LLM integration

Scope: Integrate bounded snapshots, relevant memory, strict decisions, cache correctness, fallback, and backoff.

- [ ] G1: Leaf 1.3.1 checks pass with stable root/subgoal sections and bounded context.
  CHECK: ./gradlew test --tests 'com.steve.ai.perception.*' --tests 'com.steve.ai.llm.*' --rerun-tasks
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: pending

- [ ] G2: Operational fallback output contains no private reasoning field and passes the same parser/schema as provider output.
  EVIDENCE: pending
