package com.mdvcraft.mdvquest.service;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class MDVSocialMenuInstaller {
    private final MDVQuestPlugin plugin;

    public MDVSocialMenuInstaller(MDVQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public void installIfNeeded() {
        if (!plugin.getConfig().getBoolean("mdvsocial.enabled", true)
                || !plugin.getConfig().getBoolean("mdvsocial.auto-install-main-button", true)
                || !Bukkit.getPluginManager().isPluginEnabled("MDVSocial")) return;

        String menuId = plugin.getConfig().getString("mdvsocial.main-menu-id", "main");
        File file = new File(Bukkit.getPluginsFolder(), "MDVSocial/Menus/" + menuId + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("No se encontro el menu de MDVSocial para instalar el boton: " + file.getPath());
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String buttonId = plugin.getConfig().getString("mdvsocial.button-id", "misiones");
        String path = "items." + buttonId;
        if (yaml.isConfigurationSection(path)) return;

        int slot = plugin.getConfig().getInt("mdvsocial.button-slot", 10);
        if (isSlotUsed(yaml, slot)) {
            for (int candidate : List.of(10, 12, 14, 16, 19, 20, 21, 22, 23, 24, 25)) {
                if (!isSlotUsed(yaml, candidate)) { slot = candidate; break; }
            }
        }

        yaml.set(path + ".slot", slot);
        yaml.set(path + ".material", plugin.getConfig().getString("mdvsocial.button-material", "COMPASS"));
        yaml.set(path + ".name", plugin.getConfig().getString("mdvsocial.button-name", "&dMisiones"));
        yaml.set(path + ".lore", plugin.getConfig().getStringList("mdvsocial.button-lore"));
        yaml.set(path + ".action", "COMMAND_PLAYER");
        yaml.set(path + ".commands", List.of("mdvquest"));
        yaml.set(path + ".close-on-click", true);
        yaml.set(path + ".sound", "open");
        try {
            yaml.save(file);
            plugin.getLogger().info("Boton de MDVQuest agregado al menu principal de MDVSocial en slot " + slot + ".");
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mdvsocial reload"));
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo actualizar el menu de MDVSocial: " + ex.getMessage());
        }
    }

    private boolean isSlotUsed(YamlConfiguration yaml, int slot) {
        var items = yaml.getConfigurationSection("items");
        if (items == null) return false;
        for (String key : items.getKeys(false)) {
            if (items.getInt(key + ".slot", -1) == slot) return true;
            if (items.getIntegerList(key + ".slots").contains(slot)) return true;
        }
        return false;
    }
}
