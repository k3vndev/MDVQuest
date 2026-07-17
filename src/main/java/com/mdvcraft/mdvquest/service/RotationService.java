package com.mdvcraft.mdvquest.service;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.MissionDefinition;
import com.mdvcraft.mdvquest.model.MissionInstance;
import com.mdvcraft.mdvquest.model.RotationDefinition;
import com.mdvcraft.mdvquest.storage.QuestDatabase;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public final class RotationService {
    private final MDVQuestPlugin plugin;
    private final QuestRegistry registry;
    private final QuestDatabase database;
    private final Map<String, MissionInstance> active = new LinkedHashMap<>();
    private final Map<String, String> currentCycleByRotation = new HashMap<>();
    private final Set<String> emptyCycles = new java.util.HashSet<>();

    public RotationService(MDVQuestPlugin plugin, QuestRegistry registry, QuestDatabase database) {
        this.plugin = plugin;
        this.registry = registry;
        this.database = database;
    }

    public synchronized void initialize() throws SQLException {
        active.clear();
        currentCycleByRotation.clear();
        emptyCycles.clear();
        long now = System.currentTimeMillis();
        for (QuestDatabase.StoredInstance stored : database.loadUnexpiredInstances(now)) {
            MissionDefinition definition = registry.mission(stored.definitionId());
            if (definition == null) {
                plugin.getLogger().warning("Instancia activa ignorada porque su definicion ya no existe: " + stored.id());
                continue;
            }
            MissionInstance instance = new MissionInstance(stored.id(), stored.cycleKey(), stored.rotationId(), definition, stored.startsAt(), stored.expiresAt());
            if (instance.isActive(now)) active.put(instance.id(), instance);
        }
        refresh(now);
    }

    public synchronized boolean refresh(long now) throws SQLException {
        boolean changed = active.values().removeIf(instance -> !instance.isActive(now));
        currentCycleByRotation.clear();

        for (RotationDefinition rotation : registry.rotations()) {
            if (!rotation.enabled() || rotation.missionCount() <= 0) continue;
            Window window = window(rotation, now);
            currentCycleByRotation.put(rotation.id(), window.cycleKey());

            boolean hasInMemory = active.values().stream().anyMatch(i -> i.cycleKey().equals(window.cycleKey()));
            if (hasInMemory || emptyCycles.contains(window.cycleKey())) continue;

            List<MissionInstance> storedForCycle = loadCycleFromDatabase(window.cycleKey(), now);
            if (!storedForCycle.isEmpty()) {
                for (MissionInstance instance : storedForCycle) active.put(instance.id(), instance);
                changed = true;
                continue;
            }

            List<MissionInstance> generated = generate(rotation, window, "");
            if (generated.isEmpty()) {
                emptyCycles.add(window.cycleKey());
                continue;
            }
            database.insertInstances(generated);
            for (MissionInstance instance : generated) active.put(instance.id(), instance);
            if (!generated.isEmpty()) {
                changed = true;
                plugin.getLogger().info("Rotacion " + rotation.id() + " generada con " + generated.size() + " misiones globales.");
            }
        }

        if (changed) sortActive();
        return changed;
    }

    private List<MissionInstance> loadCycleFromDatabase(String cycleKey, long now) throws SQLException {
        List<MissionInstance> result = new ArrayList<>();
        for (QuestDatabase.StoredInstance stored : database.loadUnexpiredInstances(now)) {
            if (!stored.cycleKey().equals(cycleKey)) continue;
            MissionDefinition definition = registry.mission(stored.definitionId());
            if (definition == null) continue;
            result.add(new MissionInstance(stored.id(), stored.cycleKey(), stored.rotationId(), definition, stored.startsAt(), stored.expiresAt()));
        }
        return result;
    }

    public synchronized boolean forceRotate(String rotationId, long now) throws SQLException {
        RotationDefinition rotation = registry.rotation(rotationId);
        if (rotation == null || !rotation.enabled()) return false;
        Window base = window(rotation, now);
        String previousCycle = currentCycleByRotation.getOrDefault(rotation.id(), base.cycleKey());
        database.deleteCycle(previousCycle);
        active.values().removeIf(i -> i.rotationId().equals(rotation.id()));
        emptyCycles.remove(previousCycle);

        String suffix = "forced-" + now;
        Window forced = new Window(base.startsAt(), base.expiresAt(), base.cycleKey());
        List<MissionInstance> generated = generate(rotation, forced, suffix);
        database.insertInstances(generated);
        for (MissionInstance instance : generated) active.put(instance.id(), instance);
        currentCycleByRotation.put(rotation.id(), forced.cycleKey());
        sortActive();
        return true;
    }

    private List<MissionInstance> generate(RotationDefinition rotation, Window window, String extraSeed) {
        List<MissionDefinition> candidates = new ArrayList<>(registry.missionsForRotation(rotation.id()));
        if (candidates.isEmpty()) {
            plugin.getLogger().warning("No hay misiones habilitadas para la rotacion '" + rotation.id() + "'.");
            return Collections.emptyList();
        }

        int count = Math.min(rotation.missionCount(), candidates.size());
        Random random = new Random(stableSeed(rotation.seed() + ":" + window.cycleKey() + ":" + extraSeed));
        List<MissionDefinition> selected = new ArrayList<>();
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            int totalWeight = candidates.stream().mapToInt(MissionDefinition::weight).sum();
            int roll = random.nextInt(Math.max(1, totalWeight));
            MissionDefinition picked = candidates.get(candidates.size() - 1);
            int cursor = 0;
            for (MissionDefinition candidate : candidates) {
                cursor += candidate.weight();
                if (roll < cursor) { picked = candidate; break; }
            }
            selected.add(picked);
            candidates.remove(picked);
        }

        List<MissionInstance> instances = new ArrayList<>();
        for (MissionDefinition definition : selected) {
            String id = window.cycleKey() + ":" + definition.id();
            instances.add(new MissionInstance(id, window.cycleKey(), rotation.id(), definition, window.startsAt(), window.expiresAt()));
        }
        return instances;
    }

    private long stableSeed(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash).getLong();
        } catch (Exception ignored) {
            return value.hashCode();
        }
    }

    public Window window(RotationDefinition rotation, long nowMillis) {
        ZonedDateTime now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), registry.zoneId());
        LocalDate effectiveDate = now.toLocalTime().isBefore(rotation.resetTime()) ? now.toLocalDate().minusDays(1) : now.toLocalDate();
        long days = ChronoUnit.DAYS.between(rotation.anchorDate(), effectiveDate);
        long cycle = Math.floorDiv(days, rotation.durationDays());
        LocalDate startDate = rotation.anchorDate().plusDays(cycle * rotation.durationDays());
        ZonedDateTime starts = ZonedDateTime.of(startDate, rotation.resetTime(), registry.zoneId());
        ZonedDateTime expires = starts.plusDays(rotation.durationDays());
        long startsAt = starts.toInstant().toEpochMilli();
        long expiresAt = expires.toInstant().toEpochMilli();
        String cycleKey = rotation.id() + "-" + startsAt;
        return new Window(startsAt, expiresAt, cycleKey);
    }

    private void sortActive() {
        List<MissionInstance> sorted = active.values().stream()
                .sorted(Comparator.comparingLong(MissionInstance::expiresAt).thenComparing(i -> i.definition().id()))
                .toList();
        active.clear();
        for (MissionInstance instance : sorted) active.put(instance.id(), instance);
    }

    public synchronized List<MissionInstance> activeInstances() {
        return List.copyOf(active.values());
    }

    public synchronized MissionInstance instance(String id) {
        return active.get(id);
    }

    public synchronized Set<String> activeInstanceIds() {
        return Set.copyOf(active.keySet());
    }

    public synchronized Collection<String> currentCycles() {
        return List.copyOf(currentCycleByRotation.values());
    }

    public record Window(long startsAt, long expiresAt, String cycleKey) { }
}
