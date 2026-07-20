package com.mdvcraft.mdvquest.api;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.ObjectiveType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Map;

public final class MDVQuestAPI {
    private MDVQuestAPI() { }

    private static MDVQuestPlugin plugin() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MDVQuest");
        return plugin instanceof MDVQuestPlugin mdvQuest && plugin.isEnabled() ? mdvQuest : null;
    }

    public static int report(Player player, ObjectiveType type, String target, long amount) {
        return report(player, type, target, amount, Collections.emptyMap());
    }

    public static int report(Player player, ObjectiveType type, String target, long amount, Map<String, String> data) {
        MDVQuestPlugin plugin = plugin();
        if (plugin == null || player == null) return 0;
        return plugin.getProgressService().report(player, type, target, amount, data == null ? Collections.emptyMap() : data);
    }

    public static int reportEvent(Player player, String eventId, long amount) {
        return report(player, ObjectiveType.COMPLETE_EVENT, eventId, amount);
    }

    public static int reportProfessionExperience(Player player, String professionId, long amount) {
        return report(player, ObjectiveType.EARN_PROFESSION_EXP, professionId, amount);
    }

    public static boolean openMenu(Player player) {
        return openViewerMenu(player);
    }

    public static boolean openViewerMenu(Player player) {
        MDVQuestPlugin plugin = plugin();
        if (plugin == null || player == null) return false;
        plugin.getMenuManager().openViewer(player);
        return true;
    }

    public static boolean openInteractiveMenu(Player player) {
        MDVQuestPlugin plugin = plugin();
        if (plugin == null || player == null) return false;
        plugin.getMenuManager().openInteractive(player);
        return true;
    }
}
