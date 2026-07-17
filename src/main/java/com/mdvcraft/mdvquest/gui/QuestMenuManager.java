package com.mdvcraft.mdvquest.gui;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.MissionInstance;
import com.mdvcraft.mdvquest.model.ObjectiveDefinition;
import com.mdvcraft.mdvquest.model.ObjectiveType;
import com.mdvcraft.mdvquest.service.DeliveryService;
import com.mdvcraft.mdvquest.service.ProgressService;
import com.mdvcraft.mdvquest.service.RewardService;
import com.mdvcraft.mdvquest.service.RotationService;
import com.mdvcraft.mdvquest.util.ColorUtil;
import com.mdvcraft.mdvquest.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class QuestMenuManager implements Listener {
    private final MDVQuestPlugin plugin;
    private final RotationService rotations;
    private final ProgressService progress;
    private final RewardService rewards;
    private final DeliveryService deliveries;
    private final Map<UUID, MenuSession> sessions = new HashMap<>();
    private final NamespacedKey actionKey;
    private final NamespacedKey instanceKey;
    private final NamespacedKey objectiveKey;
    private final NamespacedKey pageKey;

    public QuestMenuManager(MDVQuestPlugin plugin, RotationService rotations, ProgressService progress,
                            RewardService rewards, DeliveryService deliveries) {
        this.plugin = plugin;
        this.rotations = rotations;
        this.progress = progress;
        this.rewards = rewards;
        this.deliveries = deliveries;
        this.actionKey = new NamespacedKey(plugin, "menu_action");
        this.instanceKey = new NamespacedKey(plugin, "instance_id");
        this.objectiveKey = new NamespacedKey(plugin, "objective_id");
        this.pageKey = new NamespacedKey(plugin, "page");
    }

    public void openMain(Player player) {
        openMain(player, 1);
    }

    public void openMain(Player player, int requestedPage) {
        List<MissionInstance> active = rotations.activeInstances();
        if (active.isEmpty()) {
            plugin.message(player, "no-active-missions", Map.of());
            return;
        }
        int size = normalizeSize(plugin.getConfig().getInt("menus.size", 54));
        List<Integer> slots = listSlots(size);
        int pages = Math.max(1, (int) Math.ceil(active.size() / (double) slots.size()));
        int page = Math.max(1, Math.min(pages, requestedPage));
        String title = plugin.getConfig().getString("menus.title", "&8Misiones");
        Inventory inventory = plugin.getSocialHook().createInventory(null, title, size, true);

        int start = (page - 1) * slots.size();
        long now = System.currentTimeMillis();
        for (int i = 0; i < slots.size() && start + i < active.size(); i++) {
            MissionInstance instance = active.get(start + i);
            inventory.setItem(slots.get(i), missionItem(player, instance, now));
        }

        if (page > 1) inventory.setItem(size - 9, actionItem(Material.ARROW, "&ePagina anterior", List.of(), "PREV", null, null, page - 1));
        else if (!plugin.getConfig().getString("menus.back-command", "").isBlank()) {
            inventory.setItem(size - 9, actionItem(Material.ARROW, "&eVolver", List.of("&7Regresa al menu principal."), "SOCIAL_BACK", null, null, 1));
        }
        inventory.setItem(size - 5, actionItem(Material.CLOCK, "&fPagina " + page + "/" + pages,
                List.of("&7Las misiones cambian por tiempo real."), "NONE", null, null, page));
        if (page < pages) inventory.setItem(size - 1, actionItem(Material.ARROW, "&aPagina siguiente", List.of(), "NEXT", null, null, page + 1));
        inventory.setItem(size - 4, actionItem(Material.BARRIER, "&cCerrar", List.of(), "CLOSE", null, null, page));

        sessions.put(player.getUniqueId(), new MenuSession(inventory, MenuType.MAIN, page, null));
        InventoryView openedView = player.openInventory(inventory);
        if (openedView == null) {
            sessions.remove(player.getUniqueId());
            return;
        }
        plugin.getSocialHook().sound(player, "open");
    }

    public void openDetail(Player player, MissionInstance instance) {
        if (instance == null || !instance.isActive(System.currentTimeMillis())) {
            plugin.message(player, "mission-expired", Map.of());
            openMain(player);
            return;
        }
        int size = 54;
        String rawTitle = plugin.getConfig().getString("menus.detail-title", "&8Mision: %mission%");
        String title = rawTitle.replace("%mission%", ColorUtil.strip(instance.definition().name()));
        Inventory inventory = plugin.getSocialHook().createInventory(null, title, size, true);
        inventory.setItem(4, missionItem(player, instance, System.currentTimeMillis()));

        int[] objectiveSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        List<ObjectiveDefinition> objectives = instance.definition().objectives();
        for (int i = 0; i < objectives.size() && i < objectiveSlots.length; i++) {
            ObjectiveDefinition objective = objectives.get(i);
            inventory.setItem(objectiveSlots[i], objectiveItem(player, instance, objective));
        }

        inventory.setItem(45, actionItem(Material.ARROW, "&eVolver", List.of(), "BACK", null, null, 1));
        if (progress.isMissionComplete(player, instance) && !progress.claimed(player, instance)) {
            inventory.setItem(49, actionItem(Material.LIME_DYE, "&a&lReclamar recompensa",
                    instance.definition().rewards().displayLore(), "CLAIM", instance.id(), null, 1));
        } else if (progress.claimed(player, instance)) {
            inventory.setItem(49, actionItem(Material.GRAY_DYE, "&7Recompensa reclamada", List.of(), "NONE", instance.id(), null, 1));
        }
        inventory.setItem(53, actionItem(Material.BARRIER, "&cCerrar", List.of(), "CLOSE", null, null, 1));

        sessions.put(player.getUniqueId(), new MenuSession(inventory, MenuType.DETAIL, 1, instance.id()));
        InventoryView openedView = player.openInventory(inventory);
        if (openedView == null) {
            sessions.remove(player.getUniqueId());
            return;
        }
        plugin.getSocialHook().sound(player, "open");
    }

    private ItemStack missionItem(Player player, MissionInstance instance, long now) {
        boolean complete = progress.isMissionComplete(player, instance);
        boolean claimed = progress.claimed(player, instance);
        Material material = Material.matchMaterial(instance.definition().icon());
        if (claimed) material = Material.GRAY_DYE;
        else if (complete) material = Material.LIME_DYE;
        if (material == null || material.isAir()) material = Material.PAPER;

        List<String> lore = new ArrayList<>(instance.definition().lore());
        lore.add("");
        for (ObjectiveDefinition objective : instance.definition().objectives()) {
            long value = progress.progress(player, instance, objective);
            String mark = value >= objective.amount() ? "&a✔" : "&7•";
            lore.add(mark + " &f" + objective.displayName() + " &7(" + value + "/" + objective.amount() + ")");
        }
        lore.add("");
        lore.add("&7Expira en: &f" + TimeUtil.remaining(instance.expiresAt(), now));
        if (!instance.definition().rewards().displayLore().isEmpty()) {
            lore.add("");
            lore.add("&eRecompensa:");
            lore.addAll(instance.definition().rewards().displayLore());
        }
        lore.add("");
        if (claimed) lore.add("&7Recompensa ya reclamada.");
        else if (complete) lore.add("&aClick izquierdo para reclamar.");
        else lore.add("&eClick para ver detalles.");
        lore.add("&8Click derecho: detalles.");
        return actionItem(material, instance.definition().name(), lore, complete && !claimed ? "CLAIM_OR_DETAIL" : "DETAIL", instance.id(), null, 1);
    }

    private ItemStack objectiveItem(Player player, MissionInstance instance, ObjectiveDefinition objective) {
        long value = progress.progress(player, instance, objective);
        boolean complete = value >= objective.amount();
        Material material = complete ? Material.LIME_DYE : objective.type().isDelivery() ? Material.CHEST : Material.PAPER;
        List<String> lore = new ArrayList<>();
        lore.add("&7Tipo: &f" + objective.type().name());
        lore.add("&7Progreso: &f" + value + "&7/&f" + objective.amount());
        if (objective.type().isDelivery() && !complete) {
            lore.add("");
            lore.add("&eClick para entregar los objetos disponibles.");
        } else if (complete) {
            lore.add("");
            lore.add("&aObjetivo completado.");
        }
        String action = objective.type().isDelivery() && !complete ? "DELIVER" : "NONE";
        return actionItem(material, (complete ? "&a" : "&e") + objective.displayName(), lore, action, instance.id(), objective.id(), 1);
    }

    private ItemStack actionItem(Material material, String name, List<String> lore, String action,
                                 String instanceId, String objectiveId, int page) {
        ItemStack item = plugin.getSocialHook().button(material, 1, name, lore, action, action.equals("CLOSE") ? "close" : "default");
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(actionKey, PersistentDataType.STRING, action);
        if (instanceId != null) pdc.set(instanceKey, PersistentDataType.STRING, instanceId);
        if (objectiveId != null) pdc.set(objectiveKey, PersistentDataType.STRING, objectiveId);
        pdc.set(pageKey, PersistentDataType.INTEGER, page);
        if (!item.setItemMeta(meta)) return item;
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) return;
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null || !event.getView().getTopInventory().equals(session.inventory())) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= session.inventory().getSize()) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(actionKey, PersistentDataType.STRING);
        if (action == null || action.equals("NONE")) return;
        String instanceId = pdc.get(instanceKey, PersistentDataType.STRING);
        String objectiveId = pdc.get(objectiveKey, PersistentDataType.STRING);
        Integer page = pdc.get(pageKey, PersistentDataType.INTEGER);

        switch (action) {
            case "CLOSE" -> {
                plugin.getSocialHook().sound(player, "close");
                player.closeInventory();
            }
            case "BACK" -> openMain(player, 1);
            case "SOCIAL_BACK" -> {
                String command = plugin.getConfig().getString("menus.back-command", "social");
                player.closeInventory();
                if (command != null && !command.isBlank()) player.performCommand(command.startsWith("/") ? command.substring(1) : command);
            }
            case "PREV", "NEXT" -> openMain(player, page == null ? 1 : page);
            case "DETAIL" -> openDetail(player, rotations.instance(instanceId));
            case "CLAIM_OR_DETAIL" -> {
                MissionInstance instance = rotations.instance(instanceId);
                if (event.isRightClick()) openDetail(player, instance);
                else {
                    rewards.claim(player, instance);
                    openMain(player, session.page());
                }
            }
            case "CLAIM" -> {
                MissionInstance instance = rotations.instance(instanceId);
                rewards.claim(player, instance);
                openDetail(player, instance);
            }
            case "DELIVER" -> {
                MissionInstance instance = rotations.instance(instanceId);
                if (instance == null) return;
                ObjectiveDefinition objective = instance.definition().objective(objectiveId);
                deliveries.deliver(player, instance, objective);
                openDetail(player, instance);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) return;
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null || !event.getView().getTopInventory().equals(session.inventory())) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < session.inventory().getSize())) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        HumanEntity viewer = event.getPlayer();
        if (!(viewer instanceof Player player)) return;
        MenuSession session = sessions.get(player.getUniqueId());
        if (session != null && event.getInventory().equals(session.inventory())) sessions.remove(player.getUniqueId());
    }

    private List<Integer> listSlots(int size) {
        List<Integer> configured = plugin.getConfig().getIntegerList("menus.list-slots");
        List<Integer> source = configured.isEmpty()
                ? List.of(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43)
                : configured;
        int contentLimit = Math.max(1, size - 9); // Reserva la ultima fila para navegacion.
        Set<Integer> valid = new LinkedHashSet<>();
        for (Integer slot : source) {
            if (slot != null && slot >= 0 && slot < contentLimit) valid.add(slot);
        }
        if (valid.isEmpty()) {
            for (int slot = 0; slot < contentLimit; slot++) valid.add(slot);
        }
        return new ArrayList<>(valid);
    }

    private int normalizeSize(int size) {
        int normalized = Math.max(9, Math.min(54, size));
        return ((normalized + 8) / 9) * 9;
    }

    private enum MenuType { MAIN, DETAIL }
    private record MenuSession(Inventory inventory, MenuType type, int page, String instanceId) { }
}
