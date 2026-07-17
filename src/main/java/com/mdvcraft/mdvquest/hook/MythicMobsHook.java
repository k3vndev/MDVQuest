package com.mdvcraft.mdvquest.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class MythicMobsHook {
    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    public Optional<String> mythicId(Entity entity) {
        if (!available() || entity == null) return Optional.empty();
        try {
            Class<?> mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object api = mythicBukkit.getMethod("inst").invoke(null);
            Object manager = api.getClass().getMethod("getMobManager").invoke(api);
            Object optional = manager.getClass().getMethod("getActiveMob", UUID.class).invoke(manager, entity.getUniqueId());
            Object active = optional instanceof Optional<?> opt ? opt.orElse(null) : optional;
            if (active == null) return Optional.empty();

            for (String methodName : new String[]{"getMobType", "getType"}) {
                try {
                    Object value = active.getClass().getMethod(methodName).invoke(active);
                    String id = extractId(value);
                    if (!id.isBlank()) return Optional.of(normalize(id));
                } catch (NoSuchMethodException ignored) { }
            }
        } catch (Throwable ignored) { }
        return Optional.empty();
    }

    private String extractId(Object value) {
        if (value == null) return "";
        if (value instanceof String string) return string;
        for (String method : new String[]{"getInternalName", "getInternalNameString", "getName", "getId"}) {
            try {
                Object result = value.getClass().getMethod(method).invoke(value);
                if (result != null) return String.valueOf(result);
            } catch (Throwable ignored) { }
        }
        return String.valueOf(value);
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
