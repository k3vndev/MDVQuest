package com.mdvcraft.mdvquest.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

public final class MMOItemsHook {
    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("MMOItems");
    }

    public Optional<Identity> identify(ItemStack itemStack) {
        if (!available() || itemStack == null || itemStack.getType().isAir()) return Optional.empty();
        for (String className : new String[]{
                "net.Indyuce.mmoitems.api.item.NBTItem",
                "io.lumine.mythic.lib.api.item.NBTItem",
                "net.Indyuce.mmoitems.api.item.nbt.NBTItem"
        }) {
            Optional<Identity> identity = tryIdentity(className, itemStack);
            if (identity.isPresent()) return identity;
        }
        return Optional.empty();
    }

    private Optional<Identity> tryIdentity(String className, ItemStack stack) {
        try {
            Class<?> nbtClass = Class.forName(className);
            Object nbt = nbtClass.getMethod("get", ItemStack.class).invoke(null, stack);
            if (nbt == null) return Optional.empty();
            Object hasType = nbtClass.getMethod("hasType").invoke(nbt);
            if (!(hasType instanceof Boolean b) || !b) return Optional.empty();
            Object typeObject = nbtClass.getMethod("getType").invoke(nbt);
            String type = normalizeType(typeObject);
            Object idObject = nbtClass.getMethod("getString", String.class).invoke(nbt, "MMOITEMS_ITEM_ID");
            String id = idObject == null ? "" : String.valueOf(idObject);
            if (type.isBlank() || id.isBlank()) return Optional.empty();
            return Optional.of(new Identity(normalize(type), normalize(id)));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public ItemStack build(String typeId, String itemId, int amount) {
        if (!available() || typeId == null || itemId == null) return null;
        try {
            Class<?> clazz = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Field field = clazz.getField("plugin");
            Object plugin = field.get(null);
            if (plugin == null) return null;
            Object types = plugin.getClass().getMethod("getTypes").invoke(plugin);
            Object type = types.getClass().getMethod("get", String.class).invoke(types, normalize(typeId));
            if (type == null) return null;
            for (Method method : plugin.getClass().getMethods()) {
                if (!method.getName().equals("getItem") || method.getParameterCount() != 2) continue;
                if (!method.getParameterTypes()[1].equals(String.class) || !method.getParameterTypes()[0].isInstance(type)) continue;
                Object built = method.invoke(plugin, type, normalize(itemId));
                if (built instanceof ItemStack item) {
                    item = item.clone();
                    item.setAmount(Math.max(1, amount));
                    return item;
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private String normalizeType(Object type) {
        if (type == null) return "";
        try {
            Object id = type.getClass().getMethod("getId").invoke(type);
            if (id != null) return String.valueOf(id);
        } catch (Throwable ignored) { }
        return String.valueOf(type);
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    public record Identity(String type, String id) {
        public String combined() { return type + ":" + id; }
    }
}
