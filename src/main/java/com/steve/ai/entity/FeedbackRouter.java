package com.steve.ai.entity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Decides who receives operational Steve feedback. Nearby unrelated players are never chosen.
 * Broadcast is only used when the configured scope is explicitly {@code BROADCAST}.
 */
public final class FeedbackRouter {
    private FeedbackRouter() {
    }

    public enum Scope {
        CONTROLLER,
        OWNER,
        AUTHORIZED,
        BROADCAST;

        public static Scope parse(String value) {
            if (value == null || value.isBlank()) {
                return CONTROLLER;
            }
            try {
                return Scope.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return CONTROLLER;
            }
        }
    }

    public enum Delivery {
        PLAYERS,
        BROADCAST,
        LOG
    }

    public record Decision(Delivery delivery, List<UUID> recipients) {
        public Decision {
            recipients = recipients == null ? List.of() : List.copyOf(recipients);
        }

        public static Decision log() {
            return new Decision(Delivery.LOG, List.of());
        }

        public static Decision broadcast() {
            return new Decision(Delivery.BROADCAST, List.of());
        }

        public static Decision players(List<UUID> recipients) {
            return recipients == null || recipients.isEmpty()
                ? log()
                : new Decision(Delivery.PLAYERS, recipients);
        }
    }

    public static Decision decide(Scope scope, UUID controller, boolean controllerOnline,
            UUID owner, boolean ownerOnline, Set<UUID> authorizedOnline) {
        Scope resolved = scope == null ? Scope.CONTROLLER : scope;
        Set<UUID> authorized = authorizedOnline == null ? Set.of() : authorizedOnline;
        return switch (resolved) {
            case BROADCAST -> Decision.broadcast();
            case AUTHORIZED -> Decision.players(allAuthorized(controller, controllerOnline,
                owner, ownerOnline, authorized));
            case OWNER -> firstAvailable(owner, ownerOnline, controller, controllerOnline, authorized);
            case CONTROLLER -> firstAvailable(controller, controllerOnline, owner, ownerOnline, authorized);
        };
    }

    private static Decision firstAvailable(UUID primary, boolean primaryOnline,
            UUID fallback, boolean fallbackOnline, Set<UUID> authorizedOnline) {
        if (primary != null && primaryOnline) {
            return Decision.players(List.of(primary));
        }
        if (fallback != null && fallbackOnline) {
            return Decision.players(List.of(fallback));
        }
        if (!authorizedOnline.isEmpty()) {
            return Decision.players(new ArrayList<>(authorizedOnline));
        }
        return Decision.log();
    }

    private static List<UUID> allAuthorized(UUID controller, boolean controllerOnline,
            UUID owner, boolean ownerOnline, Set<UUID> authorizedOnline) {
        LinkedHashSet<UUID> recipients = new LinkedHashSet<>();
        if (controller != null && controllerOnline) {
            recipients.add(controller);
        }
        if (owner != null && ownerOnline) {
            recipients.add(owner);
        }
        recipients.addAll(authorizedOnline);
        return new ArrayList<>(recipients);
    }
}
