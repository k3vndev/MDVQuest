package com.mdvcraft.mdvquest.gui;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.MissionDefinition;
import com.mdvcraft.mdvquest.model.MissionInstance;
import com.mdvcraft.mdvquest.model.ObjectiveDefinition;
import com.mdvcraft.mdvquest.model.RewardDefinition;
import com.mdvcraft.mdvquest.service.DeliveryService;
import com.mdvcraft.mdvquest.service.ProgressService;
import com.mdvcraft.mdvquest.service.RewardService;
import com.mdvcraft.mdvquest.service.RotationService;
import com.mdvcraft.mdvquest.util.ColorUtil;
import com.mdvcraft.mdvquest.util.ItemUtil;
import com.mdvcraft.mdvquest.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** GUI pública de misiones. Toda la lógica visual común usa MDVSocial cuando está disponible. */
public final class QuestMenuManager implements Listener {
    private static final int[] CATEGORY_SLOTS = {9, 18, 27, 36};
    private static final int[] MISSION_SLOTS = {
            10,11,12,13,14,15,16,17,
            19,20,21,22,23,24,25,26,
            28,29,30,31,32,33,34,35,
            37,38,39,40,41,42,43,44
    };
    private static final int[] OBJECTIVE_SLOTS = {18,19,20,21,22,23,24,25,26};
    private static final int[] REWARD_SLOTS = {28,29,30,31,32,33,34,37,38,39};

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
    private final NamespacedKey groupKey;

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
        this.groupKey = new NamespacedKey(plugin, "duration_group");
    }

    public void openMain(Player player) {
        openMain(player, DurationGroup.ONE_DAY, 1);
    }

    public void openMain(Player player, int requestedPage) {
        MenuSession current = sessions.get(player.getUniqueId());
        DurationGroup group = current == null ? DurationGroup.ONE_DAY : current.group();
        openMain(player, group, requestedPage);
    }

    public void openMain(Player player, DurationGroup group, int requestedPage) {
        List<MissionInstance> all = rotations.activeInstances();
        if (all.isEmpty()) {
            plugin.message(player, "no-active-missions", Map.of());
            return;
        }

        List<MissionInstance> filtered = all.stream()
                .filter(instance -> group.accepts(plugin.getRegistry().durationDays(instance.definition())))
                .sorted(Comparator.comparingLong(MissionInstance::expiresAt).thenComparing(i -> i.definition().id()))
                .toList();

        int pages = Math.max(1, (int) Math.ceil(filtered.size() / (double) MISSION_SLOTS.length));
        int page = Math.max(1, Math.min(pages, requestedPage));
        String title = plugin.getConfig().getString("menus.title", "&8Misiones");
        Inventory inventory = plugin.getSocialHook().createInventory(null, title, 54, true);

        DurationGroup[] groups = DurationGroup.values();
        for (int i = 0; i < groups.length; i++) {
            DurationGroup option = groups[i];
            long count = all.stream().filter(instance -> option.accepts(plugin.getRegistry().durationDays(instance.definition()))).count();
            inventory.setItem(CATEGORY_SLOTS[i], categoryItem(option, option == group, count));
        }

        int start = (page - 1) * MISSION_SLOTS.length;
        long now = System.currentTimeMillis();
        for (int i = 0; i < MISSION_SLOTS.length && start + i < filtered.size(); i++) {
            inventory.setItem(MISSION_SLOTS[i], missionItem(player, filtered.get(start + i), now));
        }

        if (pages > 1 && page > 1) {
            inventory.setItem(47, actionItem(Material.ARROW, "&ePágina anterior", List.of(), "MAIN_PAGE", null, null, page - 1, group));
        }
        inventory.setItem(49, actionItem(Material.CLOCK, "&fPágina " + page + "/" + pages,
                List.of("&7Grupo: &f" + group.display(), "&7Las rotaciones usan días reales."), "NONE", null, null, page, group));
        if (pages > 1 && page < pages) {
            inventory.setItem(51, actionItem(Material.ARROW, "&aPágina siguiente", List.of(), "MAIN_PAGE", null, null, page + 1, group));
        }
        inventory.setItem(45, backHead("SOCIAL_BACK", "&eVolver", List.of("&7Regresa al menú anterior."), group));
        inventory.setItem(53, actionItem(Material.BARRIER, "&cCerrar", List.of(), "CLOSE", null, null, page, group));

        sessions.put(player.getUniqueId(), new MenuSession(inventory, MenuType.MAIN, page, group, null, 1));
        InventoryView view = player.openInventory(inventory);
        if (view == null) sessions.remove(player.getUniqueId());
        else plugin.getSocialHook().sound(player, "open");
    }

    public void openDetail(Player player, MissionInstance instance) {
        openDetail(player, instance, 1);
    }

    private void openDetail(Player player, MissionInstance instance, int rewardPage) {
        if (instance == null || !instance.isActive(System.currentTimeMillis())) {
            plugin.message(player, "mission-expired", Map.of());
            openMain(player);
            return;
        }
        if (progress.isMissionComplete(player, instance) && !progress.claimed(player, instance)) {
            rewards.claim(player, instance);
            return;
        }

        String rawTitle = plugin.getConfig().getString("menus.detail-title", "&8Misión: %mission%");
        String title = rawTitle.replace("%mission%", ColorUtil.strip(instance.definition().name()));
        Inventory inventory = plugin.getSocialHook().createInventory(null, title, 54, true);
        inventory.setItem(4, missionItem(player, instance, System.currentTimeMillis()));

        List<ObjectiveDefinition> objectives = instance.definition().objectives();
        for (int i = 0; i < OBJECTIVE_SLOTS.length && i < objectives.size(); i++) {
            inventory.setItem(OBJECTIVE_SLOTS[i], objectiveItem(player, instance, objectives.get(i)));
        }

        List<ItemStack> rewardPreview = rewardPreview(instance.definition().rewards());
        int rewardPages = Math.max(1, (int) Math.ceil(rewardPreview.size() / (double) REWARD_SLOTS.length));
        int actualRewardPage = Math.max(1, Math.min(rewardPages, rewardPage));
        int start = (actualRewardPage - 1) * REWARD_SLOTS.length;
        for (int i = 0; i < REWARD_SLOTS.length && start + i < rewardPreview.size(); i++) {
            inventory.setItem(REWARD_SLOTS[i], rewardPreview.get(start + i));
        }
        if (rewardPages > 1 && actualRewardPage > 1) {
            inventory.setItem(36, actionItem(Material.ARROW, "&eRecompensas anteriores", List.of(), "REWARD_PAGE", instance.id(), null, actualRewardPage - 1, null));
        }
        if (rewardPages > 1 && actualRewardPage < rewardPages) {
            inventory.setItem(44, actionItem(Material.ARROW, "&aMás recompensas", List.of(), "REWARD_PAGE", instance.id(), null, actualRewardPage + 1, null));
        }

        DurationGroup group = groupFor(instance);
        inventory.setItem(45, backHead("BACK", "&eVolver", List.of("&7Regresa al catálogo."), group));
        if (progress.claimed(player, instance)) {
            inventory.setItem(49, actionItem(Material.GRAY_DYE, "&7Recompensa reclamada", List.of("&7Ya recibiste esta recompensa."), "NONE", instance.id(), null, 1, group));
        }
        inventory.setItem(53, actionItem(Material.BARRIER, "&cCerrar", List.of(), "CLOSE", null, null, 1, group));

        sessions.put(player.getUniqueId(), new MenuSession(inventory, MenuType.DETAIL, 1, group, instance.id(), actualRewardPage));
        InventoryView view = player.openInventory(inventory);
        if (view == null) sessions.remove(player.getUniqueId());
        else plugin.getSocialHook().sound(player, "open");
    }

    private ItemStack categoryItem(DurationGroup group, boolean selected, long count) {
        Material material = selected ? Material.WRITABLE_BOOK : group.material();
        List<String> lore = new ArrayList<>();
        lore.add("&7Misiones activas: &f" + count);
        lore.add("");
        lore.add(selected ? "&aCategoría seleccionada." : "&eClick para consultar.");
        ItemStack item = actionItem(material, (selected ? "&a" : "&e") + group.display(), lore,
                "GROUP", null, null, 1, group);
        if (selected) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack missionItem(Player player, MissionInstance instance, long now) {
        boolean complete = progress.isMissionComplete(player, instance);
        boolean claimed = progress.claimed(player, instance);
        ItemStack item;
        if (claimed) item = new ItemStack(Material.GRAY_DYE);
        else if (complete) item = new ItemStack(Material.LIME_WOOL);
        else {
            item = instance.definition().iconItem();
            if (item == null) {
                Material material = Material.matchMaterial(instance.definition().icon());
                item = new ItemStack(material == null || material.isAir() ? Material.PAPER : material);
            }
        }
        item = ItemUtil.hideNativeTooltip(item);
        item.setAmount(1);

        List<String> lore = new ArrayList<>(instance.definition().lore());
        lore.add("");
        lore.add("&eObjetivos:");
        for (ObjectiveDefinition objective : instance.definition().objectives()) {
            long value = progress.progress(player, instance, objective);
            lore.add((value >= objective.amount() ? "&a✔ " : "&7• ") + "&f" + objective.displayName()
                    + " &7(" + value + "/" + objective.amount() + ")");
        }
        appendRewardLore(lore, instance.definition().rewards());
        lore.add("");
        lore.add("&7Expira en: &f" + TimeUtil.remaining(instance.expiresAt(), now));
        lore.add("");
        if (claimed) lore.add("&7Recompensa ya reclamada.");
        else if (complete) lore.add("&a&lClick para recibir la recompensa.");
        else lore.add("&eClick para ver más detalles.");

        return decorate(item, instance.definition().name(), lore,
                complete && !claimed ? "CLAIM" : "DETAIL", instance.id(), null, 1, groupFor(instance));
    }

    private ItemStack objectiveItem(Player player, MissionInstance instance, ObjectiveDefinition objective) {
        long value = progress.progress(player, instance, objective);
        boolean complete = value >= objective.amount();
        Material material = complete ? Material.LIME_DYE : objective.type().isDelivery() ? Material.CHEST : Material.PAPER;
        List<String> lore = new ArrayList<>();
        lore.add("&7Progreso: &f" + value + "&7/&f" + objective.amount());
        if (objective.type().isDelivery() && !complete) {
            lore.add("");
            lore.add("&eClick para entregar objetos.");
        } else if (complete) {
            lore.add("");
            lore.add("&aObjetivo completado.");
        }
        String action = objective.type().isDelivery() && !complete ? "DELIVER" : "NONE";
        return actionItem(material, (complete ? "&a" : "&e") + objective.displayName(), lore,
                action, instance.id(), objective.id(), 1, groupFor(instance));
    }

    private List<ItemStack> rewardPreview(RewardDefinition reward) {
        List<ItemStack> result = new ArrayList<>();
        for (RewardDefinition.ExperienceReward experience : reward.experience()) {
            String target = experience.profession().equalsIgnoreCase("main") ? "nivel principal" : "profesión " + experience.profession();
            result.add(actionItem(Material.EXPERIENCE_BOTTLE, "&bExperiencia",
                    List.of("&7Recibirás &f" + experience.amount() + " EXP", "&7Para: &f" + target),
                    "NONE", null, null, 1, null));
        }
        for (ItemStack item : rewards.previewItems(reward)) {
            ItemStack preview = ItemUtil.hideNativeTooltip(item);
            ItemMeta meta = preview.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add("&7Cantidad: &f" + item.getAmount());
                meta.setLore(ColorUtil.color(lore));
                preview.setItemMeta(meta);
            }
            result.add(preview);
        }
        if (result.isEmpty()) {
            result.add(actionItem(Material.CHEST, "&eRecompensas",
                    reward.displayLore().isEmpty() ? List.of("&7Recompensa ejecutada por comandos.") : reward.displayLore(),
                    "NONE", null, null, 1, null));
        }
        return result;
    }

    private void appendRewardLore(List<String> lore, RewardDefinition reward) {
        lore.add("");
        lore.add("&eRecompensas:");
        if (!reward.displayLore().isEmpty()) lore.addAll(reward.displayLore());
        for (RewardDefinition.ExperienceReward exp : reward.experience()) {
            lore.add("&7• &b" + exp.amount() + " EXP &f" + exp.profession());
        }
        for (RewardDefinition.VanillaItemReward item : reward.vanillaItems()) lore.add("&7• &f" + item.amount() + "x " + item.material());
        for (RewardDefinition.MmoItemReward item : reward.mmoItems()) lore.add("&7• &d" + item.amount() + "x " + item.id());
        for (RewardDefinition.MythicItemReward item : reward.mythicItems()) lore.add("&7• &5" + item.amount() + "x " + item.id());
        for (RewardDefinition.ExactItemReward item : reward.exactItems()) lore.add("&7• &f" + item.amount() + "x " + displayName(item.item()));
        if (reward.empty() && reward.displayLore().isEmpty()) lore.add("&7• Sin recompensa configurada");
    }

    private String displayName(ItemStack item) {
        if (item == null) return "Objeto";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return ColorUtil.strip(meta.getDisplayName());
        return item.getType().name();
    }

    private DurationGroup groupFor(MissionInstance instance) {
        int days = plugin.getRegistry().durationDays(instance.definition());
        for (DurationGroup group : DurationGroup.values()) if (group.accepts(days)) return group;
        return DurationGroup.ONE_DAY;
    }

    private ItemStack backHead(String action, String name, List<String> lore, DurationGroup group) {
        ItemStack item = ItemUtil.backHead(plugin, name, lore);
        return tag(item, action, null, null, 1, group);
    }

    private ItemStack actionItem(Material material, String name, List<String> lore, String action,
                                 String instanceId, String objectiveId, int page, DurationGroup group) {
        ItemStack item = plugin.getSocialHook().button(material, 1, name, lore, action,
                action.equals("CLOSE") ? "close" : "default");
        return tag(item, action, instanceId, objectiveId, page, group);
    }

    private ItemStack decorate(ItemStack item, String name, List<String> lore, String action,
                               String instanceId, String objectiveId, int page, DurationGroup group) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(name));
            meta.setLore(ColorUtil.color(lore));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return tag(item, action, instanceId, objectiveId, page, group);
    }

    private ItemStack tag(ItemStack item, String action, String instanceId, String objectiveId, int page, DurationGroup group) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(actionKey, PersistentDataType.STRING, action);
        if (instanceId != null) pdc.set(instanceKey, PersistentDataType.STRING, instanceId);
        if (objectiveId != null) pdc.set(objectiveKey, PersistentDataType.STRING, objectiveId);
        pdc.set(pageKey, PersistentDataType.INTEGER, page);
        if (group != null) pdc.set(groupKey, PersistentDataType.STRING, group.name());
        item.setItemMeta(meta);
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
        DurationGroup group = parseGroup(pdc.get(groupKey, PersistentDataType.STRING), session.group());

        switch (action) {
            case "CLOSE" -> {
                plugin.getSocialHook().sound(player, "close");
                player.closeInventory();
            }
            case "BACK" -> openMain(player, group, 1);
            case "SOCIAL_BACK" -> {
                String command = plugin.getConfig().getString("menus.back-command", "social");
                player.closeInventory();
                if (command != null && !command.isBlank()) player.performCommand(command.startsWith("/") ? command.substring(1) : command);
            }
            case "GROUP" -> openMain(player, group, 1);
            case "MAIN_PAGE" -> openMain(player, group, page == null ? 1 : page);
            case "DETAIL" -> openDetail(player, rotations.instance(instanceId));
            case "CLAIM" -> rewards.claim(player, rotations.instance(instanceId));
            case "REWARD_PAGE" -> openDetail(player, rotations.instance(instanceId), page == null ? 1 : page);
            case "DELIVER" -> {
                MissionInstance instance = rotations.instance(instanceId);
                if (instance == null) return;
                ObjectiveDefinition objective = instance.definition().objective(objectiveId);
                deliveries.deliver(player, instance, objective);
                openDetail(player, instance, session.rewardPage());
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

    private DurationGroup parseGroup(String raw, DurationGroup fallback) {
        if (raw == null) return fallback == null ? DurationGroup.ONE_DAY : fallback;
        try { return DurationGroup.valueOf(raw); }
        catch (IllegalArgumentException ignored) { return fallback == null ? DurationGroup.ONE_DAY : fallback; }
    }

    private enum MenuType { MAIN, DETAIL }
    private record MenuSession(Inventory inventory, MenuType type, int page, DurationGroup group,
                               String instanceId, int rewardPage) { }
}
