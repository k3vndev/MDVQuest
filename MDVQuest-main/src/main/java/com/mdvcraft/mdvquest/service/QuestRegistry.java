package com.mdvcraft.mdvquest.service;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.AccessTier;
import com.mdvcraft.mdvquest.model.MissionCountRange;
import com.mdvcraft.mdvquest.model.MissionDefinition;
import com.mdvcraft.mdvquest.model.ObjectiveDefinition;
import com.mdvcraft.mdvquest.model.ObjectiveType;
import com.mdvcraft.mdvquest.model.RewardDefinition;
import com.mdvcraft.mdvquest.model.RotationDefinition;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class QuestRegistry {
    private final MDVQuestPlugin plugin;
    /** Incluye misiones habilitadas y deshabilitadas para el catálogo administrativo. */
    private final Map<String, MissionDefinition> missions = new LinkedHashMap<>();
    private final Map<String, RotationDefinition> rotations = new LinkedHashMap<>();
    private final Map<String, Set<String>> familyMobs = new HashMap<>();
    private final Map<String, Set<String>> familyMinibosses = new HashMap<>();
    private final Set<Material> naturalTrackedMaterials = new HashSet<>();
    private ZoneId zoneId = ZoneId.of("America/Argentina/Cordoba");

    public QuestRegistry(MDVQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public int reload() {
        missions.clear();
        rotations.clear();
        familyMobs.clear();
        familyMinibosses.clear();
        naturalTrackedMaterials.clear();

        try {
            zoneId = ZoneId.of(plugin.getConfig().getString("time-zone", "America/Argentina/Cordoba"));
        } catch (Exception ex) {
            plugin.getLogger().warning("Zona horaria invalida; usando America/Argentina/Cordoba.");
            zoneId = ZoneId.of("America/Argentina/Cordoba");
        }

        loadRotations();
        loadFamilies();
        loadMissions();
        return missions.size();
    }

    private void loadRotations() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("rotations");
        if (root == null) return;
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) continue;
            String id = normalizeRotation(rawId);
            try {
                LocalDate anchor = LocalDate.parse(section.getString("anchor-date", "2026-01-01"));
                LocalTime time = LocalTime.parse(section.getString("reset-time", "00:00"));
                EnumMap<AccessTier, MissionCountRange> counts = new EnumMap<>(AccessTier.class);
                ConfigurationSection pools = section.getConfigurationSection("pools");
                if (pools == null) {
                    int legacy = Math.max(0, section.getInt("mission-count", 1));
                    counts.put(AccessTier.NORMAL, new MissionCountRange(legacy, legacy));
                    counts.put(AccessTier.VIP1, new MissionCountRange(0, 0));
                    counts.put(AccessTier.VIP2, new MissionCountRange(0, 0));
                } else {
                    for (AccessTier tier : AccessTier.values()) {
                        ConfigurationSection pool = pools.getConfigurationSection(tier.key());
                        int min = pool == null ? 0 : Math.max(0, pool.getInt("min-missions", pool.getInt("min", 0)));
                        int max = pool == null ? 0 : Math.max(min, pool.getInt("max-missions", pool.getInt("max", min)));
                        counts.put(tier, new MissionCountRange(min, max));
                    }
                }
                rotations.put(id, new RotationDefinition(
                        id,
                        section.getBoolean("enabled", true),
                        section.getInt("duration-days", 1),
                        counts,
                        anchor,
                        time,
                        section.getString("seed", "mdvquest-" + id)
                ));
            } catch (DateTimeParseException ex) {
                plugin.getLogger().warning("Rotacion '" + rawId + "' tiene anchor-date/reset-time invalido: " + ex.getMessage());
            }
        }
    }

    private void loadFamilies() {
        File file = new File(plugin.getDataFolder(), "families.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("families");
        if (root == null) return;
        for (String rawFamily : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawFamily);
            if (section == null) continue;
            String family = normalizeTarget(rawFamily);
            familyMobs.put(family, normalizeSet(section.getStringList("mobs")));
            familyMinibosses.put(family, normalizeSet(section.getStringList("minibosses")));
        }
    }

    private void loadMissions() {
        File folder = new File(plugin.getDataFolder(), "missions");
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        List<File> ordered = new ArrayList<>(List.of(files));
        ordered.sort(Comparator.comparing(File::getName));

        for (File file : ordered) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = yaml.getConfigurationSection("missions");
            if (root == null) continue;
            for (String rawId : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(rawId);
                if (section == null) continue;
                try {
                    MissionDefinition mission = parseMission(rawId, section, file.getName());
                    if (!rotations.containsKey(mission.rotation())) {
                        throw new IllegalArgumentException("rotation desconocida: " + mission.rotation());
                    }
                    MissionDefinition previous = missions.put(mission.id(), mission);
                    if (previous != null) plugin.getLogger().warning("Mision duplicada reemplazada: " + mission.id());
                    if (mission.enabled()) collectNaturalMaterials(mission);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "No se pudo cargar la mision '" + rawId + "' de " + file.getName() + ": " + ex.getMessage());
                }
            }
        }
    }

    private MissionDefinition parseMission(String id, ConfigurationSection section, String sourceFile) {
        boolean enabled = section.getBoolean("enabled", true);
        String rotation = normalizeRotation(section.getString("rotation", "daily"));
        AccessTier accessTier = AccessTier.parse(section.getString("access-pool", section.getString("access-tier", "normal")));
        int weight = Math.max(1, section.getInt("weight", 1));
        String name = section.getString("name", id);
        String icon = section.getString("icon", "PAPER");
        ItemStack iconItem = section.getItemStack("icon-item");
        List<String> lore = section.getStringList("lore");

        ConfigurationSection objectivesSection = section.getConfigurationSection("objectives");
        if (objectivesSection == null) throw new IllegalArgumentException("falta objectives");
        List<ObjectiveDefinition> objectives = new ArrayList<>();
        for (String objectiveId : objectivesSection.getKeys(false)) {
            ConfigurationSection objective = objectivesSection.getConfigurationSection(objectiveId);
            if (objective == null) continue;
            ObjectiveType type = ObjectiveType.parse(objective.getString("type"));
            if (type == ObjectiveType.CLAN_KILL) {
                throw new IllegalArgumentException("CLAN_KILL queda reservado para MDVQuest V2");
            }
            long amount = Math.max(1L, objective.getLong("amount", 1L));
            String display = objective.getString("name", objectiveId);
            Map<String, Object> options = deepValues(objective);
            objectives.add(new ObjectiveDefinition(objectiveId, type, amount, display, options));
        }

        if (objectives.isEmpty()) throw new IllegalArgumentException("la mision no contiene objetivos");
        RewardDefinition rewards = parseRewards(section.getConfigurationSection("rewards"));
        return new MissionDefinition(id, enabled, rotation, accessTier, weight, name, icon, iconItem, lore, objectives, rewards, sourceFile);
    }

    private RewardDefinition parseRewards(ConfigurationSection section) {
        if (section == null) return new RewardDefinition(null, null, null, null);
        List<String> displayLore = section.getStringList("lore");
        List<String> commands = section.getStringList("commands");

        List<RewardDefinition.VanillaItemReward> vanilla = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("vanilla-items")) {
            Object materialValue = map.get("material");
            String material = materialValue == null ? "AIR" : String.valueOf(materialValue);
            int amount = parseInt(map.get("amount"), 1);
            vanilla.add(new RewardDefinition.VanillaItemReward(material, Math.max(1, amount)));
        }

        List<RewardDefinition.MmoItemReward> mmo = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("mmoitems")) {
            Object typeValue = map.get("type");
            Object idValue = map.get("id");
            String type = typeValue == null ? "MATERIAL" : String.valueOf(typeValue);
            String itemId = idValue == null ? "" : String.valueOf(idValue);
            int amount = parseInt(map.get("amount"), 1);
            if (!itemId.isBlank()) mmo.add(new RewardDefinition.MmoItemReward(type, itemId, Math.max(1, amount)));
        }

        List<RewardDefinition.MythicItemReward> mythic = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("mythic-items")) {
            Object idValue = map.get("id");
            String itemId = idValue == null ? "" : String.valueOf(idValue);
            int amount = parseInt(map.get("amount"), 1);
            if (!itemId.isBlank()) mythic.add(new RewardDefinition.MythicItemReward(itemId, Math.max(1, amount)));
        }

        List<RewardDefinition.ExactItemReward> exact = new ArrayList<>();
        List<?> exactList = section.getList("exact-items", Collections.emptyList());
        for (Object raw : exactList) {
            if (raw instanceof ItemStack item && !item.getType().isAir()) {
                exact.add(new RewardDefinition.ExactItemReward(item, Math.max(1, item.getAmount())));
            } else if (raw instanceof Map<?, ?> map) {
                try {
                    @SuppressWarnings("unchecked")
                    ItemStack item = ItemStack.deserialize((Map<String, Object>) map);
                    if (item != null && !item.getType().isAir()) exact.add(new RewardDefinition.ExactItemReward(item, item.getAmount()));
                } catch (Exception ignored) { }
            }
        }

        List<RewardDefinition.ExperienceReward> experience = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("experience")) {
            Object professionValue = map.get("profession");
            String profession = professionValue == null ? "main" : String.valueOf(professionValue);
            long amount = parseLong(map.get("amount"), 1L);
            experience.add(new RewardDefinition.ExperienceReward(profession, amount));
        }

        return new RewardDefinition(displayLore, commands, vanilla, mmo, mythic, exact, experience);
    }

    private Map<String, Object> deepValues(ConfigurationSection section) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) values.put(key, deepValues(child));
            else values.put(key, value);
        }
        return values;
    }

    private void collectNaturalMaterials(MissionDefinition mission) {
        for (ObjectiveDefinition objective : mission.objectives()) {
            if (!objective.bool("natural-only", false)) continue;
            List<String> targets = objective.strings("targets");
            if (targets.isEmpty() && objective.type() == ObjectiveType.CUT_LOG) {
                for (Material material : Material.values()) {
                    if (material.isBlock() && Tag.LOGS.isTagged(material)) naturalTrackedMaterials.add(material);
                }
                continue;
            }
            if (targets.isEmpty() && objective.type() == ObjectiveType.MINE_BLOCK) {
                plugin.getLogger().warning("La mision '" + mission.id() + "' usa MINE_BLOCK natural-only sin targets; agrega materiales para mantener el registro economico.");
                continue;
            }
            for (String raw : targets) {
                Material material = Material.matchMaterial(raw);
                if (material != null && material.isBlock()) naturalTrackedMaterials.add(material);
            }
        }
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (Exception ignored) { return fallback; }
    }

    private long parseLong(Object value, long fallback) {
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(value)); }
        catch (Exception ignored) { return fallback; }
    }

    private Set<String> normalizeSet(List<String> values) {
        Set<String> set = new HashSet<>();
        for (String value : values) set.add(normalizeTarget(value));
        return set;
    }

    public ZoneId zoneId() { return zoneId; }
    public Collection<MissionDefinition> missions() { return Collections.unmodifiableCollection(missions.values()); }
    public MissionDefinition mission(String id) { return missions.get(normalizeMission(id)); }
    public Collection<RotationDefinition> rotations() { return Collections.unmodifiableCollection(rotations.values()); }
    public RotationDefinition rotation(String id) { return rotations.get(normalizeRotation(id)); }
    public Set<Material> naturalTrackedMaterials() { return Collections.unmodifiableSet(naturalTrackedMaterials); }

    public List<MissionDefinition> missionsForRotation(String rotationId) {
        String normalized = normalizeRotation(rotationId);
        return missions.values().stream().filter(MissionDefinition::enabled).filter(m -> m.rotation().equals(normalized)).toList();
    }

    public List<MissionDefinition> missionsForRotation(String rotationId, Set<AccessTier> allowedDefinitionPools) {
        String normalized = normalizeRotation(rotationId);
        return missions.values().stream()
                .filter(MissionDefinition::enabled)
                .filter(mission -> mission.rotation().equals(normalized))
                .filter(mission -> allowedDefinitionPools.contains(mission.accessTier()))
                .toList();
    }

    public int durationDays(MissionDefinition mission) {
        RotationDefinition rotation = rotation(mission.rotation());
        return rotation == null ? 1 : rotation.durationDays();
    }

    public boolean familyContains(String family, String mythicId) {
        Set<String> mobs = familyMobs.get(normalizeTarget(family));
        return mobs != null && mobs.contains(normalizeTarget(mythicId));
    }

    public boolean familyMinibossContains(String family, String mythicId) {
        Set<String> bosses = familyMinibosses.get(normalizeTarget(family));
        return bosses != null && bosses.contains(normalizeTarget(mythicId));
    }

    public boolean isAnyMiniboss(String mythicId) {
        String id = normalizeTarget(mythicId);
        return familyMinibosses.values().stream().anyMatch(set -> set.contains(id));
    }

    public static String normalizeMission(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    public static String normalizeRotation(String value) { return normalizeMission(value); }

    public static String normalizeTarget(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
