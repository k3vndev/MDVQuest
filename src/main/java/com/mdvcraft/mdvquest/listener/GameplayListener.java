package com.mdvcraft.mdvquest.listener;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.hook.MMOItemsHook;
import com.mdvcraft.mdvquest.model.ObjectiveType;
import com.mdvcraft.mdvquest.service.PlacedBlockService;
import com.mdvcraft.mdvquest.service.ProgressService;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class GameplayListener implements Listener {
    private final MDVQuestPlugin plugin;
    private final ProgressService progress;
    private final PlacedBlockService placedBlocks;
    private final MMOItemsHook mmoItems;
    private final NamespacedKey playerDroppedKey;

    public GameplayListener(MDVQuestPlugin plugin, ProgressService progress, PlacedBlockService placedBlocks, MMOItemsHook mmoItems) {
        this.plugin = plugin;
        this.progress = progress;
        this.placedBlocks = placedBlocks;
        this.mmoItems = mmoItems;
        this.playerDroppedKey = new NamespacedKey(plugin, "player-dropped-item");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        progress.preload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        progress.unload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        placedBlocks.placed(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Material material = event.getBlock().getType();
        boolean wasPlaced = placedBlocks.consumeIfPlaced(event.getBlock());
        Player player = event.getPlayer();

        Map<String, String> data = new HashMap<>();
        data.put("natural", Boolean.toString(!wasPlaced));
        progress.report(player, ObjectiveType.MINE_BLOCK, material.name(), 1L, data);

        if (Tag.LOGS.isTagged(material)) {
            progress.report(player, ObjectiveType.CUT_LOG, material.name(), 1L, data);
        }

        if (isCrop(material)) {
            data.put("mature", Boolean.toString(isMatureCrop(event)));
            progress.report(player, ObjectiveType.HARVEST_CROP, material.name(), 1L, data);
        }
    }

    private boolean isCrop(Material material) {
        return material == Material.NETHER_WART || material == Material.WHEAT || material == Material.CARROTS
                || material == Material.POTATOES || material == Material.BEETROOTS;
    }

    private boolean isMatureCrop(BlockBreakEvent event) {
        if (!(event.getBlock().getBlockData() instanceof Ageable ageable)) return false;
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(placedBlocks::remove);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(placedBlocks::remove);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) return;
        if (dead instanceof Player victim) {
            if (!plugin.getConfig().getBoolean("anti-exploit.pvp.require-direct-killer", true) || isDirectPlayerKill(killer, victim)) {
                progress.reportPlayerKill(killer, victim);
            }
            return;
        }

        Optional<String> mythicId = plugin.getMythicMobsHook().mythicId(dead);
        if (mythicId.isPresent()) {
            progress.reportMythicKill(killer, dead.getUniqueId(), mythicId.get());
        } else {
            progress.report(killer, ObjectiveType.KILL_VANILLA_MOB, dead.getType().name(), 1L);
        }
    }


    private boolean isDirectPlayerKill(Player killer, Player victim) {
        if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent damage)) return false;
        Entity damager = damage.getDamager();
        if (damager instanceof Player player) return player.getUniqueId().equals(killer.getUniqueId());
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter.getUniqueId().equals(killer.getUniqueId());
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Recipe recipe = event.getRecipe();
        if (recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equalsIgnoreCase("mdvrecetas")) return;
        ItemStack result = recipe == null ? null : recipe.getResult();
        if (result == null || result.getType().isAir()) return;
        int operations = estimateCraftOperations(event);
        long produced = (long) Math.max(1, result.getAmount()) * operations;
        progress.report(player, ObjectiveType.CRAFT_VANILLA_ITEM, result.getType().name(), produced,
                Map.of("craft-operations", String.valueOf(operations)));
    }

    private int estimateCraftOperations(CraftItemEvent event) {
        if (!event.isShiftClick()) return 1;
        if (!(event.getInventory() instanceof CraftingInventory crafting)) return 1;
        int min = Integer.MAX_VALUE;
        for (ItemStack item : crafting.getMatrix()) {
            if (item == null || item.getType().isAir()) continue;
            min = Math.min(min, item.getAmount());
        }
        return min == Integer.MAX_VALUE ? 1 : Math.max(1, min);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Optional<MMOItemsHook.Identity> identity = mmoItems.identify(item);
        if (identity.isPresent()) {
            progress.report(event.getPlayer(), ObjectiveType.USE_CONSUMABLE, identity.get().combined(), 1L,
                    Map.of("mmo-type", identity.get().type(), "mmo-id", identity.get().id(), "source", "CONSUME_EVENT"));
        } else {
            progress.report(event.getPlayer(), ObjectiveType.USE_CONSUMABLE, item.getType().name(), 1L);
        }
    }


    /**
     * Cubre consumibles MMOItems instantaneos que se usan con click derecho y no disparan
     * PlayerItemConsumeEvent. Solo progresa si el stack realmente disminuyo o desaparecio.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMmoConsumableInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;
        ItemStack before = event.getItem();
        Optional<MMOItemsHook.Identity> identity = mmoItems.identify(before);
        if (identity.isEmpty() || !identity.get().type().equals("CONSUMABLE")) return;
        int beforeAmount = before == null ? 0 : before.getAmount();
        MMOItemsHook.Identity expected = identity.get();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            ItemStack after = hand == EquipmentSlot.OFF_HAND
                    ? event.getPlayer().getInventory().getItemInOffHand()
                    : event.getPlayer().getInventory().getItemInMainHand();
            Optional<MMOItemsHook.Identity> afterIdentity = mmoItems.identify(after);
            boolean sameItem = afterIdentity.isPresent() && afterIdentity.get().equals(expected);
            int afterAmount = after == null || after.getType().isAir() ? 0 : after.getAmount();
            if (sameItem && afterAmount >= beforeAmount) return;
            progress.report(event.getPlayer(), ObjectiveType.USE_CONSUMABLE, expected.combined(), 1L,
                    Map.of("mmo-type", expected.type(), "mmo-id", expected.id(), "source", "MMOITEMS_INTERACT"));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        event.getItemDrop().getPersistentDataContainer().set(playerDroppedKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getItem().getPersistentDataContainer().has(playerDroppedKey, PersistentDataType.BYTE)) return;
        ItemStack item = event.getItem().getItemStack();
        Optional<MMOItemsHook.Identity> identity = mmoItems.identify(item);
        if (identity.isEmpty()) return;
        int pickedUp = Math.max(0, item.getAmount() - event.getRemaining());
        if (pickedUp <= 0) return;
        progress.report(player, ObjectiveType.OBTAIN_MMOITEM, identity.get().combined(), pickedUp,
                Map.of("mmo-type", identity.get().type(), "mmo-id", identity.get().id(), "source", "PICKUP"));
    }
}
