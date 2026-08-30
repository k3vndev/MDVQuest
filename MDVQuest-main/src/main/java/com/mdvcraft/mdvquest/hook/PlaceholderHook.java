package com.mdvcraft.mdvquest.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class PlaceholderHook {
    public String apply(OfflinePlayer player, String text) {
        if (text == null || text.isBlank() || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text == null ? "" : text;
        try {
            Class<?> clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Object result = clazz.getMethod("setPlaceholders", OfflinePlayer.class, String.class).invoke(null, player, text);
            return result == null ? text : String.valueOf(result);
        } catch (Throwable ignored) {
            return text;
        }
    }
}
