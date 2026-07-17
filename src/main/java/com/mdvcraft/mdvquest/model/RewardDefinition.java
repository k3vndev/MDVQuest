package com.mdvcraft.mdvquest.model;

import java.util.Collections;
import java.util.List;

public record RewardDefinition(
        List<String> displayLore,
        List<String> commands,
        List<VanillaItemReward> vanillaItems,
        List<MmoItemReward> mmoItems
) {
    public RewardDefinition {
        displayLore = displayLore == null ? Collections.emptyList() : List.copyOf(displayLore);
        commands = commands == null ? Collections.emptyList() : List.copyOf(commands);
        vanillaItems = vanillaItems == null ? Collections.emptyList() : List.copyOf(vanillaItems);
        mmoItems = mmoItems == null ? Collections.emptyList() : List.copyOf(mmoItems);
    }

    public record VanillaItemReward(String material, int amount) { }
    public record MmoItemReward(String type, String id, int amount) { }
}
