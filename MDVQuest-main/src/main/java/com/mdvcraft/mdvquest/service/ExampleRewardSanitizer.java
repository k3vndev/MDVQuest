package com.mdvcraft.mdvquest.service;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Migra solamente el archivo de ejemplos incluido por MDVQuest. En staging evita
 * que una copia vieja de examples.yml conserve dinero u objetos valiosos.
 * No toca ningún otro YAML y puede desactivarse desde config.yml.
 */
public final class ExampleRewardSanitizer {
    private static final int SAFE_VERSION = 1;
    private final MDVQuestPlugin plugin;

    public ExampleRewardSanitizer(MDVQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public void run() {
        if (!plugin.getConfig().getBoolean("safety.sanitize-example-economy-rewards", true)) return;
        File file = new File(plugin.getDataFolder(), "missions/examples.yml");
        if (!file.isFile()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getInt("mdvquest-safe-rewards-version", 0) >= SAFE_VERSION) return;
        ConfigurationSection missions = yaml.getConfigurationSection("missions");
        if (missions == null) return;

        for (String missionId : missions.getKeys(false)) {
            ConfigurationSection mission = missions.getConfigurationSection(missionId);
            if (mission == null) continue;
            SafeReward safe = safeReward(mission.getString("rotation", "daily"));

            mission.set("rewards", null);
            ConfigurationSection rewards = mission.createSection("rewards");
            rewards.set("experience", List.of(Map.of(
                    "profession", "main",
                    "amount", safe.experience()
            )));
            rewards.set("vanilla-items", List.of(Map.of(
                    "material", "IRON_INGOT",
                    "amount", safe.iron()
            )));
        }

        yaml.set("mdvquest-safe-rewards-version", SAFE_VERSION);
        try {
            yaml.save(file);
            plugin.getLogger().info("examples.yml migrado a recompensas seguras: EXP principal baja y lingotes de hierro.");
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo sanear missions/examples.yml: " + ex.getMessage());
        }
    }

    private SafeReward safeReward(String rotation) {
        return switch (rotation == null ? "daily" : rotation.toLowerCase(Locale.ROOT)) {
            case "two-days" -> new SafeReward(40, 2);
            case "three-days" -> new SafeReward(50, 2);
            case "four-days" -> new SafeReward(60, 3);
            case "five-days" -> new SafeReward(70, 3);
            case "six-days" -> new SafeReward(80, 4);
            case "weekly" -> new SafeReward(100, 4);
            default -> new SafeReward(25, 1);
        };
    }

    private record SafeReward(int experience, int iron) { }
}
