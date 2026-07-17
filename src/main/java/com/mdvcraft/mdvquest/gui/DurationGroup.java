package com.mdvcraft.mdvquest.gui;

import org.bukkit.Material;

public enum DurationGroup {
    ONE_DAY("1 Día", Material.BOOK, 1, 1),
    TWO_THREE("2 a 3 Días", Material.BOOK, 2, 3),
    FOUR_SIX("4 a 6 Días", Material.BOOK, 4, 6),
    SEVEN_DAYS("7 Días", Material.BOOK, 7, 7);

    private final String display;
    private final Material material;
    private final int minDays;
    private final int maxDays;

    DurationGroup(String display, Material material, int minDays, int maxDays) {
        this.display = display;
        this.material = material;
        this.minDays = minDays;
        this.maxDays = maxDays;
    }

    public String display() { return display; }
    public Material material() { return material; }
    public int minDays() { return minDays; }
    public int maxDays() { return maxDays; }
    public boolean accepts(int days) { return days >= minDays && days <= maxDays; }
}
