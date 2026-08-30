package com.mdvcraft.mdvquest.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Estado mutable y exclusivamente temporal del editor in-game. */
public final class QuestDraft {
    private String originalId;
    private String originalFile;
    private String id;
    private boolean enabled = true;
    private int durationDays = 1;
    private String rotation = "daily";
    private AccessTier accessTier = AccessTier.NORMAL;
    private int weight = 10;
    private String name = "&eNueva misión";
    private ItemStack icon = new ItemStack(Material.PAPER);
    private final List<String> lore = new ArrayList<>();
    private final List<ObjectiveDefinition> objectives = new ArrayList<>();
    private RewardDefinition rewards = new RewardDefinition(null, null, null, null);
    private String targetFile = "missions.yml";

    public static QuestDraft create(int durationDays) {
        QuestDraft draft = new QuestDraft();
        draft.setDurationDays(durationDays);
        draft.id = "mision-" + System.currentTimeMillis();
        draft.lore.add("&7Descripción pendiente.");
        return draft;
    }

    public static QuestDraft from(MissionDefinition mission, int durationDays) {
        QuestDraft draft = new QuestDraft();
        draft.originalId = mission.id();
        draft.originalFile = mission.sourceFile();
        draft.id = mission.id();
        draft.enabled = mission.enabled();
        draft.durationDays = durationDays;
        draft.rotation = mission.rotation();
        draft.accessTier = mission.accessTier();
        draft.weight = mission.weight();
        draft.name = mission.name();
        ItemStack iconItem = mission.iconItem();
        if (iconItem == null) {
            Material material = Material.matchMaterial(mission.icon());
            iconItem = new ItemStack(material == null || material.isAir() ? Material.PAPER : material);
        }
        draft.icon = iconItem.clone();
        draft.lore.addAll(mission.lore());
        draft.objectives.addAll(mission.objectives());
        draft.rewards = mission.rewards();
        draft.targetFile = mission.sourceFile();
        return draft;
    }

    public String originalId() { return originalId; }
    public String originalFile() { return originalFile; }
    public String id() { return id; }
    public boolean enabled() { return enabled; }
    public int durationDays() { return durationDays; }
    public String rotation() { return rotation; }
    public AccessTier accessTier() { return accessTier; }
    public int weight() { return weight; }
    public String name() { return name; }
    public ItemStack icon() { return icon.clone(); }
    public List<String> lore() { return lore; }
    public List<ObjectiveDefinition> objectives() { return objectives; }
    public RewardDefinition rewards() { return rewards; }
    public String targetFile() { return targetFile; }
    public boolean editingExisting() { return originalId != null; }

    public void setId(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT)
                .replace(' ', '-').replace('_', '-').replaceAll("[^a-z0-9-]", "");
        if (!normalized.isBlank()) this.id = normalized;
    }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setDurationDays(int durationDays) {
        this.durationDays = Math.max(1, Math.min(7, durationDays));
        this.rotation = rotationForDays(this.durationDays);
    }
    public void setRotation(String rotation) { this.rotation = rotation; }
    public void setAccessTier(AccessTier accessTier) { this.accessTier = accessTier == null ? AccessTier.NORMAL : accessTier; }
    public void setWeight(int weight) { this.weight = Math.max(1, weight); }
    public void setName(String name) { if (name != null && !name.isBlank()) this.name = name; }
    public void setIcon(ItemStack icon) {
        if (icon == null || icon.getType().isAir()) return;
        this.icon = icon.clone();
        this.icon.setAmount(1);
    }
    public void setLore(List<String> lore) {
        this.lore.clear();
        if (lore != null) this.lore.addAll(lore);
    }
    public void setRewards(RewardDefinition rewards) {
        this.rewards = rewards == null ? new RewardDefinition(null, null, null, null) : rewards;
    }
    public void setTargetFile(String targetFile) {
        if (targetFile == null || targetFile.isBlank()) return;
        String safe = targetFile.trim().replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".yml")) safe += ".yml";
        this.targetFile = safe;
    }

    public void replaceObjective(int index, ObjectiveDefinition objective) {
        if (index < 0 || index >= objectives.size()) objectives.add(objective);
        else objectives.set(index, objective);
    }

    public void resetKeepingDuration() {
        int days = durationDays;
        String file = targetFile;
        originalId = null;
        originalFile = null;
        id = "mision-" + System.currentTimeMillis();
        enabled = true;
        accessTier = AccessTier.NORMAL;
        weight = 10;
        name = "&eNueva misión";
        icon = new ItemStack(Material.PAPER);
        lore.clear();
        lore.add("&7Descripción pendiente.");
        objectives.clear();
        rewards = new RewardDefinition(null, null, null, null);
        targetFile = file;
        setDurationDays(days);
    }

    public static String rotationForDays(int days) {
        return switch (Math.max(1, Math.min(7, days))) {
            case 1 -> "daily";
            case 2 -> "two-days";
            case 3 -> "three-days";
            case 4 -> "four-days";
            case 5 -> "five-days";
            case 6 -> "six-days";
            default -> "weekly";
        };
    }
}
