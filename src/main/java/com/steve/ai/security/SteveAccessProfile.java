package com.steve.ai.security;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** UUID-based ownership and sharing metadata for one Steve. */
public final class SteveAccessProfile {
    public static final int DATA_VERSION = 1;
    private static final int MAX_METADATA_LENGTH = 64;

    private UUID ownerUuid;
    private final Set<UUID> authorizedPlayers = new HashSet<>();
    private String team = "";
    private String permissionProfile = "default";

    /** Returns the owner, or {@code null} for a legacy Steve that has not been claimed. */
    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    /** Transfers ownership without implicitly retaining access for the former owner. */
    public void transferOwnership(UUID newOwnerUuid) {
        if (newOwnerUuid == null) {
            throw new IllegalArgumentException("Owner UUID cannot be null");
        }
        ownerUuid = newOwnerUuid;
        authorizedPlayers.remove(newOwnerUuid);
    }

    /** Grants explicit access to a player UUID. */
    public void authorize(UUID playerUuid) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("Player UUID cannot be null");
        }
        if (!playerUuid.equals(ownerUuid)) {
            authorizedPlayers.add(playerUuid);
        }
    }

    /** Revokes explicit access. Ownership is not affected. */
    public void revoke(UUID playerUuid) {
        if (playerUuid != null) {
            authorizedPlayers.remove(playerUuid);
        }
    }

    /** Administrators always retain recovery access; all other access is UUID based. */
    public boolean canControl(UUID playerUuid, boolean administrator) {
        if (administrator) {
            return true;
        }
        return playerUuid != null
            && (playerUuid.equals(ownerUuid) || authorizedPlayers.contains(playerUuid));
    }

    public Set<UUID> getAuthorizedPlayers() {
        return Collections.unmodifiableSet(new HashSet<>(authorizedPlayers));
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = normalizeMetadata(team, "team");
    }

    public String getPermissionProfile() {
        return permissionProfile;
    }

    public void setPermissionProfile(String permissionProfile) {
        this.permissionProfile = normalizeMetadata(permissionProfile, "permission profile");
    }

    /** Serializes access metadata without names or mutable player references. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", DATA_VERSION);
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        ListTag authorized = new ListTag();
        authorizedPlayers.stream()
            .map(UUID::toString)
            .sorted()
            .map(StringTag::valueOf)
            .forEach(authorized::add);
        tag.put("AuthorizedPlayers", authorized);
        tag.putString("Team", team);
        tag.putString("PermissionProfile", permissionProfile);
        return tag;
    }

    /** Restores a complete snapshot, clearing any previously loaded ACL entries first. */
    public void load(CompoundTag tag) {
        ownerUuid = null;
        authorizedPlayers.clear();
        team = "";
        permissionProfile = "default";
        if (tag == null || tag.isEmpty()) {
            return;
        }

        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        ListTag authorized = tag.getList("AuthorizedPlayers", Tag.TAG_STRING);
        for (int index = 0; index < authorized.size(); index++) {
            try {
                UUID playerUuid = UUID.fromString(authorized.getString(index));
                if (!playerUuid.equals(ownerUuid)) {
                    authorizedPlayers.add(playerUuid);
                }
            } catch (IllegalArgumentException ignored) {
                // Dados inválidos antigos são ignorados sem ampliar acesso.
            }
        }
        if (tag.contains("Team", Tag.TAG_STRING)) {
            team = normalizeLoadedMetadata(tag.getString("Team"), "");
        }
        if (tag.contains("PermissionProfile", Tag.TAG_STRING)) {
            permissionProfile = normalizeLoadedMetadata(tag.getString("PermissionProfile"), "default");
        }
    }

    private static String normalizeMetadata(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > MAX_METADATA_LENGTH) {
            throw new IllegalArgumentException(field + " cannot exceed " + MAX_METADATA_LENGTH + " characters");
        }
        return normalized;
    }

    private static String normalizeLoadedMetadata(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= MAX_METADATA_LENGTH ? normalized : fallback;
    }
}
