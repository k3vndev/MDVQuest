package com.mdvcraft.mdvquest.model;

import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class MissionDefinition {
    private final String id;
    private final boolean enabled;
    private final String rotation;
    private final int weight;
    private final String name;
    private final String icon;
    private final ItemStack iconItem;
    private final List<String> lore;
    private final List<ObjectiveDefinition> objectives;
    private final RewardDefinition rewards;
    private final String sourceFile;

    public MissionDefinition(String id, boolean enabled, String rotation, int weight, String name,
                             String icon, ItemStack iconItem, List<String> lore,
                             List<ObjectiveDefinition> objectives, RewardDefinition rewards,
                             String sourceFile) {
        this.id = normalize(id);
        this.enabled = enabled;
        this.rotation = normalize(rotation);
        this.weight = Math.max(1, weight);
        this.name = name == null || name.isBlank() ? this.id : name;
        this.icon = icon == null || icon.isBlank() ? "PAPER" : icon.toUpperCase(Locale.ROOT);
        this.iconItem = iconItem == null ? null : iconItem.clone();
        this.lore = lore == null ? Collections.emptyList() : List.copyOf(lore);
        this.objectives = List.copyOf(Objects.requireNonNull(objectives, "objectives"));
        this.rewards = rewards == null ? new RewardDefinition(null, null, null, null) : rewards;
        this.sourceFile = sourceFile == null || sourceFile.isBlank() ? "missions.yml" : sourceFile;
        if (this.objectives.isEmpty()) throw new IllegalArgumentException("Mission " + id + " has no objectives");
    }

    /** Compatibilidad con código 1.0.x. */
    public MissionDefinition(String id, String rotation, int weight, String name, String icon, List<String> lore,
                             List<ObjectiveDefinition> objectives, RewardDefinition rewards) {
        this(id, true, rotation, weight, name, icon, null, lore, objectives, rewards, "missions.yml");
    }

    public String id() { return id; }
    public boolean enabled() { return enabled; }
    public String rotation() { return rotation; }
    public int weight() { return weight; }
    public String name() { return name; }
    public String icon() { return icon; }
    public ItemStack iconItem() { return iconItem == null ? null : iconItem.clone(); }
    public List<String> lore() { return lore; }
    public List<ObjectiveDefinition> objectives() { return objectives; }
    public RewardDefinition rewards() { return rewards; }
    public String sourceFile() { return sourceFile; }

    public ObjectiveDefinition objective(String objectiveId) {
        String normalized = ObjectiveDefinition.normalize(objectiveId);
        return objectives.stream().filter(o -> o.id().equals(normalized)).findFirst().orElse(null);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
    }
}
