package com.mdvcraft.mdvquest.service;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.MissionInstance;
import com.mdvcraft.mdvquest.model.ObjectiveDefinition;
import com.mdvcraft.mdvquest.model.ObjectiveKey;
import com.mdvcraft.mdvquest.model.ObjectiveType;
import com.mdvcraft.mdvquest.model.PlayerQuestState;
import com.mdvcraft.mdvquest.storage.QuestDatabase;
import com.mdvcraft.mdvquest.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Iterator;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressService {
    private final MDVQuestPlugin plugin;
    private final QuestRegistry registry;
    private final RotationService rotations;
    private final QuestDatabase database;
    private final Map<UUID, PlayerQuestState> cache = new ConcurrentHashMap<>();
    private final Map<ObjectiveType, List<ObjectiveRef>> index = new EnumMap<>(ObjectiveType.class);
    private final Map<UUID, Long> lastActionbar = new HashMap<>();
    private final Map<UUID, Long> recentMythicKills = new HashMap<>();

    public ProgressService(MDVQuestPlugin plugin, QuestRegistry registry, RotationService rotations, QuestDatabase database) {
        this.plugin = plugin;
        this.registry = registry;
        this.rotations = rotations;
        this.database = database;
        rebuildIndex();
    }

    public synchronized void rebuildIndex() {
        index.clear();
        for (ObjectiveType type : ObjectiveType.values()) index.put(type, new ArrayList<>());
        for (MissionInstance instance : rotations.activeInstances()) {
            for (ObjectiveDefinition objective : instance.definition().objectives()) {
                index.get(objective.type()).add(new ObjectiveRef(instance, objective));
            }
        }
        Set<String> activeIds = rotations.activeInstanceIds();
        for (PlayerQuestState state : cache.values()) {
            state.progress().keySet().removeIf(key -> !activeIds.contains(key.instanceId()));
            state.claimedInstances().removeIf(id -> !activeIds.contains(id));
            state.dirty().removeIf(key -> !activeIds.contains(key.instanceId()));
        }
    }

    public PlayerQuestState state(Player player) {
        return state(player.getUniqueId());
    }

    public PlayerQuestState state(UUID playerId) {
        return cache.computeIfAbsent(playerId, uuid -> {
            try {
                return database.loadPlayer(uuid, rotations.activeInstanceIds());
            } catch (SQLException ex) {
                plugin.getLogger().severe("No se pudo cargar progreso de " + uuid + ": " + ex.getMessage());
                return new PlayerQuestState(uuid);
            }
        });
    }

    public void preload(Player player) {
        state(player);
    }

    public void unload(Player player) {
        PlayerQuestState state = cache.get(player.getUniqueId());
        if (state != null) flush(state);
        if (plugin.getConfig().getBoolean("performance.unload-player-cache-on-quit", true)) {
            cache.remove(player.getUniqueId());
            lastActionbar.remove(player.getUniqueId());
        }
    }

    public int report(Player player, ObjectiveType type, String target, long amount) {
        return report(player, type, target, amount, Collections.emptyMap());
    }

    public int report(Player player, ObjectiveType type, String target, long amount, Map<String, String> data) {
        if (player == null || type == null || amount <= 0) return 0;
        List<ObjectiveRef> refs = index.getOrDefault(type, Collections.emptyList());
        if (refs.isEmpty()) return 0;
        PlayerQuestState state = state(player);
        int changed = 0;
        long now = System.currentTimeMillis();

        for (ObjectiveRef ref : refs) {
            if (!ref.instance().isActive(now) || state.claimed(ref.instance().id())) continue;
            if (!matches(player, ref.objective(), target, data)) continue;
            long increment = calculateIncrement(ref.objective(), amount, data);
            if (increment <= 0) continue;
            if (increment(player, state, ref, increment, true)) changed++;
        }
        return changed;
    }

    public boolean incrementSpecific(Player player, MissionInstance instance, ObjectiveDefinition objective, long amount) {
        if (player == null || instance == null || objective == null || amount <= 0 || !instance.isActive(System.currentTimeMillis())) return false;
        return increment(player, state(player), new ObjectiveRef(instance, objective), amount, true);
    }

    private boolean increment(Player player, PlayerQuestState state, ObjectiveRef ref, long increment, boolean notify) {
        ObjectiveKey key = key(ref);
        long before = Math.min(ref.objective().amount(), state.progress(key));
        if (before >= ref.objective().amount()) return false;
        boolean missionBefore = isMissionComplete(state, ref.instance());
        long after = Math.min(ref.objective().amount(), before + increment);
        state.setProgress(key, after, true);

        boolean objectiveCompleted = before < ref.objective().amount() && after >= ref.objective().amount();
        boolean missionCompleted = !missionBefore && isMissionComplete(state, ref.instance());

        if (notify) {
            if (objectiveCompleted) {
                plugin.message(player, "objective-completed", Map.of("objective", ref.objective().displayName()));
            } else {
                sendProgressActionbar(player, ref.objective(), after);
            }
            if (missionCompleted && plugin.getAccessService().hasAccess(player, ref.instance().accessTier())) {
                plugin.message(player, "mission-completed", Map.of("mission", ref.instance().definition().name()));
                plugin.getSocialHook().sound(player, "confirm");
            }
        }
        if (objectiveCompleted || missionCompleted) flush(state);
        return true;
    }

    private boolean matches(Player player, ObjectiveDefinition objective, String rawTarget, Map<String, String> data) {
        String target = normalize(rawTarget);
        List<String> worlds = objective.strings("worlds");
        if (!worlds.isEmpty() && !worlds.contains(normalize(player.getWorld().getName()))) return false;

        return switch (objective.type()) {
            case MINE_BLOCK, CUT_LOG -> {
                boolean natural = Boolean.parseBoolean(data.getOrDefault("natural", "true"));
                if (objective.bool("natural-only", false) && !natural) yield false;
                yield objective.targetMatches(target);
            }
            case HARVEST_CROP -> {
                boolean natural = Boolean.parseBoolean(data.getOrDefault("natural", "true"));
                boolean mature = Boolean.parseBoolean(data.getOrDefault("mature", "false"));
                if (objective.bool("natural-only", false) && !natural) yield false;
                if (objective.bool("mature-only", true) && !mature) yield false;
                yield objective.targetMatches(target);
            }
            case KILL_VANILLA_MOB, KILL_MYTHIC_MOB, CRAFT_VANILLA_ITEM -> objective.targetMatches(target);
            case KILL_ANY_HOSTILE_MOB -> true;
            case BREAK_CUSTOM_ORE -> {
                String requiredKind = normalize(objective.string("resource-kind", ""));
                String actualKind = normalize(data.getOrDefault("resource-kind", ""));
                yield (requiredKind.isBlank() || requiredKind.equals(actualKind)) && objective.targetMatches(target);
            }
            case KILL_MOB_FAMILY -> {
                String family = objective.string("family", "");
                boolean regular = !family.isBlank() && registry.familyContains(family, target);
                boolean miniboss = !family.isBlank() && objective.bool("include-minibosses", true)
                        && registry.familyMinibossContains(family, target);
                yield regular || miniboss;
            }
            case KILL_MINIBOSS -> {
                String family = objective.string("family", "");
                if (!family.isBlank()) yield registry.familyMinibossContains(family, target);
                List<String> targets = objective.strings("targets");
                yield targets.isEmpty() ? registry.isAnyMiniboss(target) : objective.targetMatches(target);
            }
            case CRAFT_RECIPE -> matchesSingleOrTargets(objective, "recipe", target);
            case CRAFT_CATEGORY -> matchesSingleOrTargets(objective, "category", target);
            case OBTAIN_MMOITEM -> sourceAllowed(objective, data) && matchesMmoItem(objective, data, target);
            case DELIVER_MMOITEM -> matchesMmoItem(objective, data, target);
            case USE_CONSUMABLE -> {
                boolean mmoTarget = data.containsKey("mmo-type") || target.contains(":")
                        || !objective.string("mmoitems-id", objective.string("item-id", "")).isBlank()
                        || !objective.string("mmoitems-type", objective.string("type-id", "")).isBlank();
                yield mmoTarget ? matchesMmoItem(objective, data, target)
                        : matchesSingleOrTargets(objective, "material", target);
            }
            case DELIVER_VANILLA_ITEM -> matchesSingleOrTargets(objective, "material", target);
            case EARN_PROFESSION_EXP -> matchesSingleOrTargets(objective, "profession", target);
            case COMPLETE_EVENT -> matchesSingleOrTargets(objective, "event", target);
            case PLAYER_KILL -> true;
            case CLAN_KILL -> false;
        };
    }

    private boolean matchesSingleOrTargets(ObjectiveDefinition objective, String key, String target) {
        String configured = normalize(objective.string(key, ""));
        if (!configured.isBlank()) return configured.equals(target);
        return objective.targetMatches(target);
    }

    private boolean sourceAllowed(ObjectiveDefinition objective, Map<String, String> data) {
        List<String> sources = objective.strings("sources");
        if (sources.isEmpty()) return true;
        return sources.contains(normalize(data.getOrDefault("source", "UNKNOWN")));
    }

    private boolean matchesMmoItem(ObjectiveDefinition objective, Map<String, String> data, String target) {
        String type = normalize(data.getOrDefault("mmo-type", ""));
        String id = normalize(data.getOrDefault("mmo-id", ""));
        if ((type.isBlank() || id.isBlank()) && target.contains(":")) {
            String[] split = target.split(":", 2);
            type = split[0]; id = split[1];
        }
        String configuredType = normalize(objective.string("mmoitems-type", objective.string("type-id", "")));
        String configuredId = normalize(objective.string("mmoitems-id", objective.string("item-id", "")));
        if (!configuredType.isBlank() && !configuredType.equals(type)) return false;
        if (!configuredId.isBlank() && !configuredId.equals(id)) return false;
        if (configuredType.isBlank() && configuredId.isBlank()) return objective.targetMatches(type + ":" + id);
        return !id.isBlank();
    }

    private long calculateIncrement(ObjectiveDefinition objective, long defaultAmount, Map<String, String> data) {
        if (objective.type() == ObjectiveType.CRAFT_RECIPE || objective.type() == ObjectiveType.CRAFT_CATEGORY || objective.type() == ObjectiveType.CRAFT_VANILLA_ITEM) {
            if (!objective.bool("count-produced-items", true)) {
                try { return Math.max(1L, Long.parseLong(data.getOrDefault("craft-operations", "1"))); }
                catch (NumberFormatException ignored) { return 1L; }
            }
        }
        return defaultAmount;
    }



    public int reportMythicKill(Player killer, UUID entityId, String mythicId) {
        if (killer == null || entityId == null || mythicId == null || mythicId.isBlank()) return 0;
        long now = System.currentTimeMillis();
        Long previous = recentMythicKills.putIfAbsent(entityId, now);
        if (previous != null && now - previous < 10_000L) return 0;
        recentMythicKills.put(entityId, now);
        if (recentMythicKills.size() > 512) {
            Iterator<Map.Entry<UUID, Long>> iterator = recentMythicKills.entrySet().iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().getValue() > 15_000L) iterator.remove();
            }
        }
        int changed = 0;
        changed += report(killer, ObjectiveType.KILL_MYTHIC_MOB, mythicId, 1L);
        changed += report(killer, ObjectiveType.KILL_MOB_FAMILY, mythicId, 1L);
        changed += report(killer, ObjectiveType.KILL_MINIBOSS, mythicId, 1L);
        changed += report(killer, ObjectiveType.KILL_ANY_HOSTILE_MOB, mythicId, 1L);
        return changed;
    }

    public int reportPlayerKill(Player killer, Player victim) {
        if (killer == null || victim == null || killer.getUniqueId().equals(victim.getUniqueId())) return 0;
        List<ObjectiveRef> refs = index.getOrDefault(ObjectiveType.PLAYER_KILL, Collections.emptyList());
        if (refs.isEmpty()) return 0;

        List<String> allowedWorlds = plugin.getConfig().getStringList("anti-exploit.pvp.allowed-worlds");
        if (!allowedWorlds.isEmpty() && allowedWorlds.stream().noneMatch(w -> w.equalsIgnoreCase(killer.getWorld().getName()))) return 0;

        if (plugin.getConfig().getBoolean("anti-exploit.pvp.deny-same-ip", true)
                && killer.getAddress() != null && victim.getAddress() != null
                && killer.getAddress().getAddress() != null && victim.getAddress().getAddress() != null
                && killer.getAddress().getAddress().equals(victim.getAddress().getAddress())) return 0;

        long minimumMinutes = Math.max(0L, plugin.getConfig().getLong("anti-exploit.pvp.minimum-victim-playtime-minutes", 30));
        try {
            long playedTicks = victim.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
            if (playedTicks < minimumMinutes * 60L * 20L) return 0;
        } catch (Throwable ignored) { }

        String clanPlaceholder = plugin.getConfig().getString("anti-exploit.pvp.clan-id-placeholder", "");
        if (clanPlaceholder != null && !clanPlaceholder.isBlank()) {
            String killerClan = plugin.getPlaceholderHook().apply(killer, clanPlaceholder).trim();
            String victimClan = plugin.getPlaceholderHook().apply(victim, clanPlaceholder).trim();
            if (!killerClan.isBlank() && !killerClan.equalsIgnoreCase("none") && killerClan.equalsIgnoreCase(victimClan)) return 0;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(0L, plugin.getConfig().getLong("anti-exploit.pvp.victim-repeat-cooldown-hours", 24)) * 3_600_000L;
        if (cooldownMillis > 0) {
            try {
                long last = database.lastVictimCount(killer.getUniqueId(), victim.getUniqueId());
                if (last > 0 && now - last < cooldownMillis) return 0;
            } catch (SQLException ex) {
                plugin.getLogger().warning("No se pudo validar cooldown PvP: " + ex.getMessage());
                return 0;
            }
        }

        PlayerQuestState state = state(killer);
        int changed = 0;
        for (ObjectiveRef ref : refs) {
            if (!ref.instance().isActive(now) || state.claimed(ref.instance().id())) continue;
            if (!matches(killer, ref.objective(), victim.getUniqueId().toString(), Collections.emptyMap())) continue;
            if (state.progress(key(ref)) >= ref.objective().amount()) continue;
            boolean unique = ref.objective().bool("unique-victims", plugin.getConfig().getBoolean("anti-exploit.pvp.unique-victims-default", true));
            if (unique) {
                try {
                    if (!database.registerUniqueVictim(killer.getUniqueId(), ref.instance().id(), ref.objective().id(), victim.getUniqueId(), now)) continue;
                } catch (SQLException ex) {
                    plugin.getLogger().warning("No se pudo registrar victima PvP: " + ex.getMessage());
                    continue;
                }
            }
            if (increment(killer, state, ref, 1L, true)) changed++;
        }
        if (changed > 0) {
            try { database.recordVictimKill(killer.getUniqueId(), victim.getUniqueId(), now); }
            catch (SQLException ex) { plugin.getLogger().warning("No se pudo guardar historial PvP: " + ex.getMessage()); }
        }
        return changed;
    }

    public boolean isMissionComplete(Player player, MissionInstance instance) {
        return isMissionComplete(state(player), instance);
    }

    public boolean isMissionComplete(PlayerQuestState state, MissionInstance instance) {
        for (ObjectiveDefinition objective : instance.definition().objectives()) {
            if (state.progress(new ObjectiveKey(instance.id(), objective.id())) < objective.amount()) return false;
        }
        return true;
    }

    public long progress(Player player, MissionInstance instance, ObjectiveDefinition objective) {
        return state(player).progress(new ObjectiveKey(instance.id(), objective.id()));
    }

    public boolean claimed(Player player, MissionInstance instance) {
        return state(player).claimed(instance.id());
    }

    public void markClaimed(Player player, MissionInstance instance) {
        state(player).claimedInstances().add(instance.id());
    }

    public void flushAll() {
        flushMany(new ArrayList<>(cache.values()), "todos los jugadores");
    }

    public void flushOnline() {
        List<PlayerQuestState> states = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerQuestState state = cache.get(player.getUniqueId());
            if (state != null) states.add(state);
        }
        flushMany(states, "jugadores conectados");
    }

    public void flush(Player player) {
        if (player == null) return;
        PlayerQuestState state = cache.get(player.getUniqueId());
        if (state != null) flush(state);
    }

    private void flush(PlayerQuestState state) {
        try {
            database.flushPlayer(state);
        } catch (SQLException ex) {
            plugin.getLogger().severe("No se pudo guardar progreso de " + state.playerId() + ": " + ex.getMessage());
        }
    }

    private void flushMany(List<PlayerQuestState> states, String context) {
        try {
            database.flushPlayers(states);
        } catch (SQLException ex) {
            plugin.getLogger().severe("No se pudo guardar progreso de " + context + ": " + ex.getMessage());
        }
    }

    private ObjectiveKey key(ObjectiveRef ref) {
        return new ObjectiveKey(ref.instance().id(), ref.objective().id());
    }

    private void sendProgressActionbar(Player player, ObjectiveDefinition objective, long progress) {
        if (!plugin.getConfig().getBoolean("performance.progress-actionbar", true)) return;
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, plugin.getConfig().getLong("performance.progress-actionbar-cooldown-ms", 900));
        if (now - lastActionbar.getOrDefault(player.getUniqueId(), 0L) < cooldown) return;
        lastActionbar.put(player.getUniqueId(), now);
        String text = ColorUtil.color("&e" + objective.displayName() + " &f" + progress + "&7/&f" + objective.amount());
        try {
            Class<?> serializerClass = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Object serializer = serializerClass.getMethod("legacySection").invoke(null);
            Object component = serializerClass.getMethod("deserialize", String.class).invoke(serializer, text);
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            player.getClass().getMethod("sendActionBar", componentClass).invoke(player, component);
            return;
        } catch (Throwable ignored) { }
        try {
            Method method = player.getClass().getMethod("sendActionBar", String.class);
            method.invoke(player, text);
        } catch (Throwable ignored) { }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    public record ObjectiveRef(MissionInstance instance, ObjectiveDefinition objective) { }
}
