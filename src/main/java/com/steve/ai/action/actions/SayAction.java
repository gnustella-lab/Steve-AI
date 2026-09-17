package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;

/**
 * Player-facing speech. Must be the only task in a planning horizon.
 * Plain text only — Minecraft chat does not render markdown.
 */
public class SayAction extends BaseAction {
    static final int MAX_TEXT_LENGTH = 160;

    public SayAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        String spoken = sanitize(task.getStringParameter("text"));
        if (spoken.isEmpty()) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Missing say text")
                .retryable(false)
                .build();
            return;
        }
        if (SteveConfig.ENABLE_CHAT_RESPONSES.get()) {
            steve.sendFeedback(spoken);
        }
        result = ActionResult.success("Spoke to the player")
            .observation("actionType", "say")
            .observation("spoken", spoken)
            .build();
    }

    @Override
    protected void onTick() {
    }

    @Override
    protected void onCancel() {
    }

    @Override
    public String getDescription() {
        String text = sanitize(task.getStringParameter("text"));
        return text.isEmpty() ? "Say" : "Say: " + text;
    }

    static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(raw.length());
        boolean pendingSpace = false;
        for (int index = 0; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if ((current == '§' || current == '&')
                    && index + 1 < raw.length()
                    && isLegacyColorCode(raw.charAt(index + 1))) {
                index++;
                continue;
            }
            if (current == '§') {
                continue;
            }
            if (current <= 0x1F || current == 0x7F) {
                pendingSpace = cleaned.length() > 0;
                continue;
            }
            if (Character.isWhitespace(current)) {
                pendingSpace = cleaned.length() > 0;
                continue;
            }
            if (pendingSpace) {
                cleaned.append(' ');
                pendingSpace = false;
            }
            cleaned.append(current);
            if (cleaned.length() >= MAX_TEXT_LENGTH) {
                break;
            }
        }
        return cleaned.toString();
    }

    private static boolean isLegacyColorCode(char code) {
        return (code >= '0' && code <= '9')
            || (code >= 'a' && code <= 'f')
            || (code >= 'A' && code <= 'F')
            || "klmnorKLMNOR".indexOf(code) >= 0;
    }
}
