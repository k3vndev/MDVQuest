package com.mdvcraft.mdvquest.service;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.AccessTier;
import com.mdvcraft.mdvquest.model.MissionCountRange;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Genera y conserva las selecciones globales de cada rotación y pool. */
public final class RotationService {
    private final MDVQuestPlugin plugin;
    private final QuestRegistry registry;
    private final QuestDatabase database;
    private final Map<String, MissionInstance> active = new LinkedHashMap<>();
    private final Map<String, String> currentCycleByRotation = new HashMap<>();
    private final Set<String> emptyPools = new HashSet<>();

    public RotationService(MDVQuestPlugin plugin, QuestRegistry registry, QuestDatabase database) {
        this.plugin = plugin;
        this.registry = registry;
        this.database = database;
    }

    public synchronized void initialize() throws SQLException {
        active.clear();
        currentCycleByRotation.clear();
        emptyPools.clear();
        long now = System.currentTimeMillis();
        // Purga primero toda la información del ciclo vencido. De esta forma una
        // aceptación expirada, su progreso y una recompensa ya reclamada no pueden
        // sobrevivir a un reinicio del servidor ni mezclarse con el siguiente roll.
        int cleaned = database.cleanupExpired(now);
        if (cleaned > 0) {
            plugin.getLogger().info("Se purgaron " + cleaned + " instancia(s) expirada(s) antes de cargar las rotaciones.");
        }
        for (QuestDatabase.StoredInstance stored : database.loadUnexpiredInstances(now)) {
            MissionDefinition definition = registry.mission(stored.definitionId());
            if (definition == null) {
                plugin.getLogger().warning("Instancia activa ignorada porque su definicion ya no existe: " + stored.id());
                continue;
            }
            MissionInstance instance = new MissionInstance(
                    stored.id(), stored.cycleKey(), stored.rotationId(), stored.accessTier(), definition,
                    stored.startsAt(), stored.expiresAt()
            );
            if (instance.isActive(now)) active.put(instance.id(), instance);
        }
        refresh(now);
    }

    public synchronized boolean refresh(long now) throws SQLException {
        boolean changed = active.values().removeIf(instance -> !instance.isActive(now));
        currentCycleByRotation.clear();

        for (RotationDefinition rotation : registry.rotations()) {
            if (!rotation.enabled() || !rotation.hasAnyPoolEnabled()) continue;
            Window window = window(rotation, now);
            currentCycleByRotation.put(rotation.id(), window.cycleKey());

            Set<String> selectedDefinitions = new HashSet<>();
            for (MissionInstance instance : active.values()) {
                if (instance.cycleKey().equals(window.cycleKey())) selectedDefinitions.add(instance.definition().id());
            }

            for (AccessTier tier : AccessTier.values()) {
                MissionCountRange range = rotation.countRange(tier);
                if (!range.enabled()) continue;
                String poolKey = poolKey(window.cycleKey(), tier);
                boolean alreadyGenerated = active.values().stream().anyMatch(instance ->
                        instance.cycleKey().equals(window.cycleKey()) && instance.accessTier() == tier);
                if (alreadyGenerated || emptyPools.contains(poolKey)) continue;

                List<MissionInstance> generated = generatePool(rotation, window, tier, selectedDefinitions, "");
                if (generated.isEmpty()) {
                    emptyPools.add(poolKey);
                    continue;
                }
                database.insertInstances(generated);
                for (MissionInstance instance : generated) {
                    active.put(instance.id(), instance);
                    selectedDefinitions.add(instance.definition().id());
                }
                changed = true;
                plugin.getLogger().info("Rotacion " + rotation.id() + " / " + tier.key()
                        + " generada con " + generated.size() + " misiones globales.");
            }
        }

        if (changed) sortActive();
        return changed;
    }

