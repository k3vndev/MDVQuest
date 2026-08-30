package com.mdvcraft.mdvquest.service;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.hook.MMOItemsHook;
import com.mdvcraft.mdvquest.hook.MythicItemsHook;
import com.mdvcraft.mdvquest.model.MissionInstance;
import com.mdvcraft.mdvquest.model.RewardDefinition;
import com.mdvcraft.mdvquest.storage.QuestDatabase;
import com.mdvcraft.mdvquest.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reclamación segura de recompensas.
 *
 * No usa addItem ni tira recompensas por falta de espacio. Primero construye todos los
 * objetos, cuenta stacks reales, reserva slots vacíos y recién entonces registra la
 * reclamación. La entrega se realiza al tick siguiente; si el jugador intenta ocupar
 * un slot reservado, ese objeto intruso se devuelve al mundo y la recompensa conserva
 * el lugar reservado.
 */
public final class RewardService {
    private final MDVQuestPlugin plugin;
    private final ProgressService progress;
    private final QuestDatabase database;
    private final MMOItemsHook mmoItems;
    private final MythicItemsHook mythicItems;
    private final AccessService access;
    private final Set<UUID> claiming = new HashSet<>();

    public RewardService(MDVQuestPlugin plugin, ProgressService progress, QuestDatabase database,
                         MMOItemsHook mmoItems, MythicItemsHook mythicItems, AccessService access) {
        this.plugin = plugin;
        this.progress = progress;
        this.database = database;
        this.mmoItems = mmoItems;
        this.mythicItems = mythicItems;
        this.access = access;
    }

    public boolean claim(Player player, MissionInstance instance) {
        long now = System.currentTimeMillis();
        if (instance == null || !instance.isActive(now)) {
            plugin.message(player, "mission-expired", Map.of());
            return false;
        }
        if (!access.hasAccess(player, instance.accessTier())) {
            plugin.message(player, "mission-rank-required", Map.of(
                    "rank", access.displayName(instance.accessTier()),
                    "permission", access.permission(instance.accessTier())
            ));
            plugin.getSocialHook().sound(player, "error");
            return false;
        }
        if (!progress.accepted(player, instance)) {
            plugin.message(player, "contract-not-accepted", Map.of("mission", instance.definition().name()));
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

        UUID claimKey = player.getUniqueId();
        synchronized (claiming) {
            if (!claiming.add(claimKey)) {
                player.sendMessage(plugin.prefix() + "§eEspera a que termine la entrega anterior.");
                return false;
            }
        }

        BuildResult built = buildItems(instance.definition().rewards());
        if (!built.success()) {
            synchronized (claiming) { claiming.remove(claimKey); }
            player.sendMessage(plugin.prefix() + "§cLa recompensa contiene un objeto inválido: §f" + built.error());
            plugin.getLogger().warning("Recompensa inválida en " + instance.definition().id() + ": " + built.error());
            return false;
        }

        List<Integer> reservedSlots = freeStorageSlots(player.getInventory(), built.items().size());
        if (reservedSlots.size() < built.items().size()) {
            synchronized (claiming) { claiming.remove(claimKey); }
            int missing = built.items().size() - reservedSlots.size();
            player.closeInventory();
            plugin.message(player, "inventory-slots-needed", Map.of(
                    "slots", String.valueOf(missing),
                    "required", String.valueOf(built.items().size())
            ));
            plugin.getSocialHook().sound(player, "error");
            return false;
        }

        // Cierra el menú para evitar mover objetos dentro de la GUI y deja un tick para
        // reproducir correctamente el caso de carrera solicitado.
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> finishClaim(player, instance, claimKey, built.items(), reservedSlots));
        return true;
    }

