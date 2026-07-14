package com.steve.ai.execution;

import com.steve.ai.entity.SteveEntity;

/**
 * Compatibility facade for the former JavaScript execution subsystem.
 * Script execution is intentionally disabled because the old implementation did not enforce
 * its advertised timeout and therefore could freeze the server indefinitely.
 */
public class CodeExecutionEngine {
    private final SteveAPI steveAPI;

    private static final long DEFAULT_TIMEOUT_MS = 30_000;
    private static final String DISABLED_MESSAGE =
        "JavaScript execution is disabled until a bounded sandbox is available";

    public CodeExecutionEngine(SteveEntity steve) {
        this.steveAPI = new SteveAPI(steve);
    }

    /**
     * Execute JavaScript code with default timeout
     */
    public ExecutionResult execute(String code) {
        return execute(code, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Execute JavaScript code with custom timeout
     *
     * @param code JavaScript code to execute
     * @param timeoutMs Maximum execution time in milliseconds
     * @return ExecutionResult containing success/failure status and output
     */
    public ExecutionResult execute(String code, long timeoutMs) {
        if (code == null || code.trim().isEmpty()) {
            return ExecutionResult.error("No code provided");
        }
        return ExecutionResult.error(DISABLED_MESSAGE);
    }

    /**
     * Validate JavaScript code syntax without executing
     *
     * @param code JavaScript code to validate
     * @return true if syntax is valid, false otherwise
     */
    public boolean validateSyntax(String code) {
        return false;
    }

    /**
     * Get the Steve API bridge
     */
    public SteveAPI getAPI() {
        return steveAPI;
    }

    /**
     * Clean up resources
     */
    public void close() {
        // No resources are allocated while execution is disabled.
    }

    /**
     * Result of code execution
     */
    public static class ExecutionResult {
        private final boolean success;
        private final String output;
        private final String error;

        private ExecutionResult(boolean success, String output, String error) {
            this.success = success;
            this.output = output;
            this.error = error;
        }

        public static ExecutionResult success(String output) {
            return new ExecutionResult(true, output, null);
        }

        public static ExecutionResult error(String error) {
            return new ExecutionResult(false, null, error);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getOutput() {
            return output;
        }

        public String getError() {
            return error;
        }

        @Override
        public String toString() {
            if (success) {
                return "Success: " + output;
            } else {
                return "Error: " + error;
            }
        }
    }
}
