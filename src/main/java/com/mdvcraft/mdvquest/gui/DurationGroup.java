package com.mdvcraft.mdvquest.gui;

import org.bukkit.Material;

public enum DurationGroup {
    ONE_DAY("one-day", "1 Día", Material.BOOK, 1, 1),
    TWO_THREE("two-three-days", "2 a 3 Días", Material.BOOK, 2, 3),
    FOUR_SIX("four-six-days", "4 a 6 Días", Material.BOOK, 4, 6),
    SEVEN_DAYS("seven-days", "7 Días", Material.BOOK, 7, 7);

    private final String configKey;
    private final String display;
    private final Material material;
    private final int minDays;
    private final int maxDays;

    DurationGroup(String configKey, String display, Material material, int minDays, int maxDays) {
        this.configKey = configKey;
        this.display = display;
        this.material = material;
        this.minDays = minDays;
        this.maxDays = maxDays;
    }

    public String configKey() { return configKey; }
    public String display() { return display; }
    public Material material() { return material; }
    public int minDays() { return minDays; }
    public int maxDays() { return maxDays; }
    public boolean accepts(int days) { return days >= minDays && days <= maxDays; }
}
