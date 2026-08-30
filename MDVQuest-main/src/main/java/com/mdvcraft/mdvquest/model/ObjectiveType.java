package com.mdvcraft.mdvquest.model;

import java.util.Locale;

public enum ObjectiveType {
    MINE_BLOCK,
    BREAK_CUSTOM_ORE,
    CUT_LOG,
    HARVEST_CROP,
    KILL_VANILLA_MOB,
    KILL_MYTHIC_MOB,
    KILL_MOB_FAMILY,
    KILL_MINIBOSS,
    KILL_ANY_HOSTILE_MOB,
    CRAFT_VANILLA_ITEM,
    CRAFT_RECIPE,
    CRAFT_CATEGORY,
    OBTAIN_MMOITEM,
    DELIVER_MMOITEM,
    DELIVER_VANILLA_ITEM,
    USE_CONSUMABLE,
    EARN_PROFESSION_EXP,
    COMPLETE_EVENT,
    PLAYER_KILL,
    CLAN_KILL;

    public static ObjectiveType parse(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Objective type is missing");
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "KILL_MOB", "KILL_VANILLA" -> KILL_VANILLA_MOB;
            case "KILL_ALL_HOSTILE", "KILL_GENERAL_MOB", "KILL_HOSTILE_MOB" -> KILL_ANY_HOSTILE_MOB;
            case "MINE_VANILLA_BLOCK", "BREAK_BLOCK" -> MINE_BLOCK;
            case "CRAFT_MDVRECIPE" -> CRAFT_RECIPE;
            case "DELIVER_ITEM" -> DELIVER_VANILLA_ITEM;
            default -> ObjectiveType.valueOf(normalized);
        };
    }

    public boolean isDelivery() {
        return this == DELIVER_MMOITEM || this == DELIVER_VANILLA_ITEM;
    }
}