    public synchronized boolean forceRotate(String rotationId, long now) throws SQLException {
        RotationDefinition rotation = registry.rotation(rotationId);
        if (rotation == null || !rotation.enabled()) return false;
        Window base = window(rotation, now);
        String previousCycle = currentCycleByRotation.getOrDefault(rotation.id(), base.cycleKey());
        database.deleteCycle(previousCycle);
        active.values().removeIf(instance -> instance.rotationId().equals(rotation.id()));
        emptyPools.removeIf(key -> key.startsWith(previousCycle + "|"));

        String extraSeed = "reroll-" + now;
        Set<String> selectedDefinitions = new HashSet<>();
        List<MissionInstance> allGenerated = new ArrayList<>();
        for (AccessTier tier : AccessTier.values()) {
            if (!rotation.countRange(tier).enabled()) continue;
            List<MissionInstance> generated = generatePool(rotation, base, tier, selectedDefinitions, extraSeed);
            if (generated.isEmpty()) emptyPools.add(poolKey(base.cycleKey(), tier));
            for (MissionInstance instance : generated) selectedDefinitions.add(instance.definition().id());
            allGenerated.addAll(generated);
        }

        database.insertInstances(allGenerated);
        for (MissionInstance instance : allGenerated) active.put(instance.id(), instance);
        currentCycleByRotation.put(rotation.id(), base.cycleKey());
        sortActive();
        return true;
    }

    /**
     * Inserta una definición concreta en su ciclo global actual sin regenerar la rotación
     * ni borrar progreso existente. También admite definiciones deshabilitadas para pruebas.
     */
    public synchronized ForceResult forceMission(String definitionId, long now) throws SQLException {
        MissionDefinition definition = registry.mission(definitionId);
        if (definition == null) return new ForceResult(ForceStatus.NOT_FOUND, null);

        RotationDefinition rotation = registry.rotation(definition.rotation());
        if (rotation == null) return new ForceResult(ForceStatus.ROTATION_NOT_FOUND, null);
        if (!rotation.enabled()) return new ForceResult(ForceStatus.ROTATION_DISABLED, null);

        Window window = window(rotation, now);
        currentCycleByRotation.put(rotation.id(), window.cycleKey());

        for (MissionInstance instance : active.values()) {
            if (instance.cycleKey().equals(window.cycleKey())
                    && instance.definition().id().equals(definition.id())) {
                return new ForceResult(ForceStatus.ALREADY_ACTIVE, instance);
            }
        }

        AccessTier tier = definition.accessTier();
        String id = window.cycleKey() + ":" + tier.key() + ":" + definition.id();
        MissionInstance instance = new MissionInstance(
                id, window.cycleKey(), rotation.id(), tier, definition,
                window.startsAt(), window.expiresAt()
        );
        database.insertInstances(List.of(instance));
        active.put(instance.id(), instance);
        emptyPools.remove(poolKey(window.cycleKey(), tier));
        sortActive();
        plugin.getLogger().info("Mision forzada globalmente: " + definition.id()
                + " [" + rotation.id() + "/" + tier.key() + "]");
        return new ForceResult(ForceStatus.ADDED, instance);
    }

