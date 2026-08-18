package com.steve.ai.autonomy;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded loop detector for failed strategies. */
public final class FailureTracker {
    private static final int MAX_FINGERPRINTS = 64;
    private final int maxRepeated;
    private final LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> descriptions = new LinkedHashMap<>();

    public FailureTracker(int maxRepeated) {
        this.maxRepeated = Math.max(1, Math.min(maxRepeated, 32));
    }

    public int record(AgentGoal goal, Task task, ActionResult result, BlockPos position) {
        FailureFingerprint fingerprint = FailureFingerprint.from(goal, task, result, position);
        return record(fingerprint.compact(), result == null ? "failure" : result.getMessage());
    }

    public int record(String fingerprint, String description) {
        String key = fingerprint == null ? "unknown" : bounded(fingerprint, 1_024);
        int count = counts.merge(key, 1, Integer::sum);
        descriptions.put(key, bounded(description, 256));
        while (counts.size() > MAX_FINGERPRINTS) {
            String oldest = counts.keySet().iterator().next();
            counts.remove(oldest);
            descriptions.remove(oldest);
        }
        return count;
    }

    public int count(AgentGoal goal, Task task, ActionResult result, BlockPos position) {
        return counts.getOrDefault(FailureFingerprint.from(goal, task, result, position).compact(), 0);
    }

    public boolean repeated(AgentGoal goal, Task task, ActionResult result, BlockPos position) {
        return count(goal, task, result, position) > maxRepeated;
    }

    public List<String> failedApproaches() {
        List<String> result = new ArrayList<>();
        counts.forEach((key, count) -> result.add("x" + count + " " + descriptions.getOrDefault(key, key)));
        return Collections.unmodifiableList(result);
    }

    public Map<String, Integer> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    public void clear() {
        counts.clear();
        descriptions.clear();
    }

    private static String bounded(String value, int max) {
        String normalized = value == null ? "" : value;
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
