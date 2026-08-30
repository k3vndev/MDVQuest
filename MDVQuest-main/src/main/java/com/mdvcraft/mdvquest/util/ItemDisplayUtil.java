package com.mdvcraft.mdvquest.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Utilidades de nombres/lore que preservan traducciones del cliente y nombres custom. */
public final class ItemDisplayUtil {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ItemDisplayUtil() { }

    public static Component legacy(String value) {
        return LEGACY.deserialize(value == null ? "" : value)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> legacyLines(List<String> values) {
        List<Component> result = new ArrayList<>();
        if (values != null) for (String value : values) result.add(legacy(value));
        return result;
    }

    /**
     * Devuelve el nombre efectivo que ve el jugador. Para objetos vanilla queda como
     * componente traducible por el cliente; para MMOItems/Mythic/Crucible conserva
     * el display name real configurado en el objeto construido.
     */
    public static Component effectiveName(ItemStack item) {
        if (item == null || item.getType().isAir()) return legacy("&fObjeto");
        try {
            return item.effectiveName().decoration(TextDecoration.ITALIC, false);
        } catch (Throwable ignored) {
            return legacy("&f" + prettify(item.getType().name()));
        }
    }

    public static Component rewardLine(int amount, ItemStack item, String amountColor) {
        Component base = legacy("&7• " + (amountColor == null ? "&f" : amountColor)
                + Math.max(1, amount) + "x ");
        return base.append(effectiveName(item)).decoration(TextDecoration.ITALIC, false);
    }

    public static String plainName(ItemStack item) {
        return PLAIN.serialize(effectiveName(item));
    }

    public static String prettify(String raw) {
        if (raw == null || raw.isBlank()) return "Objeto";
        String[] parts = raw.trim().replace('-', '_').split("_+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1).toLowerCase());
        }
        return out.isEmpty() ? "Objeto" : out.toString();
    }
}
