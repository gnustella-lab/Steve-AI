package com.steve.ai.entity;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackRouterTest {
    private final UUID controller = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID authorized = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @Test
    void controllerScopePrefersControllerThenOwnerThenAuthorizedAndNeverAStranger() {
        FeedbackRouter.Decision primary = FeedbackRouter.decide(
            FeedbackRouter.Scope.CONTROLLER, controller, true, owner, true, Set.of(authorized));
        assertEquals(FeedbackRouter.Delivery.PLAYERS, primary.delivery());
        assertEquals(controller, primary.recipients().get(0));

        FeedbackRouter.Decision ownerFallback = FeedbackRouter.decide(
            FeedbackRouter.Scope.CONTROLLER, controller, false, owner, true, Set.of(authorized));
        assertEquals(owner, ownerFallback.recipients().get(0));

        FeedbackRouter.Decision authorizedFallback = FeedbackRouter.decide(
            FeedbackRouter.Scope.CONTROLLER, controller, false, owner, false, Set.of(authorized));
        assertEquals(Set.of(authorized), Set.copyOf(authorizedFallback.recipients()));

        FeedbackRouter.Decision consoleIssued = FeedbackRouter.decide(
            FeedbackRouter.Scope.CONTROLLER, null, false, null, false, Set.of());
        assertEquals(FeedbackRouter.Delivery.LOG, consoleIssued.delivery());
        assertTrue(consoleIssued.recipients().isEmpty());
        assertTrue(!consoleIssued.recipients().contains(stranger));
    }

    @Test
    void ownerScopeDoesNotBroadcastAndAuthorizedFanOutStaysExplicit() {
        FeedbackRouter.Decision ownerFirst = FeedbackRouter.decide(
            FeedbackRouter.Scope.OWNER, controller, true, owner, true, Set.of(authorized));
        assertEquals(owner, ownerFirst.recipients().get(0));

        FeedbackRouter.Decision authorized = FeedbackRouter.decide(
            FeedbackRouter.Scope.AUTHORIZED, controller, true, owner, true, Set.of(this.authorized));
        assertEquals(FeedbackRouter.Delivery.PLAYERS, authorized.delivery());
        assertEquals(3, authorized.recipients().size());

        FeedbackRouter.Decision broadcast = FeedbackRouter.decide(
            FeedbackRouter.Scope.BROADCAST, controller, true, owner, true, Set.of());
        assertEquals(FeedbackRouter.Delivery.BROADCAST, broadcast.delivery());
    }

    @Test
    void unknownConfiguredScopeFailsClosedToController() {
        assertEquals(FeedbackRouter.Scope.CONTROLLER, FeedbackRouter.Scope.parse("nearby"));
        assertEquals(FeedbackRouter.Scope.CONTROLLER, FeedbackRouter.Scope.parse(""));
        assertEquals(FeedbackRouter.Scope.BROADCAST, FeedbackRouter.Scope.parse("broadcast"));
    }
}
