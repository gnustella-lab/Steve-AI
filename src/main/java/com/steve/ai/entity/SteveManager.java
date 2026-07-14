package com.steve.ai.entity;

import com.steve.ai.SteveMod;
import com.steve.ai.config.SteveConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SteveManager {
    private final Map<String, SteveEntity> activeSteves;
    private final Map<UUID, SteveEntity> stevesByUUID;

    public SteveManager() {
        this.activeSteves = new ConcurrentHashMap<>();
        this.stevesByUUID = new ConcurrentHashMap<>();
    }

    public SteveEntity spawnSteve(ServerLevel level, Vec3 position, String name) {
        SteveMod.LOGGER.info("Current active Steves: {}", activeSteves.size());

        if (name == null || !name.matches("[A-Za-z0-9_-]{1,32}")) {
            SteveMod.LOGGER.warn("Invalid Steve name: {}", name);
            return null;
        }

        String normalizedName = normalizeName(name);
        if (activeSteves.containsKey(normalizedName)) {
            SteveMod.LOGGER.warn("Steve name '{}' already exists", name);
            return null;
        }

        int maxSteves = SteveConfig.MAX_ACTIVE_STEVES.get();
        if (activeSteves.size() >= maxSteves) {
            SteveMod.LOGGER.warn("Max Steve limit reached: {}", maxSteves);
            return null;
        }

        try {
            SteveEntity steve = new SteveEntity(SteveMod.STEVE_ENTITY.get(), level);
            steve.setSteveName(name.trim());
            steve.setPos(position.x, position.y, position.z);

            if (!level.addFreshEntity(steve)) {
                SteveMod.LOGGER.error("Failed to add Steve entity to world");
                return null;
            }

            registerSteve(steve);
            SteveMod.LOGGER.info("Successfully spawned Steve: {} with UUID {} at {}",
                steve.getSteveName(), steve.getUUID(), position);
            return steve;
        } catch (Throwable error) {
            SteveMod.LOGGER.error("Failed to create or spawn Steve entity", error);
            return null;
        }
    }

    public SteveEntity getSteve(String name) {
        return name == null ? null : activeSteves.get(normalizeName(name));
    }

    public SteveEntity getSteve(UUID uuid) {
        return stevesByUUID.get(uuid);
    }

    public boolean removeSteve(String name) {
        SteveEntity steve = name == null ? null : activeSteves.remove(normalizeName(name));
        if (steve != null) {
            stevesByUUID.remove(steve.getUUID());
            steve.discard();
            return true;
        }
        return false;
    }

    public void clearAllSteves() {
        SteveMod.LOGGER.info("Clearing {} Steve entities", activeSteves.size());
        for (SteveEntity steve : activeSteves.values()) {
            steve.discard();
        }
        activeSteves.clear();
        stevesByUUID.clear();
    }

    /** Limpa apenas os índices em memória, preservando entidades salvas no mundo. */
    public void clearTracking() {
        activeSteves.clear();
        stevesByUUID.clear();
    }

    public Collection<SteveEntity> getAllSteves() {
        return Collections.unmodifiableCollection(activeSteves.values());
    }

    public List<String> getSteveNames() {
        return activeSteves.values().stream()
            .map(SteveEntity::getSteveName)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    /** Registra entidades carregadas de chunks ou adicionadas por outro fluxo. */
    public void registerSteve(SteveEntity steve) {
        if (steve == null || steve.level().isClientSide()) {
            return;
        }

        String originalName = steve.getSteveName();
        String sanitizedName = sanitizeName(originalName);
        if (!sanitizedName.equals(originalName)) {
            steve.setSteveName(sanitizedName);
            SteveMod.LOGGER.warn("Sanitized invalid loaded Steve name '{}' to '{}'", originalName, sanitizedName);
            originalName = sanitizedName;
        }
        String candidateName = originalName;
        int suffix = 2;
        while (true) {
            String normalizedName = normalizeName(candidateName);
            SteveEntity existing = activeSteves.putIfAbsent(normalizedName, steve);
            if (existing == null || existing == steve) {
                break;
            }

            String suffixText = "_" + suffix++;
            int baseLength = Math.max(1, 32 - suffixText.length());
            String baseName = originalName.substring(0, Math.min(originalName.length(), baseLength));
            candidateName = baseName + suffixText;
        }

        if (!candidateName.equals(originalName)) {
            steve.setSteveName(candidateName);
            SteveMod.LOGGER.warn("Renamed duplicate loaded Steve '{}' to '{}' so it remains manageable",
                originalName, candidateName);
        }
        stevesByUUID.put(steve.getUUID(), steve);
    }

    /** Remove somente a instância informada. */
    public void unregisterSteve(SteveEntity steve) {
        if (steve == null) {
            return;
        }
        activeSteves.remove(normalizeName(steve.getSteveName()), steve);
        stevesByUUID.remove(steve.getUUID(), steve);
    }

    public int getActiveCount() {
        return activeSteves.size();
    }

    public void tick(ServerLevel level) {
        // Clean up dead or removed Steves
        Iterator<Map.Entry<String, SteveEntity>> iterator = activeSteves.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SteveEntity> entry = iterator.next();
            SteveEntity steve = entry.getValue();
            
            if (!steve.isAlive() || steve.isRemoved()) {
                iterator.remove();
                stevesByUUID.remove(steve.getUUID());
                SteveMod.LOGGER.info("Cleaned up Steve: {}", entry.getKey());
            }
        }
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String sanitizeName(String name) {
        String sanitized = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        if (sanitized.isEmpty()) {
            sanitized = "Steve";
        }
        return sanitized.substring(0, Math.min(sanitized.length(), 32));
    }
}

