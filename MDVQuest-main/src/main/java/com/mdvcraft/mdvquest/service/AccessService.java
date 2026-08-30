package com.mdvcraft.mdvquest.service;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.AccessTier;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Centraliza permisos, nombres e iconos bloqueados de los pools de acceso. */
public final class AccessService {
    private final MDVQuestPlugin plugin;

    public AccessService(MDVQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasAccess(Player player, AccessTier required) {
        if (required == null || required == AccessTier.NORMAL) return true;
        if (player == null) return false;
        if (required == AccessTier.VIP1 && plugin.getConfig().getBoolean("access-tiers.vip2-inherits-vip1", true)) {
            return hasExactPermission(player, AccessTier.VIP1) || hasExactPermission(player, AccessTier.VIP2);
        }
        return hasExactPermission(player, required);
    }

    private boolean hasExactPermission(Player player, AccessTier tier) {
        String permission = permission(tier);
        return permission.isBlank() || player.hasPermission(permission);
    }

    public String permission(AccessTier tier) {
        if (tier == null || tier == AccessTier.NORMAL) return "";
        String configured = plugin.getConfig().getString("access-tiers." + tier.key() + ".permission",
                tier == AccessTier.VIP1 ? "mdvquest.access.vip1" : "mdvquest.access.vip2");
        return configured == null ? "" : configured.trim();
    }

    public String displayName(AccessTier tier) {
        AccessTier safe = tier == null ? AccessTier.NORMAL : tier;
        return plugin.getConfig().getString("access-tiers." + safe.key() + ".display-name",
                switch (safe) {
                    case NORMAL -> "Normal";
                    case VIP1 -> "VIP";
                    case VIP2 -> "VIP 2";
                });
    }

    public Material lockedMaterial(AccessTier tier) {
        AccessTier safe = tier == null ? AccessTier.NORMAL : tier;
        Material fallback = safe == AccessTier.VIP2
                ? Material.YELLOW_STAINED_GLASS_PANE
                : Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        String raw = plugin.getConfig().getString("access-tiers." + safe.key() + ".locked-material", fallback.name());
        Material material = Material.matchMaterial(raw == null ? "" : raw);
        return material == null || material.isAir() ? fallback : material;
    }
}
