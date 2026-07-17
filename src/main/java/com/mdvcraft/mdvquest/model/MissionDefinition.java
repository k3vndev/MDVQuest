package com.mdvcraft.mdvquest.model;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class MissionDefinition {
    private final String id;
    private final String rotation;
    private final int weight;
    private final String name;
    private final String icon;
    private final List<String> lore;
    private final List<ObjectiveDefinition> objectives;
    private final RewardDefinition rewards;

    public MissionDefinition(String id, String rotation, int weight, String name, String icon, List<String> lore,
                             List<ObjectiveDefinition> objectives, RewardDefinition rewards) {
        this.id = normalize(id);
        this.rotation = normalize(rotation);
        this.weight = Math.max(1, weight);
        this.name = name == null || name.isBlank() ? this.id : name;
        this.icon = icon == null || icon.isBlank() ? "PAPER" : icon.toUpperCase(Locale.ROOT);
        this.lore = lore == null ? Collections.emptyList() : List.copyOf(lore);
        this.objectives = List.copyOf(Objects.requireNonNull(objectives, "objectives"));
        this.rewards = rewards == null ? new RewardDefinition(null, null, null, null) : rewards;
        if (this.objectives.isEmpty()) throw new IllegalArgumentException("Mission " + id + " has no objectives");
    }

    public String id() { return id; }
    public String rotation() { return rotation; }
    public int weight() { return weight; }
    public String name() { return name; }
    public String icon() { return icon; }
    public List<String> lore() { return lore; }
    public List<ObjectiveDefinition> objectives() { return objectives; }
    public RewardDefinition rewards() { return rewards; }

    public ObjectiveDefinition objective(String objectiveId) {
        String normalized = ObjectiveDefinition.normalize(objectiveId);
        return objectives.stream().filter(o -> o.id().equals(normalized)).findFirst().orElse(null);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
    }
}