    private List<MissionInstance> generatePool(RotationDefinition rotation, Window window, AccessTier generatedTier,
                                               Set<String> excludedDefinitions, String extraSeed) {
        Set<AccessTier> allowedDefinitionPools = switch (generatedTier) {
            case NORMAL -> EnumSet.of(AccessTier.NORMAL);
            case VIP1 -> EnumSet.of(AccessTier.NORMAL, AccessTier.VIP1);
            case VIP2 -> EnumSet.of(AccessTier.VIP1, AccessTier.VIP2);
        };

        List<MissionDefinition> candidates = new ArrayList<>(
                registry.missionsForRotation(rotation.id(), allowedDefinitionPools).stream()
                        .filter(mission -> !excludedDefinitions.contains(mission.id()))
                        .toList()
        );
        if (candidates.isEmpty()) {
            plugin.getLogger().warning("No hay candidatas libres para la rotacion '" + rotation.id()
                    + "' en el pool '" + generatedTier.key() + "'.");
            return List.of();
        }

        MissionCountRange range = rotation.countRange(generatedTier);
        Random random = new Random(stableSeed(rotation.seed() + ":" + window.cycleKey() + ":"
                + generatedTier.key() + ":" + extraSeed));
        int requested = range.min();
        if (range.max() > range.min()) requested += random.nextInt(range.max() - range.min() + 1);
        int count = Math.min(requested, candidates.size());
        if (count < range.min()) {
            plugin.getLogger().warning("El pool '" + generatedTier.key() + "' de '" + rotation.id()
                    + "' pidio al menos " + range.min() + " misiones, pero solo hay " + candidates.size() + " candidatas libres.");
        }

        List<MissionDefinition> selected = new ArrayList<>();
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            int totalWeight = candidates.stream().mapToInt(MissionDefinition::weight).sum();
            int roll = random.nextInt(Math.max(1, totalWeight));
            MissionDefinition picked = candidates.get(candidates.size() - 1);
            int cursor = 0;
            for (MissionDefinition candidate : candidates) {
                cursor += candidate.weight();
                if (roll < cursor) {
                    picked = candidate;
                    break;
                }
            }
            selected.add(picked);
            candidates.remove(picked);
        }

        List<MissionInstance> instances = new ArrayList<>();
        String generationSuffix = extraSeed == null || extraSeed.isBlank() ? "" : ":" + extraSeed;
        for (MissionDefinition definition : selected) {
            // Un reroll dentro del mismo ciclo necesita IDs nuevos. De lo contrario,
            // una definición sorteada nuevamente reutiliza el ID anterior y una caché
            // todavía cargada puede interpretarla como el contrato viejo.
            String id = window.cycleKey() + generationSuffix + ":" + generatedTier.key() + ":" + definition.id();
            instances.add(new MissionInstance(id, window.cycleKey(), rotation.id(), generatedTier,
                    definition, window.startsAt(), window.expiresAt()));
        }
        return instances;
    }

    private String poolKey(String cycleKey, AccessTier tier) {
        return cycleKey + "|" + tier.key();
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
        LocalDate effectiveDate = now.toLocalTime().isBefore(rotation.resetTime())
                ? now.toLocalDate().minusDays(1) : now.toLocalDate();
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
                .sorted(Comparator.comparingLong(MissionInstance::expiresAt)
                        .thenComparingInt(instance -> instance.accessTier().level())
                        .thenComparing(instance -> instance.definition().id()))
                .toList();
        active.clear();
        for (MissionInstance instance : sorted) active.put(instance.id(), instance);
    }

    public synchronized List<MissionInstance> activeInstances() {
        long now = System.currentTimeMillis();
        return active.values().stream().filter(instance -> instance.isActive(now)).toList();
    }

    public synchronized MissionInstance instance(String id) {
        MissionInstance instance = active.get(id);
        return instance != null && instance.isActive(System.currentTimeMillis()) ? instance : null;
    }

    public synchronized Set<String> activeInstanceIds() {
        long now = System.currentTimeMillis();
        return active.values().stream()
                .filter(instance -> instance.isActive(now))
                .map(MissionInstance::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** IDs conocidos de una rotación, incluidos los que acaban de vencer y aún no fueron refrescados. */
    public synchronized Set<String> instanceIdsForRotation(String rotationId) {
        return active.values().stream()
                .filter(instance -> instance.rotationId().equals(rotationId))
                .map(MissionInstance::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Todos los IDs todavía presentes en memoria antes de una regeneración global. */
    public synchronized Set<String> knownInstanceIds() {
        return Set.copyOf(active.keySet());
    }

    public synchronized Collection<String> currentCycles() {
        return List.copyOf(currentCycleByRotation.values());
    }

    public enum ForceStatus {
        ADDED,
        ALREADY_ACTIVE,
        NOT_FOUND,
        ROTATION_NOT_FOUND,
        ROTATION_DISABLED,
        DATABASE_ERROR
    }

    public record ForceResult(ForceStatus status, MissionInstance instance) { }

    public record Window(long startsAt, long expiresAt, String cycleKey) { }
}
