package com.steve.ai.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeExecutionEngineTest {
    @Test
    void rejectsScriptsWhileNoBoundedSandboxIsAvailable() {
        CodeExecutionEngine engine = new CodeExecutionEngine(null);

        CodeExecutionEngine.ExecutionResult result = engine.execute("while (true) {}", 1);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("disabled"));
        assertFalse(engine.validateSyntax("1 + 1"));
    }
}