    private void finishClaim(Player player, MissionInstance instance, UUID claimKey,
                             List<ItemStack> items, List<Integer> reservedSlots) {
        try {
            if (!player.isOnline()) return;
            long now = System.currentTimeMillis();
            if (!instance.isActive(now)) {
                plugin.message(player, "mission-expired", Map.of());
                return;
            }
            if (!access.hasAccess(player, instance.accessTier())) {
                plugin.message(player, "mission-rank-required", Map.of(
                        "rank", access.displayName(instance.accessTier()),
                        "permission", access.permission(instance.accessTier())
                ));
                return;
            }
            if (progress.claimed(player, instance) || !progress.isMissionComplete(player, instance)) return;

            progress.flush(player);
            try {
                if (!database.claim(player.getUniqueId(), instance.id(), now)) {
                    progress.markClaimed(player, instance);
                    plugin.message(player, "already-claimed", Map.of());
                    return;
                }
            } catch (SQLException ex) {
                plugin.getLogger().severe("No se pudo registrar la reclamacion de " + player.getName() + ": " + ex.getMessage());
                player.sendMessage(plugin.prefix() + "§cNo se pudo guardar la reclamación. Inténtalo de nuevo.");
                return;
            }

            PlayerInventory inventory = player.getInventory();
            for (int i = 0; i < items.size(); i++) {
                int slot = reservedSlots.get(i);
                ItemStack intruder = inventory.getItem(slot);
                if (intruder != null && !intruder.getType().isAir()) {
                    inventory.setItem(slot, null);
                    player.getWorld().dropItemNaturally(player.getLocation(), intruder.clone());
                }
                inventory.setItem(slot, items.get(i).clone());
            }

            progress.markClaimed(player, instance);
            // No se elimina la aceptación: el contrato reclamado mantiene ocupado
            // su cupo hasta el próximo roll de esa rotación.
            giveNonItemRewards(player, instance);
            plugin.message(player, "mission-claimed", Map.of("mission", instance.definition().name()));
            plugin.getSocialHook().sound(player, "confirm");
        } finally {
            synchronized (claiming) { claiming.remove(claimKey); }
        }
    }

    private void giveNonItemRewards(Player player, MissionInstance instance) {
        RewardDefinition reward = instance.definition().rewards();
        String xpTemplate = plugin.getConfig().getString("rewards.mmocore-experience-command",
                "mmocore admin exp give %player% %profession% %amount% false");
        for (RewardDefinition.ExperienceReward experience : reward.experience()) {
            String command = xpTemplate
                    .replace("%player%", player.getName())
                    .replace("%profession%", experience.profession())
                    .replace("%amount%", String.valueOf(experience.amount()));
            dispatch(command);
        }
        for (String raw : reward.commands()) dispatch(placeholders(raw, player, instance));
    }

    private void dispatch(String command) {
        if (command == null) return;
        String clean = command.startsWith("/") ? command.substring(1) : command;
        if (!clean.isBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), clean);
    }

    public BuildResult buildItems(RewardDefinition reward) {
        if (reward == null) return new BuildResult(true, Collections.emptyList(), "");
        List<ItemStack> items = new ArrayList<>();

        for (RewardDefinition.VanillaItemReward configured : reward.vanillaItems()) {
            Material material = Material.matchMaterial(configured.material());
            if (material == null || material.isAir()) return BuildResult.failure("material vanilla " + configured.material());
            items.addAll(ItemUtil.splitStacks(new ItemStack(material), configured.amount()));
        }

        for (RewardDefinition.MmoItemReward configured : reward.mmoItems()) {
            ItemStack template = mmoItems.build(configured.type(), configured.id(), 1);
            if (template == null) return BuildResult.failure("MMOItems " + configured.type() + ":" + configured.id());
            items.addAll(ItemUtil.splitStacks(template, configured.amount()));
        }

        for (RewardDefinition.MythicItemReward configured : reward.mythicItems()) {
            ItemStack template = mythicItems.build(configured.id(), 1);
            if (template == null) return BuildResult.failure("Mythic/Crucible " + configured.id());
            items.addAll(ItemUtil.splitStacks(template, configured.amount()));
        }

        for (RewardDefinition.ExactItemReward configured : reward.exactItems()) {
            items.addAll(ItemUtil.splitStacks(configured.item(), configured.amount()));
        }

        if (items.size() > 36) return BuildResult.failure("requiere " + items.size() + " stacks y el inventario solo tiene 36 slots");
        return new BuildResult(true, List.copyOf(items), "");
    }

    /** Objetos ya construidos para mostrar en el menú de detalle. */
    public List<ItemStack> previewItems(RewardDefinition reward) {
        BuildResult result = buildItems(reward);
        return result.success() ? result.items().stream().map(ItemStack::clone).toList() : Collections.emptyList();
    }

    private List<Integer> freeStorageSlots(PlayerInventory inventory, int limit) {
        List<Integer> result = new ArrayList<>();
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length && result.size() < limit; slot++) {
            ItemStack item = storage[slot];
            if (item == null || item.getType().isAir()) result.add(slot);
        }
        return result;
    }

    private String placeholders(String value, Player player, MissionInstance instance) {
        return value
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%mission%", instance.definition().id())
                .replace("%rotation%", instance.rotationId());
    }

    public record BuildResult(boolean success, List<ItemStack> items, String error) {
        public static BuildResult failure(String error) {
            return new BuildResult(false, Collections.emptyList(), error);
        }
    }
}
