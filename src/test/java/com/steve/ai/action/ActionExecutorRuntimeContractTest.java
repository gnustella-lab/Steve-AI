package com.steve.ai.action;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionExecutorRuntimeContractTest {

    @Test
    void compatibilitySyncEntryPointDelegatesToNonBlockingPlanning() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/steve/ai/action/ActionExecutor.java"));
        int start = source.indexOf("public void processNaturalLanguageCommandSync");
        int end = source.indexOf("    /**", start + 10);
        String method = source.substring(start, end < 0 ? source.length() : end);

        assertTrue(method.contains("processNaturalLanguageCommand(command"));
        assertFalse(method.contains("planTasks(steve, command)"));
    }

    @Test
    void runtimeSourceDoesNotOwnRecoveryPolicyDecisions() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/steve/ai/action/ActionExecutor.java"));

        assertFalse(source.contains("recoveryPolicy.decide"));
        assertFalse(source.contains("getRecoveryPolicy"));
        assertFalse(source.contains("MAX_REPLANS_PER_PLAN"));
    }
}
