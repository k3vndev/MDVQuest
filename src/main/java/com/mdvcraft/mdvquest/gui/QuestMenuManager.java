package com.mdvcraft.mdvquest.gui;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.MissionInstance;
import com.mdvcraft.mdvquest.model.ObjectiveDefinition;
import com.mdvcraft.mdvquest.model.RewardDefinition;
import com.mdvcraft.mdvquest.model.RotationDefinition;
import com.mdvcraft.mdvquest.service.AccessService;
import com.mdvcraft.mdvquest.service.DeliveryService;
import com.mdvcraft.mdvquest.service.ProgressService;
import com.mdvcraft.mdvquest.service.RewardService;
import com.mdvcraft.mdvquest.service.RotationService;
import com.mdvcraft.mdvquest.util.ColorUtil;
import com.mdvcraft.mdvquest.util.ItemDisplayUtil;
import com.mdvcraft.mdvquest.util.ItemUtil;
import com.mdvcraft.mdvquest.util.TimeUtil;
import net.kyori.adventure.text.Component;
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
    private static final int[] DEFAULT_CATEGORY_SLOTS = {9, 18, 27, 36};
    private static final int[] DEFAULT_MISSION_SLOTS = {
            11,12,13,14,15,16,17,
            20,21,22,23,24,25,26,
            29,30,31,32,33,34,35,
            38,39,40,41,42,43,44
    };
    private static final int[] DEFAULT_SEPARATOR_SLOTS = {10, 19, 28, 37};
    private static final int[] DEFAULT_OBJECTIVE_SLOTS = {10,11,12,13,14,15,16};
    private static final int[] DEFAULT_REWARD_SLOTS = {29,30,31,32,33};
    private static final int[] DEFAULT_REWARD_BORDER_SLOTS = {
            19,20,21,22,23,24,25,
            28,34,
            37,38,39,40,41,42,43
    };

    private final MDVQuestPlugin plugin;
    private final RotationService rotations;
    private final ProgressService progress;
    private final RewardService rewards;
    private final DeliveryService deliveries;
    private final AccessService access;
    private final Map<UUID, MenuSession> sessions = new HashMap<>();
    private final NamespacedKey actionKey;
    private final NamespacedKey instanceKey;
    private final NamespacedKey objectiveKey;
    private final NamespacedKey pageKey;
    private final NamespacedKey groupKey;

    public QuestMenuManager(MDVQuestPlugin plugin, RotationService rotations, ProgressService progress,
                            RewardService rewards, DeliveryService deliveries, AccessService access) {
        this.plugin = plugin;
        this.rotations = rotations;
        this.progress = progress;
        this.rewards = rewards;
        this.deliveries = deliveries;
        this.access = access;
        this.actionKey = new NamespacedKey(plugin, "menu_action");
        this.instanceKey = new NamespacedKey(plugin, "instance_id");
        this.objectiveKey = new NamespacedKey(plugin, "objective_id");
        this.pageKey = new NamespacedKey(plugin, "page");
        this.groupKey = new NamespacedKey(plugin, "duration_group");
    }

    /** Compatibilidad: el menú público normal siempre abre la variante de consulta. */
    public void openMain(Player player) {
        openViewer(player);
    }

    public void openViewer(Player player) {
        openMain(player, MenuMode.VIEW_ONLY, DurationGroup.ONE_DAY, 1);
    }

    public void openInteractive(Player player) {
        openMain(player, MenuMode.INTERACTIVE, DurationGroup.ONE_DAY, 1);
    }

    public void openMain(Player player, int requestedPage) {
        MenuSession current = sessions.get(player.getUniqueId());
        DurationGroup group = current == null ? DurationGroup.ONE_DAY : current.group();
        MenuMode mode = current == null ? MenuMode.VIEW_ONLY : current.mode();
        openMain(player, mode, group, requestedPage);
    }

    public void openViewer(Player player, int requestedPage) {
        MenuSession current = sessions.get(player.getUniqueId());
        DurationGroup group = current == null ? DurationGroup.ONE_DAY : current.group();
        openMain(player, MenuMode.VIEW_ONLY, group, requestedPage);
    }

    public void openInteractive(Player player, int requestedPage) {
        MenuSession current = sessions.get(player.getUniqueId());
        DurationGroup group = current == null ? DurationGroup.ONE_DAY : current.group();
        openMain(player, MenuMode.INTERACTIVE, group, requestedPage);
    }

    private void openMain(Player player, MenuMode mode, DurationGroup group, int requestedPage) {
        List<MissionInstance> all = rotations.activeInstances();
        if (all.isEmpty()) {
            plugin.message(player, "no-active-missions", Map.of());
            return;
        }

        int[] categorySlots = modeSlots(mode, "main.category-slots", DEFAULT_CATEGORY_SLOTS);
        int[] missionSlots = modeSlots(mode, "main.mission-slots", DEFAULT_MISSION_SLOTS);
        if (missionSlots.length == 0) missionSlots = DEFAULT_MISSION_SLOTS;

        List<MissionInstance> filtered = all.stream()
                .filter(instance -> group.accepts(plugin.getRegistry().durationDays(instance.definition())))
                .filter(instance -> mode == MenuMode.INTERACTIVE || progress.accepted(player, instance))
                .sorted(Comparator
                        .comparingInt((MissionInstance i) -> i.accessTier().level())
                        .thenComparingLong(MissionInstance::expiresAt)
                        .thenComparing(i -> i.definition().id()))
                .toList();

        int pages = Math.max(1, (int) Math.ceil(filtered.size() / (double) missionSlots.length));
        int page = Math.max(1, Math.min(pages, requestedPage));
        int size = modeMenuSize(mode, "main.size", 54);
        String legacyTitle = plugin.getConfig().getString("menus.title", "&8Misiones");
        int selectedAccepted = progress.acceptedCount(player, group);
        int selectedLimit = progress.contractLimit(player, group);
        String title = modeText(mode, "main.title", legacyTitle, Map.of(
                "accepted", String.valueOf(selectedAccepted),
                "limit", String.valueOf(selectedLimit),
                "category", group.display()
        ));
        Inventory inventory = plugin.getSocialHook().createInventory(null, title, size,
                modeBoolean(mode, "main.fill", true));

        DurationGroup[] groups = DurationGroup.values();
        long now = System.currentTimeMillis();
        for (int i = 0; i < groups.length && i < categorySlots.length; i++) {
            int slot = categorySlots[i];
            if (!validSlot(slot, size)) continue;
            DurationGroup option = groups[i];
            List<MissionInstance> categoryMissions = all.stream()
                    .filter(instance -> option.accepts(plugin.getRegistry().durationDays(instance.definition())))
                    .toList();
            long completed = categoryMissions.stream().filter(instance -> progress.accepted(player, instance) && progress.isMissionComplete(player, instance)).count();
            int accepted = progress.acceptedCount(player, option);
            int limit = progress.contractLimit(player, option);
            long nextReset = nextResetAt(option, now);
            inventory.setItem(slot, categoryItem(mode, option, option == group, completed, categoryMissions.size(), accepted, limit, nextReset, now));
        }

        int[] separatorSlots = modeSlots(mode, "main.separator-slots", DEFAULT_SEPARATOR_SLOTS);
        for (int i = 0; i < separatorSlots.length && i < groups.length; i++) {
            int slot = separatorSlots[i];
            if (!validSlot(slot, size)) continue;
            inventory.setItem(slot, separatorItem(mode, groups[i] == group));
        }

        int start = (page - 1) * missionSlots.length;
        for (int i = 0; i < missionSlots.length && start + i < filtered.size(); i++) {
            int slot = missionSlots[i];
            if (validSlot(slot, size)) inventory.setItem(slot, missionItem(player, filtered.get(start + i), now, mode));
        }

        if (pages > 1 && page > 1) {
            int slot = modeInt(mode, "main.previous-page-slot", 45);
            if (validSlot(slot, size)) inventory.setItem(slot,
                    pageArrow(mode, false, "MAIN_PAGE", null, page - 1, page, pages, group));
        }
        if (pages > 1 && page < pages) {
            int slot = modeInt(mode, "main.next-page-slot", 53);
            if (validSlot(slot, size)) inventory.setItem(slot,
                    pageArrow(mode, true, "MAIN_PAGE", null, page + 1, page, pages, group));
        }

        int backSlot = modeInt(mode, "main.back-slot", 49);
        if (validSlot(backSlot, size)) {
            inventory.setItem(backSlot, backHead("SOCIAL_BACK",
                    modeText(mode, "main.back-button.name", "&eVolver", Map.of()),
                    modeLore(mode, "main.back-button.lore", List.of("&7Regresa al menú de /social."), Map.of()), group));
        }

        sessions.put(player.getUniqueId(), new MenuSession(inventory, MenuType.MAIN, mode, page, group, null, 1));
        InventoryView view = player.openInventory(inventory);
        if (view == null) sessions.remove(player.getUniqueId());
        else plugin.getSocialHook().sound(player, "open");
    }

    public void openDetail(Player player, MissionInstance instance) {
        MenuSession current = sessions.get(player.getUniqueId());
        if (current != null && current.mode() != MenuMode.INTERACTIVE) return;
        int returnPage = current != null && current.type() == MenuType.MAIN ? current.mainPage() : 1;
        openDetail(player, instance, 1, returnPage);
    }

    private void openDetail(Player player, MissionInstance instance, int rewardPage, int returnPage) {
        if (instance == null || !instance.isActive(System.currentTimeMillis())) {
            plugin.message(player, "mission-expired", Map.of());
            openInteractive(player);
            return;
        }
        if (progress.isMissionComplete(player, instance) && !progress.claimed(player, instance)
                && access.hasAccess(player, instance.accessTier())) {
            rewards.claim(player, instance);
            return;
        }

        int size = modeMenuSize(MenuMode.INTERACTIVE, "detail.size", 54);
        Map<String, String> titleValues = Map.of("mission", ColorUtil.strip(instance.definition().name()));
        String title = modeText(MenuMode.INTERACTIVE, "detail.title",
                plugin.getConfig().getString("menus.detail-title", "&8Misión: %mission%"), titleValues);
        Inventory inventory = plugin.getSocialHook().createInventory(null, title, size,
                modeBoolean(MenuMode.INTERACTIVE, "detail.fill", true));

        int iconSlot = modeInt(MenuMode.INTERACTIVE, "detail.mission-icon-slot", 4);
        if (validSlot(iconSlot, size)) inventory.setItem(iconSlot,
                missionItem(player, instance, System.currentTimeMillis(), MenuMode.INTERACTIVE));

        int[] objectiveSlots = modeSlots(MenuMode.INTERACTIVE, "detail.objective-slots", DEFAULT_OBJECTIVE_SLOTS);
        List<ObjectiveDefinition> objectives = instance.definition().objectives();
        for (int i = 0; i < objectiveSlots.length && i < objectives.size(); i++) {
            int slot = objectiveSlots[i];
            if (validSlot(slot, size)) inventory.setItem(slot, objectiveItem(player, instance, objectives.get(i)));
        }

        boolean borderEnabled = modeBoolean(MenuMode.INTERACTIVE, "detail.reward-border.enabled", true);
        if (borderEnabled) {
            int[] borderSlots = modeSlots(MenuMode.INTERACTIVE, "detail.reward-border.slots", DEFAULT_REWARD_BORDER_SLOTS);
            Material borderMaterial = modeMaterial(MenuMode.INTERACTIVE, "detail.reward-border.material", Material.LIME_STAINED_GLASS_PANE);
            String borderName = modeText(MenuMode.INTERACTIVE, "detail.reward-border.name", " ", Map.of());
            ItemStack border = actionItem(borderMaterial, borderName, List.of(), "NONE", null, null, 1, null);
            for (int slot : borderSlots) if (validSlot(slot, size)) inventory.setItem(slot, border);
        }

        int[] rewardSlots = modeSlots(MenuMode.INTERACTIVE, "detail.reward-slots", DEFAULT_REWARD_SLOTS);
        if (rewardSlots.length == 0) rewardSlots = DEFAULT_REWARD_SLOTS;
        List<ItemStack> rewardPreview = rewardPreview(instance.definition().rewards());
        int rewardPages = Math.max(1, (int) Math.ceil(rewardPreview.size() / (double) rewardSlots.length));
        int actualRewardPage = Math.max(1, Math.min(rewardPages, rewardPage));
        int start = (actualRewardPage - 1) * rewardSlots.length;
        for (int i = 0; i < rewardSlots.length && start + i < rewardPreview.size(); i++) {
            int slot = rewardSlots[i];
            if (validSlot(slot, size)) inventory.setItem(slot, rewardPreview.get(start + i));
        }

        DurationGroup group = groupFor(instance);
        if (rewardPages > 1 && actualRewardPage > 1) {
            int slot = modeInt(MenuMode.INTERACTIVE, "detail.previous-reward-page-slot", 45);
            if (validSlot(slot, size)) inventory.setItem(slot,
                    pageArrow(MenuMode.INTERACTIVE, false, "REWARD_PAGE", instance.id(), actualRewardPage - 1, actualRewardPage, rewardPages, group));
        }
        if (rewardPages > 1 && actualRewardPage < rewardPages) {
            int slot = modeInt(MenuMode.INTERACTIVE, "detail.next-reward-page-slot", 53);
            if (validSlot(slot, size)) inventory.setItem(slot,
                    pageArrow(MenuMode.INTERACTIVE, true, "REWARD_PAGE", instance.id(), actualRewardPage + 1, actualRewardPage, rewardPages, group));
        }

        int backSlot = modeInt(MenuMode.INTERACTIVE, "detail.back-slot", 49);
        if (validSlot(backSlot, size)) {
            inventory.setItem(backSlot, backHead("BACK",
                    modeText(MenuMode.INTERACTIVE, "detail.back-button.name", "&eVolver", Map.of()),
                    modeLore(MenuMode.INTERACTIVE, "detail.back-button.lore", List.of("&7Regresa al catálogo anterior."), Map.of()), group));
        }

        sessions.put(player.getUniqueId(), new MenuSession(inventory, MenuType.DETAIL, MenuMode.INTERACTIVE,
                returnPage, group, instance.id(), actualRewardPage));
        InventoryView view = player.openInventory(inventory);
        if (view == null) sessions.remove(player.getUniqueId());
        else plugin.getSocialHook().sound(player, "open");
    }

    private ItemStack categoryItem(MenuMode mode, DurationGroup group, boolean selected, long completed, long total,
                                   int accepted, int limit, long nextReset, long now) {
        String base = "main.categories." + group.configKey();
        Material configured = modeMaterial(mode, base + ".material", group.material());
        String name = modeText(mode, base + ".name", "&e" + group.display(), Map.of());
        String remaining = nextReset <= 0 ? "Sin rotación programada" : TimeUtil.remaining(nextReset, now);
        Map<String, String> values = Map.of(
                "completed", String.valueOf(completed),
                "total", String.valueOf(total),
                "accepted", String.valueOf(accepted),
                "limit", String.valueOf(limit),
                "remaining", remaining
        );
        List<String> configuredLore = modeLore(mode, base + ".lore", List.of(
                "&7Contratos aceptados: &f%accepted%/%limit%",
                "&7Completadas: &f%completed%/%total%",
                "&7Nuevas misiones en: &f%remaining%",
                "",
                selected ? "&aCategoría seleccionada." : "&eClick para consultar."
        ), values);
        ItemStack item = actionItem(configured, name, configuredLore, "GROUP", null, null, 1, group);
        if (selected && modeBoolean(mode, "main.selected-category-glow", true)) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack separatorItem(MenuMode mode, boolean selected) {
        Material material = modeMaterial(mode, selected
                        ? "main.separator.selected-material"
                        : "main.separator.material",
                selected ? Material.PURPLE_STAINED_GLASS_PANE : Material.BROWN_STAINED_GLASS_PANE);
        String name = modeText(mode, selected
                        ? "main.separator.selected-name"
                        : "main.separator.name",
                " ", Map.of());
        return actionItem(material, name, List.of(), "NONE", null, null, 1, null);
    }

    private ItemStack missionItem(Player player, MissionInstance instance, long now, MenuMode mode) {
        boolean acceptedMission = progress.accepted(player, instance);
        boolean complete = progress.isMissionComplete(player, instance);
        boolean claimed = progress.claimed(player, instance);
        boolean locked = !access.hasAccess(player, instance.accessTier());
        ItemStack item;
        if (claimed) item = new ItemStack(modeMaterial(mode, "main.mission-state.claimed-material", Material.GRAY_DYE));
        else if (locked) item = new ItemStack(access.lockedMaterial(instance.accessTier()));
        else if (complete) item = new ItemStack(modeMaterial(mode, "main.mission-state.completed-material", Material.LIME_STAINED_GLASS_PANE));
        else {
            item = instance.definition().iconItem();
            if (item == null) {
                Material configured = Material.matchMaterial(instance.definition().icon());
                item = new ItemStack(configured == null || configured.isAir() ? Material.PAPER : configured);
            }
        }
        item = ItemUtil.hideNativeTooltip(item);
        item.setAmount(1);

        List<Component> lore = ItemDisplayUtil.legacyLines(instance.definition().lore());
        lore.add(Component.empty());
        lore.add(ItemDisplayUtil.legacy(modeText(mode, "main.mission-lore.objectives-header", "&eObjetivos:", Map.of())));
        for (ObjectiveDefinition objective : instance.definition().objectives()) {
            long value = progress.progress(player, instance, objective);
            boolean objectiveComplete = value >= objective.amount();
            String state = modeText(mode, objectiveComplete
                    ? "main.mission-lore.objective-complete-state"
                    : "main.mission-lore.objective-pending-state",
                    objectiveComplete ? "&a✔ " : "&7• ", Map.of());
            Map<String, String> values = Map.of(
                    "state", state,
                    "objective", objective.displayName(),
                    "progress", String.valueOf(value),
                    "required", String.valueOf(objective.amount())
            );
            lore.add(ItemDisplayUtil.legacy(modeText(mode, "main.mission-lore.objective-format",
                    "%state%&f%objective% &7(%progress%/%required%)", values)));
        }
        appendRewardLore(mode, lore, instance.definition().rewards());
        if (!locked && instance.accessTier() != com.mdvcraft.mdvquest.model.AccessTier.NORMAL) {
            lore.add(Component.empty());
            lore.add(ItemDisplayUtil.legacy(modeText(mode, "main.access." + instance.accessTier().key() + "-line",
                    instance.accessTier() == com.mdvcraft.mdvquest.model.AccessTier.VIP1
                            ? "&b● Misión VIP"
                            : "&e● Misión VIP 2", Map.of(
                            "rank", access.displayName(instance.accessTier()),
                            "permission", access.permission(instance.accessTier()),
                            "pool", instance.accessTier().key()
                    ))));
        }
        if (locked) {
            lore.add(Component.empty());
            lore.add(ItemDisplayUtil.legacy(modeText(mode, "main.access.locked-line",
                    "&b● Necesitas el rango &f%rank% &bpara reclamar esta recompensa.", Map.of(
                            "rank", access.displayName(instance.accessTier()),
                            "permission", access.permission(instance.accessTier()),
                            "pool", instance.accessTier().key()
                    ))));
        }
        if (mode == MenuMode.INTERACTIVE) {
            DurationGroup contractGroup = groupFor(instance);
            int acceptedCount = progress.acceptedCount(player, contractGroup);
            int limit = progress.contractLimit(player, contractGroup);
            lore.add(Component.empty());
            lore.add(ItemDisplayUtil.legacy(modeText(mode, "main.mission-lore.contract-count",
                    "&7Contratos activos: &f%accepted%/%limit%", Map.of(
                            "accepted", String.valueOf(acceptedCount), "limit", String.valueOf(limit),
                            "category", contractGroup.display()))));
            String statePath = acceptedMission
                    ? "main.mission-lore.accepted-state"
                    : locked ? "main.mission-lore.locked-state" : "main.mission-lore.available-state";
            String stateFallback = acceptedMission
                    ? "&a✦ Contrato en curso"
                    : locked ? "&cNo disponible para aceptar" : "&eDisponible para aceptar";
            lore.add(ItemDisplayUtil.legacy(modeText(mode, statePath, stateFallback, Map.of())));
        }
        lore.add(Component.empty());
        lore.add(ItemDisplayUtil.legacy(modeText(mode, "main.mission-lore.expiration",
                "&7Expira en: &f%remaining%", Map.of("remaining", TimeUtil.remaining(instance.expiresAt(), now)))));
        List<String> finalLines = new ArrayList<>();
        if (claimed) {
            finalLines.add(modeText(mode, "main.mission-lore.claimed", "&7Recompensa ya reclamada.", Map.of()));
        } else if (complete && !locked) {
            finalLines.add(modeText(mode, "main.mission-lore.completed",
                    mode == MenuMode.VIEW_ONLY
                            ? "&aMisión completada. &7Visita al encargado para reclamarla."
                            : "&a&lClick para recibir la recompensa.", Map.of()));
        } else if (mode == MenuMode.VIEW_ONLY && hasPendingDelivery(player, instance)) {
            finalLines.add(modeText(mode, "main.mission-lore.delivery-pending",
                    "&eEntrega los objetos con el encargado de misiones.", Map.of()));
        } else if (mode == MenuMode.INTERACTIVE) {
            if (acceptedMission) {
                List<String> controls = modeFlexibleLore(mode, "main.mission-lore.accepted-controls", List.of(
                        "&eClick izquierdo: ver detalles.",
                        "&cShift + click derecho: cancelar."
                ), Map.of());
                if (hasPendingDelivery(player, instance)) {
                    int deliveryIndex = Math.min(1, controls.size());
                    controls.add(deliveryIndex, modeText(mode, "main.mission-lore.delivery-control",
                            "&6Click derecho: entregar objetos.", Map.of()));
                }
                finalLines.addAll(controls);
            } else if (locked) {
                finalLines.addAll(modeFlexibleLore(mode, "main.mission-lore.locked-controls", List.of(
                        "&eClick izquierdo: ver detalles."
                ), Map.of()));
            } else {
                finalLines.addAll(modeFlexibleLore(mode, "main.mission-lore.available-controls", List.of(
                        "&eClick izquierdo: ver detalles.",
                        "&aClick derecho: aceptar contrato."
                ), Map.of()));
            }
        } else {
            String details = modeText(mode, "main.mission-lore.details", "", Map.of());
            if (details != null && !details.isBlank()) finalLines.add(details);
        }
        finalLines.removeIf(line -> line == null || line.isBlank());
        if (!finalLines.isEmpty()) {
            lore.add(Component.empty());
            for (String line : finalLines) lore.add(ItemDisplayUtil.legacy(line));
        }

        String action = mode == MenuMode.VIEW_ONLY
                ? "NONE"
                : complete && !claimed && !locked ? "CLAIM" : "MISSION";
        ItemStack decorated = decorate(item, instance.definition().name(), lore,
                action, instance.id(), null, 1, groupFor(instance));
        if (mode == MenuMode.INTERACTIVE && acceptedMission && !claimed) {
            ItemMeta meta = decorated.getItemMeta();
            if (meta != null) {
                // Algunos iconos personalizados (especialmente cofres) pueden traer
                // ENCHANTMENT_GLINT_OVERRIDE=false. Forzarlo garantiza el brillo de
                // contrato activo incluso cuando el icono proviene de MMOItems.
                forceEnchantmentGlint(meta);
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                decorated.setItemMeta(meta);
            }
        }
        return decorated;
    }

    private void forceEnchantmentGlint(ItemMeta meta) {
        try {
            // Paper/Purpur 1.21.6 expone setEnchantmentGlintOverride(Boolean).
            // La reflexión conserva compatibilidad de compilación con APIs que no
            // declaren todavía el método, manteniendo el encantamiento como fallback.
            ItemMeta.class.getMethod("setEnchantmentGlintOverride", Boolean.class)
                    .invoke(meta, Boolean.TRUE);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // El encantamiento UNBREAKING aplicado por el llamador sigue funcionando
            // como respaldo en iconos que no traigan un override explícito a false.
        }
    }

    private boolean hasPendingDelivery(Player player, MissionInstance instance) {
        for (ObjectiveDefinition objective : instance.definition().objectives()) {
            if (objective.type().isDelivery() && progress.progress(player, instance, objective) < objective.amount()) return true;
        }
        return false;
    }

    private ItemStack objectiveItem(Player player, MissionInstance instance, ObjectiveDefinition objective) {
        long value = progress.progress(player, instance, objective);
        boolean complete = value >= objective.amount();
        Material material = complete
                ? modeMaterial(MenuMode.INTERACTIVE, "detail.objective-state.completed-material", Material.LIME_DYE)
                : objective.type().isDelivery()
                    ? modeMaterial(MenuMode.INTERACTIVE, "detail.objective-state.delivery-material", Material.CHEST)
                    : modeMaterial(MenuMode.INTERACTIVE, "detail.objective-state.pending-material", Material.PAPER);
        Map<String, String> values = Map.of(
                "progress", String.valueOf(value),
                "required", String.valueOf(objective.amount()),
                "objective", objective.displayName()
        );
        List<String> lore = new ArrayList<>();
        lore.add(modeText(MenuMode.INTERACTIVE, "detail.objective-lore.progress", "&7Progreso: &f%progress%&7/&f%required%", values));
        if (objective.type().isDelivery() && !complete) {
            lore.add("");
            lore.add(modeText(MenuMode.INTERACTIVE, "detail.objective-lore.delivery", "&eClick para entregar objetos.", values));
        } else if (complete) {
            lore.add("");
            lore.add(modeText(MenuMode.INTERACTIVE, "detail.objective-lore.completed", "&aObjetivo completado.", values));
        }
        String action = objective.type().isDelivery() && !complete ? "DELIVER" : "NONE";
        String objectiveName = modeText(MenuMode.INTERACTIVE, complete
                        ? "detail.objective-lore.completed-name"
                        : "detail.objective-lore.pending-name",
                complete ? "&a%objective%" : "&e%objective%", values);
        return actionItem(material, objectiveName, lore,
                action, instance.id(), objective.id(), 1, groupFor(instance));
    }

    private List<ItemStack> rewardPreview(RewardDefinition reward) {
        List<ItemStack> result = new ArrayList<>();
        for (RewardDefinition.ExperienceReward experience : reward.experience()) {
            String target = professionDisplay(experience.profession());
            Map<String, String> values = Map.of(
                    "amount", String.valueOf(experience.amount()),
                    "target", target
            );
            result.add(actionItem(modeMaterial(MenuMode.INTERACTIVE, "detail.experience-icon", Material.EXPERIENCE_BOTTLE),
                    modeText(MenuMode.INTERACTIVE, "detail.experience-name", "&bExperiencia", values),
                    modeLore(MenuMode.INTERACTIVE, "detail.experience-lore",
                            List.of("&7Recibirás &f%amount% EXP", "&7Para: &f%target%"), values),
                    "NONE", null, null, 1, null));
        }
        for (ItemStack item : rewards.previewItems(reward)) {
            ItemStack preview = ItemUtil.hideNativeTooltip(item);
            List<Component> lore = preview.lore() == null ? new ArrayList<>() : new ArrayList<>(preview.lore());
            lore.add(Component.empty());
            lore.add(ItemDisplayUtil.legacy(modeText(MenuMode.INTERACTIVE, "detail.reward-item-quantity",
                    "&7Cantidad: &f%amount%", Map.of("amount", String.valueOf(item.getAmount())))));
            preview.lore(lore);
            result.add(preview);
        }
        if (result.isEmpty()) {
            result.add(actionItem(modeMaterial(MenuMode.INTERACTIVE, "detail.empty-reward.material", Material.CHEST),
                    modeText(MenuMode.INTERACTIVE, "detail.empty-reward.name", "&eRecompensas", Map.of()),
                    reward.displayLore().isEmpty()
                            ? modeLore(MenuMode.INTERACTIVE, "detail.empty-reward.lore", List.of("&7Recompensa ejecutada por comandos."), Map.of())
                            : reward.displayLore(),
                    "NONE", null, null, 1, null));
        }
        return result;
    }

    private void appendRewardLore(MenuMode mode, List<Component> lore, RewardDefinition reward) {
        lore.add(Component.empty());
        lore.add(ItemDisplayUtil.legacy(modeText(mode, "main.mission-lore.rewards-header", "&eRecompensas:", Map.of())));
        for (String line : reward.displayLore()) lore.add(ItemDisplayUtil.legacy(line));
        for (RewardDefinition.ExperienceReward exp : reward.experience()) {
            Map<String, String> values = Map.of(
                    "amount", String.valueOf(exp.amount()),
                    "target", professionDisplay(exp.profession())
            );
            lore.add(ItemDisplayUtil.legacy(modeText(mode, "main.mission-lore.experience-format",
                    "&7• &b%amount% EXP &f%target%", values)));
        }
        for (RewardDefinition.VanillaItemReward configured : reward.vanillaItems()) {
            Material material = Material.matchMaterial(configured.material());
            if (material == null || material.isAir()) {
                lore.add(ItemDisplayUtil.legacy(modeText(mode, "main.mission-lore.invalid-item-format",
                        "&7• &f%amount%x %item%", Map.of(
                                "amount", String.valueOf(configured.amount()),
                                "item", ItemDisplayUtil.prettify(configured.material())
                        ))));
            } else {
                lore.add(rewardItemLine(mode, "main.mission-lore.vanilla-item-prefix", "&7• &f%amount%x ",
                        configured.amount(), new ItemStack(material)));
            }
        }
        for (RewardDefinition.MmoItemReward configured : reward.mmoItems()) {
            ItemStack item = plugin.getMmoItemsHook().build(configured.type(), configured.id(), 1);
            lore.add(item == null
                    ? ItemDisplayUtil.legacy(modeText(mode, "main.mission-lore.invalid-mmoitem-format",
                            "&7• &d%amount%x %item%", Map.of(
                                    "amount", String.valueOf(configured.amount()),
                                    "item", ItemDisplayUtil.prettify(configured.id())
                            )))
                    : rewardItemLine(mode, "main.mission-lore.mmoitem-prefix", "&7• &d%amount%x ", configured.amount(), item));
        }
        for (RewardDefinition.MythicItemReward configured : reward.mythicItems()) {
            ItemStack item = plugin.getMythicItemsHook().build(configured.id(), 1);
            lore.add(item == null
                    ? ItemDisplayUtil.legacy(modeText(mode, "main.mission-lore.invalid-mythic-item-format",
                            "&7• &5%amount%x %item%", Map.of(
                                    "amount", String.valueOf(configured.amount()),
                                    "item", ItemDisplayUtil.prettify(configured.id())
                            )))
                    : rewardItemLine(mode, "main.mission-lore.mythic-item-prefix", "&7• &5%amount%x ", configured.amount(), item));
        }
        for (RewardDefinition.ExactItemReward configured : reward.exactItems()) {
            lore.add(rewardItemLine(mode, "main.mission-lore.exact-item-prefix", "&7• &f%amount%x ",
                    configured.amount(), configured.item()));
        }
        if (reward.empty() && reward.displayLore().isEmpty()) {
            lore.add(ItemDisplayUtil.legacy(modeText(mode, "main.mission-lore.no-reward",
                    "&7• Sin recompensa configurada", Map.of())));
        }
    }

    private Component rewardItemLine(MenuMode mode, String path, String fallback, int amount, ItemStack item) {
        String prefix = modeText(mode, path, fallback, Map.of("amount", String.valueOf(Math.max(1, amount))));
        return ItemDisplayUtil.legacy(prefix)
                .append(ItemDisplayUtil.effectiveName(item))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    private String professionDisplay(String id) {
        String normalized = id == null || id.isBlank() ? "main" : id.trim();
        String configured = plugin.getConfig().getString("rewards.profession-display-names." + normalized.toLowerCase());
        if (configured != null && !configured.isBlank()) return ColorUtil.strip(configured);
        if (normalized.equalsIgnoreCase("main")) return "Nivel principal";
        return ItemDisplayUtil.prettify(normalized);
    }

    private long nextResetAt(DurationGroup group, long now) {
        long next = Long.MAX_VALUE;
        for (RotationDefinition rotation : plugin.getRegistry().rotations()) {
            if (!rotation.enabled() || !group.accepts(rotation.durationDays())) continue;
            long expiresAt = rotations.window(rotation, now).expiresAt();
            if (expiresAt > now && expiresAt < next) next = expiresAt;
        }
        return next == Long.MAX_VALUE ? 0L : next;
    }

    private DurationGroup groupFor(MissionInstance instance) {
        int days = plugin.getRegistry().durationDays(instance.definition());
        for (DurationGroup group : DurationGroup.values()) if (group.accepts(days)) return group;
        return DurationGroup.ONE_DAY;
    }

    private ItemStack pageArrow(MenuMode mode, boolean next, String action, String instanceId, int destination,
                                int currentPage, int totalPages, DurationGroup group) {
        String base = next ? "page-buttons.next" : "page-buttons.previous";
        Map<String, String> values = Map.of(
                "page", String.valueOf(currentPage),
                "pages", String.valueOf(totalPages),
                "destination", String.valueOf(destination)
        );
        Material material = modeMaterial(mode, base + ".material", Material.ARROW);
        String name = modeText(mode, base + ".name", next ? "&aPágina siguiente" : "&ePágina anterior", values);
        List<String> lore = modeLore(mode, base + ".lore", List.of("&7Página actual: &f%page%/%pages%"), values);
        return actionItem(material, name, lore, action, instanceId, null, destination, group);
    }

    private ItemStack backHead(String action, String name, List<String> lore, DurationGroup group) {
        ItemStack item = ItemUtil.backHead(plugin, name, lore);
        return tag(item, action, null, null, 1, group);
    }

    private ItemStack actionItem(Material material, String name, List<String> lore, String action,
                                 String instanceId, String objectiveId, int page, DurationGroup group) {
        ItemStack item = plugin.getSocialHook().button(material, 1, name, lore, action, "default");
        return tag(item, action, instanceId, objectiveId, page, group);
    }

    private ItemStack decorate(ItemStack item, String name, List<Component> lore, String action,
                               String instanceId, String objectiveId, int page, DurationGroup group) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ItemDisplayUtil.legacy(name));
            meta.lore(lore);
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
        // El menú público nunca acepta objetos del inventario del jugador. Cancelar
        // toda la vista impide shift-click, doble click y teclas numéricas capaces
        // de insertar o recolectar iconos desde el inventario superior.
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= session.inventory().getSize()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(actionKey, PersistentDataType.STRING);
        if (action == null || action.equals("NONE")) return;
        String instanceId = pdc.get(instanceKey, PersistentDataType.STRING);
        String objectiveId = pdc.get(objectiveKey, PersistentDataType.STRING);
        Integer page = pdc.get(pageKey, PersistentDataType.INTEGER);
        DurationGroup group = parseGroup(pdc.get(groupKey, PersistentDataType.STRING), session.group());

        if (session.mode() == MenuMode.VIEW_ONLY
                && (action.equals("DETAIL") || action.equals("MISSION") || action.equals("CLAIM") || action.equals("DELIVER")
                || action.equals("REWARD_PAGE"))) return;

        switch (action) {
            case "BACK" -> openMain(player, session.mode(), group, session.mainPage());
            case "SOCIAL_BACK" -> {
                String command = modeString(session.mode(), "back-command", "social");
                player.closeInventory();
                if (command != null && !command.isBlank()) player.performCommand(command.startsWith("/") ? command.substring(1) : command);
            }
            case "GROUP" -> openMain(player, session.mode(), group, 1);
            case "MAIN_PAGE" -> openMain(player, session.mode(), group, page == null ? 1 : page);
            case "DETAIL" -> openDetail(player, rotations.instance(instanceId), 1, session.mainPage());
            case "MISSION" -> {
                MissionInstance instance = rotations.instance(instanceId);
                if (instance == null) return;
                if (event.isShiftClick() && event.isRightClick() && progress.accepted(player, instance)) {
                    progress.cancelMission(player, instance);
                    openMain(player, MenuMode.INTERACTIVE, group, session.mainPage());
                } else if (event.isRightClick() && progress.accepted(player, instance) && hasPendingDelivery(player, instance)) {
                    deliveries.deliverAll(player, instance);
                    openMain(player, MenuMode.INTERACTIVE, group, session.mainPage());
                } else if (event.isRightClick() && !progress.accepted(player, instance)) {
                    if (progress.acceptMission(player, instance)) {
                        openMain(player, MenuMode.INTERACTIVE, group, session.mainPage());
                    }
                } else if (event.isLeftClick()) {
                    openDetail(player, instance, 1, session.mainPage());
                }
            }
            case "CLAIM" -> rewards.claim(player, rotations.instance(instanceId));
            case "REWARD_PAGE" -> openDetail(player, rotations.instance(instanceId), page == null ? 1 : page, session.mainPage());
            case "DELIVER" -> {
                MissionInstance instance = rotations.instance(instanceId);
                if (instance == null) return;
                ObjectiveDefinition objective = instance.definition().objective(objectiveId);
                deliveries.deliver(player, instance, objective);
                openDetail(player, instance, session.rewardPage(), session.mainPage());
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

    private String modePath(MenuMode mode, String relative) {
        return "menus." + mode.configKey() + "." + relative;
    }

    private String legacyPath(String relative) {
        return "menus." + relative;
    }

    private String modeString(MenuMode mode, String relative, String fallback) {
        String primary = modePath(mode, relative);
        if (plugin.getConfig().isSet(primary)) {
            String value = plugin.getConfig().getString(primary);
            return value == null ? fallback : value;
        }
        String legacy = legacyPath(relative);
        String value = plugin.getConfig().getString(legacy, fallback);
        return value == null ? fallback : value;
    }

    private int modeInt(MenuMode mode, String relative, int fallback) {
        String primary = modePath(mode, relative);
        if (plugin.getConfig().isSet(primary)) return plugin.getConfig().getInt(primary, fallback);
        return plugin.getConfig().getInt(legacyPath(relative), fallback);
    }

    private boolean modeBoolean(MenuMode mode, String relative, boolean fallback) {
        String primary = modePath(mode, relative);
        if (plugin.getConfig().isSet(primary)) return plugin.getConfig().getBoolean(primary, fallback);
        return plugin.getConfig().getBoolean(legacyPath(relative), fallback);
    }

    private Material modeMaterial(MenuMode mode, String relative, Material fallback) {
        String raw = modeString(mode, relative, fallback.name());
        Material material = Material.matchMaterial(raw == null ? "" : raw);
        return material == null || material.isAir() ? fallback : material;
    }

    private int[] modeSlots(MenuMode mode, String relative, int[] fallback) {
        String primary = modePath(mode, relative);
        List<Integer> configured = plugin.getConfig().isSet(primary)
                ? plugin.getConfig().getIntegerList(primary)
                : plugin.getConfig().getIntegerList(legacyPath(relative));
        if (configured == null || configured.isEmpty()) return fallback.clone();
        return configured.stream().mapToInt(Integer::intValue).toArray();
    }

    private String modeText(MenuMode mode, String relative, String fallback, Map<String, String> replacements) {
        String value = modeString(mode, relative, fallback);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            value = value.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return value;
    }

    private List<String> modeLore(MenuMode mode, String relative, List<String> fallback,
                                  Map<String, String> replacements) {
        String primary = modePath(mode, relative);
        List<String> values = plugin.getConfig().isSet(primary)
                ? plugin.getConfig().getStringList(primary)
                : plugin.getConfig().getStringList(legacyPath(relative));
        if (values == null || values.isEmpty()) values = fallback;
        List<String> result = new ArrayList<>(values.size());
        for (String raw : values) {
            String value = raw;
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                value = value.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            result.add(value);
        }
        return result;
    }

    /**
     * Lee una entrada de lore tanto si el YAML viejo la guardó como String como
     * si la versión nueva la define como lista. Las líneas antiguas con dos
     * acciones separadas por punto se dividen automáticamente.
     */
    private List<String> modeFlexibleLore(MenuMode mode, String relative, List<String> fallback,
                                          Map<String, String> replacements) {
        String primary = modePath(mode, relative);
        String selectedPath = plugin.getConfig().isSet(primary) ? primary : legacyPath(relative);
        Object raw = plugin.getConfig().get(selectedPath);
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object entry : list) if (entry != null) values.add(String.valueOf(entry));
        } else if (raw != null && !String.valueOf(raw).isBlank()) {
            String[] split = String.valueOf(raw).split("(?<=\\.)\\s+");
            for (String entry : split) if (!entry.isBlank()) values.add(entry);
        }
        if (values.isEmpty()) values.addAll(fallback);

        List<String> result = new ArrayList<>(values.size());
        for (String rawLine : values) {
            String value = rawLine;
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                value = value.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            result.add(value);
        }
        return result;
    }

    private int modeMenuSize(MenuMode mode, String relative, int fallback) {
        int size = modeInt(mode, relative, fallback);
        if (size < 9) return fallback;
        size = Math.min(54, size);
        return size - (size % 9);
    }

    private boolean validSlot(int slot, int size) {
        return slot >= 0 && slot < size;
    }

    private enum MenuMode {
        VIEW_ONLY("viewer"),
        INTERACTIVE("interactive");

        private final String configKey;
        MenuMode(String configKey) { this.configKey = configKey; }
        String configKey() { return configKey; }
    }

    private enum MenuType { MAIN, DETAIL }
    private record MenuSession(Inventory inventory, MenuType type, MenuMode mode, int mainPage,
                               DurationGroup group, String instanceId, int rewardPage) { }
}
