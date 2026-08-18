package com.steve.ai.llm;

import java.util.concurrent.CompletableFuture;

/** Small injection seam for deterministic autonomous-loop tests. */
@FunctionalInterface
public interface AutonomyPlanner {
    CompletableFuture<ResponseParser.ParsedResponse> plan(PlanningContext context);
}
