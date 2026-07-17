package com.mdvcraft.mdvquest.service;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.hook.MMOItemsHook;
import com.mdvcraft.mdvquest.model.MissionInstance;
import com.mdvcraft.mdvquest.model.ObjectiveDefinition;
import com.mdvcraft.mdvquest.model.ObjectiveType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class DeliveryService {
    private final MDVQuestPlugin plugin;
    private final ProgressService progress;
    private final MMOItemsHook mmoItems;

    public DeliveryService(MDVQuestPlugin plugin, ProgressService progress, MMOItemsHook mmoItems) {
        this.plugin = plugin;
        this.progress = progress;
        this.mmoItems = mmoItems;
    }

    public long deliver(Player player, MissionInstance instance, ObjectiveDefinition objective) {
        if (objective.type() != ObjectiveType.DELIVER_MMOITEM && objective.type() != ObjectiveType.DELIVER_VANILLA_ITEM) return 0;
        long current = progress.progress(player, instance, objective);
        long remaining = Math.max(0L, objective.amount() - current);
        if (remaining <= 0) return 0;

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        long delivered = 0;
        for (int slot = 0; slot < contents.length && delivered < remaining; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir() || !matches(objective, item)) continue;
            int take = (int) Math.min(item.getAmount(), remaining - delivered);
            int left = item.getAmount() - take;
            if (left <= 0) contents[slot] = null;
            else {
                ItemStack changed = item.clone();
                changed.setAmount(left);
                contents[slot] = changed;
            }
            delivered += take;
        }

        if (delivered <= 0) {
            plugin.message(player, "delivery-none", Map.of());
            return 0;
        }
        inventory.setStorageContents(contents);
        progress.incrementSpecific(player, instance, objective, delivered);
        progress.flush(player);
        long updated = progress.progress(player, instance, objective);
        plugin.message(player, "delivery-progress", Map.of(
                "amount", String.valueOf(delivered),
                "progress", String.valueOf(updated),
                "required", String.valueOf(objective.amount())
        ));
        plugin.getSocialHook().sound(player, "confirm");
        return delivered;
    }

    private boolean matches(ObjectiveDefinition objective, ItemStack item) {
        if (objective.type() == ObjectiveType.DELIVER_VANILLA_ITEM) {
            String configured = objective.string("material", "");
            if (!configured.isBlank()) {
                Material material = Material.matchMaterial(configured);
                return material != null && material == item.getType();
            }
            return objective.targetMatches(item.getType().name());
        }

        Optional<MMOItemsHook.Identity> identity = mmoItems.identify(item);
        if (identity.isEmpty()) return false;
        String type = MMOItemsHook.normalize(objective.string("mmoitems-type", objective.string("type-id", "")));
        String id = MMOItemsHook.normalize(objective.string("mmoitems-id", objective.string("item-id", "")));
        if (!type.isBlank() && !type.equals(identity.get().type())) return false;
        if (!id.isBlank() && !id.equals(identity.get().id())) return false;
        if (type.isBlank() && id.isBlank()) return objective.targetMatches(identity.get().combined());
        return true;
    }
}
