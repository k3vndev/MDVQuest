package com.mdvcraft.mdvquest.hook;

import com.mdvcraft.mdvquest.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class MDVSocialHook {
    private Class<?> apiClass;

    public boolean available() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MDVSocial")) return false;
        if (apiClass != null) return true;
        try {
            apiClass = Class.forName("com.mdvcraft.mdvsocial.MDVSocialAPI");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public Inventory createInventory(InventoryHolder holder, String title, int size, boolean fill) {
        if (available()) {
            try {
                Object inventory = apiClass.getMethod("createInventory", String.class, int.class, boolean.class).invoke(null, title, size, fill);
                if (inventory instanceof Inventory inv) return inv;
            } catch (Throwable ignored) { }
        }
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtil.color(title));
        if (fill) {
            ItemStack filler = basicButton(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
            for (int i = 0; i < size; i++) inventory.setItem(i, filler);
        }
        return inventory;
    }

    public ItemStack button(Material material, int amount, String name, List<String> lore, String action, String sound) {
        if (available()) {
            try {
                Method method = apiClass.getMethod("createButton", Material.class, int.class, String.class, List.class,
                        String.class, String.class, List.class, boolean.class, String.class);
                Object result = method.invoke(null, material, amount, name, lore, action, "", List.of(), false, sound);
                if (result instanceof ItemStack item) return item;
            } catch (Throwable ignored) { }
        }
        return basicButton(material, name, lore);
    }

    public void sound(Player player, String key) {
        if (!available()) return;
        try { apiClass.getMethod("playUISound", Player.class, String.class).invoke(null, player, key); }
        catch (Throwable ignored) { }
    }

    private ItemStack basicButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(name));
            meta.setLore(ColorUtil.color(lore == null ? new ArrayList<>() : lore));
            if (!item.setItemMeta(meta)) return item;
        }
        return item;
    }
}
