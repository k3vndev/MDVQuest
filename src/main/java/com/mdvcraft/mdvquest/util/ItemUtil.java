package com.mdvcraft.mdvquest.util;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemUtil {
    private static final Pattern TEXTURE_URL = Pattern.compile("https?://[^\\\"}]+", Pattern.CASE_INSENSITIVE);

    private ItemUtil() { }

    public static ItemStack hideNativeTooltip(ItemStack source) {
        if (source == null) return null;
        ItemStack item = source.clone();
        item.setAmount(Math.max(1, Math.min(item.getAmount(), item.getMaxStackSize())));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static List<ItemStack> splitStacks(ItemStack source, int totalAmount) {
        List<ItemStack> result = new ArrayList<>();
        if (source == null || source.getType().isAir() || totalAmount <= 0) return result;
        int max = Math.max(1, source.getMaxStackSize());
        int remaining = totalAmount;
        while (remaining > 0) {
            ItemStack stack = source.clone();
            int amount = Math.min(max, remaining);
            stack.setAmount(amount);
            result.add(stack);
            remaining -= amount;
        }
        return result;
    }

    public static ItemStack backHead(MDVQuestPlugin plugin, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = head.getItemMeta();
        if (rawMeta instanceof SkullMeta meta) {
            String texture = plugin.getConfig().getString("menus.back-head-texture", "");
            String url = textureUrl(texture);
            if (!url.isBlank()) {
                try {
                    PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                    PlayerTextures textures = profile.getTextures();
                    textures.setSkin(new URL(url));
                    profile.setTextures(textures);
                    meta.setOwnerProfile(profile);
                } catch (Throwable ex) {
                    plugin.getLogger().fine("No se pudo aplicar la textura de la cabeza Volver: " + ex.getMessage());
                }
            }
            meta.setDisplayName(ColorUtil.color(name));
            meta.setLore(ColorUtil.color(lore));
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }

    private static String textureUrl(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            Matcher matcher = TEXTURE_URL.matcher(decoded);
            return matcher.find() ? matcher.group() : "";
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
}
