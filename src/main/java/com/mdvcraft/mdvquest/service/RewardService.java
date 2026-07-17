package com.mdvcraft.mdvquest.service;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.hook.MMOItemsHook;
import com.mdvcraft.mdvquest.model.MissionInstance;
import com.mdvcraft.mdvquest.model.RewardDefinition;
import com.mdvcraft.mdvquest.storage.QuestDatabase;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RewardService {
    private final MDVQuestPlugin plugin;
    private final ProgressService progress;
    private final QuestDatabase database;
    private final MMOItemsHook mmoItems;

    public RewardService(MDVQuestPlugin plugin, ProgressService progress, QuestDatabase database, MMOItemsHook mmoItems) {
        this.plugin = plugin;
        this.progress = progress;
        this.database = database;
        this.mmoItems = mmoItems;
    }

    public boolean claim(Player player, MissionInstance instance) {
        long now = System.currentTimeMillis();
        if (instance == null || !instance.isActive(now)) {
            plugin.message(player, "mission-expired", Map.of());
            return false;
        }
        if (progress.claimed(player, instance)) {
            plugin.message(player, "already-claimed", Map.of());
            return false;
        }
        if (!progress.isMissionComplete(player, instance)) {
            plugin.message(player, "not-completed", Map.of());
            return false;
        }

        progress.flush(player);
        try {
            if (!database.claim(player.getUniqueId(), instance.id(), now)) {
                progress.markClaimed(player, instance);
                plugin.message(player, "already-claimed", Map.of());
                return false;
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe("No se pudo registrar la reclamacion de " + player.getName() + ": " + ex.getMessage());
            player.sendMessage(plugin.prefix() + "§cNo se pudo guardar la reclamacion. Intentalo de nuevo.");
            return false;
        }

        progress.markClaimed(player, instance);
        giveRewards(player, instance);
        plugin.message(player, "mission-claimed", Map.of("mission", instance.definition().name()));
        plugin.getSocialHook().sound(player, "confirm");
        return true;
    }

    private void giveRewards(Player player, MissionInstance instance) {
        RewardDefinition reward = instance.definition().rewards();
        List<ItemStack> items = new ArrayList<>();
        for (RewardDefinition.VanillaItemReward itemReward : reward.vanillaItems()) {
            Material material = Material.matchMaterial(itemReward.material());
            if (material != null && !material.isAir()) items.add(new ItemStack(material, Math.max(1, itemReward.amount())));
        }
        for (RewardDefinition.MmoItemReward itemReward : reward.mmoItems()) {
            ItemStack item = mmoItems.build(itemReward.type(), itemReward.id(), itemReward.amount());
            if (item != null) items.add(item);
            else {
                String command = "mi give " + itemReward.type() + " " + itemReward.id() + " " + player.getName() + " " + itemReward.amount();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        for (String raw : reward.commands()) {
            String command = placeholders(raw, player, instance);
            if (command.startsWith("/")) command = command.substring(1);
            if (!command.isBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private String placeholders(String value, Player player, MissionInstance instance) {
        return value
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%mission%", instance.definition().id())
                .replace("%rotation%", instance.rotationId());
    }
}
