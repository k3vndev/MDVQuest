package com.mdvcraft.mdvquest.model;

import java.util.Locale;

/**
 * Nivel de acceso de una definición o de una instancia activa.
 *
 * En una definición indica en qué catálogo puede participar. En una instancia
 * indica qué permiso se exige para reclamar su recompensa.
 */
public enum AccessTier {
    NORMAL("normal", 0),
    VIP1("vip1", 1),
    VIP2("vip2", 2);

    private final String key;
    private final int level;

    AccessTier(String key, int level) {
        this.key = key;
        this.level = level;
    }

    public String key() {
        return key;
    }

    public int level() {
        return level;
    }

    public static AccessTier parse(String raw) {
        if (raw == null || raw.isBlank()) return NORMAL;
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "vip", "vip_1", "vip1", "rango_vip", "premium" -> VIP1;
            case "vip_2", "vip2", "rango_vip2", "premium2" -> VIP2;
            default -> NORMAL;
        };
    }
}
