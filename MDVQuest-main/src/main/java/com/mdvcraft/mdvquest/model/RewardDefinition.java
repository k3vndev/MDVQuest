package com.mdvcraft.mdvquest.model;

import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * Recompensas estructuradas de una misión.
 *
 * Los comandos siguen disponibles para integraciones libres, pero los objetos y la
 * experiencia se modelan de forma explícita para poder previsualizarlos y validar
 * espacio antes de reclamar.
 */
public record RewardDefinition(
        List<String> displayLore,
        List<String> commands,
        List<VanillaItemReward> vanillaItems,
        List<MmoItemReward> mmoItems,
        List<MythicItemReward> mythicItems,
        List<ExactItemReward> exactItems,
        List<ExperienceReward> experience
) {
    public RewardDefinition {
        displayLore = displayLore == null ? Collections.emptyList() : List.copyOf(displayLore);
        commands = commands == null ? Collections.emptyList() : List.copyOf(commands);
        vanillaItems = vanillaItems == null ? Collections.emptyList() : List.copyOf(vanillaItems);
        mmoItems = mmoItems == null ? Collections.emptyList() : List.copyOf(mmoItems);
        mythicItems = mythicItems == null ? Collections.emptyList() : List.copyOf(mythicItems);
        exactItems = exactItems == null ? Collections.emptyList() : exactItems.stream()
                .map(reward -> new ExactItemReward(reward.item().clone(), reward.amount()))
                .toList();
        experience = experience == null ? Collections.emptyList() : List.copyOf(experience);
    }

    /** Compatibilidad con el constructor usado por MDVQuest 1.0.x. */
    public RewardDefinition(List<String> displayLore,
                            List<String> commands,
                            List<VanillaItemReward> vanillaItems,
                            List<MmoItemReward> mmoItems) {
        this(displayLore, commands, vanillaItems, mmoItems, null, null, null);
    }

    public boolean empty() {
        return commands.isEmpty() && vanillaItems.isEmpty() && mmoItems.isEmpty()
                && mythicItems.isEmpty() && exactItems.isEmpty() && experience.isEmpty();
    }

    public record VanillaItemReward(String material, int amount) { }
    public record MmoItemReward(String type, String id, int amount) { }
    public record MythicItemReward(String id, int amount) { }

    /**
     * Fallback para un objeto vanilla con meta que no pertenece a MMOItems ni Mythic.
     * Se serializa con la API nativa de Bukkit dentro del YAML.
     */
    public record ExactItemReward(ItemStack item, int amount) {
        public ExactItemReward {
            if (item == null) throw new IllegalArgumentException("item no puede ser null");
            item = item.clone();
            amount = Math.max(1, amount);
        }
    }

    /** profession=main para nivel principal; cualquier otro ID se trata como profesión MMOCore. */
    public record ExperienceReward(String profession, long amount) {
        public ExperienceReward {
            profession = profession == null || profession.isBlank() ? "main" : profession.trim();
            amount = Math.max(1L, amount);
        }
    }
}
