package com.steve.ai.security;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteveAccessProfileTest {

    @Test
    void authorizesOwnerExplicitSharesAndAdministrators() {
        UUID owner = UUID.randomUUID();
        UUID sharedPlayer = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        SteveAccessProfile profile = new SteveAccessProfile();
        profile.transferOwnership(owner);
        profile.authorize(sharedPlayer);

        assertTrue(profile.canControl(owner, false));
        assertTrue(profile.canControl(sharedPlayer, false));
        assertTrue(profile.canControl(stranger, true));
        assertFalse(profile.canControl(stranger, false));

        profile.revoke(sharedPlayer);
        assertFalse(profile.canControl(sharedPlayer, false));
    }

    @Test
    void legacyProfileWithoutOwnerFailsClosedForPlayers() {
        SteveAccessProfile profile = new SteveAccessProfile();

        assertNull(profile.getOwnerUuid());
        assertFalse(profile.canControl(UUID.randomUUID(), false));
        assertTrue(profile.canControl(UUID.randomUUID(), true));
    }

    @Test
    void roundTripNbtPreservesUuidBasedAccessAndMetadata() {
        UUID owner = UUID.randomUUID();
        UUID sharedPlayer = UUID.randomUUID();
        SteveAccessProfile original = new SteveAccessProfile();
        original.transferOwnership(owner);
        original.authorize(sharedPlayer);
        original.setTeam("builders");
        original.setPermissionProfile("survival-safe");
        original.grantCapability(AgentCapability.ALLOW_FLIGHT);

        CompoundTag tag = original.save();
        SteveAccessProfile restored = new SteveAccessProfile();
        restored.load(tag);

        assertEquals(owner, restored.getOwnerUuid());
        assertTrue(restored.getAuthorizedPlayers().contains(sharedPlayer));
        assertEquals("builders", restored.getTeam());
        assertEquals("survival-safe", restored.getPermissionProfile());
        assertTrue(restored.hasCapability(AgentCapability.ALLOW_FLIGHT));
        assertFalse(restored.hasCapability(AgentCapability.ALLOW_TELEPORT));
    }

    @Test
    void unknownPersistedCapabilityFailsClosed() {
        CompoundTag tag = new CompoundTag();
        net.minecraft.nbt.ListTag capabilities = new net.minecraft.nbt.ListTag();
        capabilities.add(net.minecraft.nbt.StringTag.valueOf("LLM_SUPERPOWER"));
        tag.put("Capabilities", capabilities);

        SteveAccessProfile profile = new SteveAccessProfile();
        profile.load(tag);

        assertTrue(profile.getCapabilities().isEmpty());
    }
}
