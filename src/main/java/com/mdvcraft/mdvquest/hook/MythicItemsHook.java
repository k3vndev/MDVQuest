package com.mdvcraft.mdvquest.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

/**
 * Integración reflectiva con el ItemManager de MythicMobs.
 * MythicCrucible usa el mismo sistema de Items de MythicMobs, por lo que sus objetos
 * también se identifican y reconstruyen desde aquí sin dependencia dura.
 */
public final class MythicItemsHook {
    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    public Optional<String> identify(ItemStack item) {
        if (!available() || item == null || item.getType().isAir()) return Optional.empty();
        try {
            Object manager = itemManager();
            if (manager == null) return Optional.empty();
            Method method = findMethod(manager.getClass(), "getMythicTypeFromItem", ItemStack.class);
            if (method == null) return Optional.empty();
            Object result = unwrap(method.invoke(manager, item));
            String id = result == null ? "" : String.valueOf(result).trim();
            if (id.isBlank() || id.equalsIgnoreCase("null") || id.equalsIgnoreCase("none")) return Optional.empty();
            return Optional.of(id);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public ItemStack build(String itemId, int amount) {
        if (!available() || itemId == null || itemId.isBlank()) return null;
        try {
            Object manager = itemManager();
            if (manager == null) return null;
            Method withAmount = findMethod(manager.getClass(), "getItemStack", String.class, int.class);
            Object built;
            if (withAmount != null) built = withAmount.invoke(manager, itemId, Math.max(1, amount));
            else {
                Method single = findMethod(manager.getClass(), "getItemStack", String.class);
                if (single == null) return null;
                built = single.invoke(manager, itemId);
            }
            built = unwrap(built);
            if (built instanceof ItemStack item) {
                ItemStack clone = item.clone();
                clone.setAmount(Math.max(1, amount));
                return clone;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private Object itemManager() throws ReflectiveOperationException {
        Class<?> mythic = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
        Object instance = mythic.getMethod("inst").invoke(null);
        return instance.getClass().getMethod("getItemManager").invoke(instance);
    }

    private Object unwrap(Object value) {
        if (value instanceof Optional<?> optional) return optional.orElse(null);
        return value;
    }

    private Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        try { return type.getMethod(name, parameters); }
        catch (NoSuchMethodException ignored) { return null; }
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
    }
}
