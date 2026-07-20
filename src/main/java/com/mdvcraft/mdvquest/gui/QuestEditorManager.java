package com.mdvcraft.mdvquest.gui;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.hook.MMOItemsHook;
import com.mdvcraft.mdvquest.hook.MythicItemsHook;
import com.mdvcraft.mdvquest.model.AccessTier;
import com.mdvcraft.mdvquest.model.MissionDefinition;
import com.mdvcraft.mdvquest.model.ObjectiveDefinition;
import com.mdvcraft.mdvquest.model.ObjectiveType;
import com.mdvcraft.mdvquest.model.QuestDraft;
import com.mdvcraft.mdvquest.model.RewardDefinition;
import com.mdvcraft.mdvquest.service.QuestYamlService;
import com.mdvcraft.mdvquest.service.RewardService;
import com.mdvcraft.mdvquest.util.ColorUtil;
import com.mdvcraft.mdvquest.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Editor y catálogo administrativo completamente in-game. */
public final class QuestEditorManager implements Listener {
    private static final String ALL_FILES = "*";
    private static final int[] ADMIN_CATEGORY_SLOTS = {9, 18, 27, 36};
    private static final int[] ADMIN_MISSION_SLOTS = {
            10,11,12,13,14,15,16,17,
            19,20,21,22,23,24,25,26,
            28,29,30,31,32,33,34,35,
            37,38,39,40,41,42,43,44
    };
    private static final int[] DEPOSIT_SLOTS = {
            9,10,11,12,13,14,15,16,17,
            18,19,20,21,22,23,24,25,26,
            27,28,29,30,31,32,33,34,35,
            36,37,38,39,40,41,42,43,44
    };

    private final MDVQuestPlugin plugin;
    private final ChatPromptManager prompts;
    private final QuestYamlService yamlService;
    private final RewardService rewardService;
    private final MMOItemsHook mmoItems;
    private final MythicItemsHook mythicItems;
    private final Map<UUID, QuestDraft> drafts = new HashMap<>();
    private final Map<UUID, EditorSession> sessions = new HashMap<>();
    private final Map<UUID, CatalogContext> catalogContexts = new HashMap<>();

    private final NamespacedKey actionKey;
    private final NamespacedKey indexKey;
    private final NamespacedKey pageKey;
    private final NamespacedKey groupKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey missionKey;
    private final NamespacedKey virtualRewardKey;

    public QuestEditorManager(MDVQuestPlugin plugin, ChatPromptManager prompts, QuestYamlService yamlService,
                              RewardService rewardService, MMOItemsHook mmoItems, MythicItemsHook mythicItems) {
        this.plugin = plugin;
        this.prompts = prompts;
        this.yamlService = yamlService;
        this.rewardService = rewardService;
        this.mmoItems = mmoItems;
        this.mythicItems = mythicItems;
        this.actionKey = new NamespacedKey(plugin, "editor_action");
        this.indexKey = new NamespacedKey(plugin, "editor_index");
        this.pageKey = new NamespacedKey(plugin, "editor_page");
        this.groupKey = new NamespacedKey(plugin, "editor_group");
        this.typeKey = new NamespacedKey(plugin, "editor_type");
        this.missionKey = new NamespacedKey(plugin, "editor_mission");
        this.virtualRewardKey = new NamespacedKey(plugin, "virtual_reward_item");
    }

    private boolean hasEditorPermission(Player player) {
        return player != null && (player.hasPermission("mdvquest.editor") || player.hasPermission("mdvquest.admin"));
    }

    public void openDurationPicker(Player player) {
        if (!hasEditorPermission(player)) {
            plugin.message(player, "no-permission", Map.of());
            return;
        }
        Inventory inventory = plugin.getSocialHook().createInventory(null, "&8Crear misión: duración", 54, true);
        int[] slots = {10,11,12,13,14,15,16};
        for (int day = 1; day <= 7; day++) {
            List<String> lore = new ArrayList<>();
            lore.add("&7Duración: &f" + day + (day == 1 ? " día real" : " días reales"));
            lore.add("&7Rotación: &f" + QuestDraft.rotationForDays(day));
            lore.add("");
            lore.add("&eClick para seleccionar.");
            inventory.setItem(slots[day - 1], item(Material.BOOK, "&e" + day + (day == 1 ? " día" : " días"), lore,
                    "DURATION", day, 1, null, null, null));
        }
        inventory.setItem(45, backHead("DURATION_BACK", "&eVolver", List.of("&7Regresa al editor o al menú anterior.")));
        inventory.setItem(49, item(Material.BOOKSHELF, "&6Catálogo administrativo",
                List.of("&7Consulta o edita todas las misiones."), "ADMIN_CATALOG", -1, 1, DurationGroup.ONE_DAY, null, null));
        inventory.setItem(53, item(Material.BARRIER, "&cCerrar", List.of(), "CLOSE", -1, 1, null, null, null));
        open(player, inventory, MenuType.DURATION, 1, null, null);
    }

    public void openEditor(Player player) {
        if (!hasEditorPermission(player)) {
            plugin.message(player, "no-permission", Map.of());
            return;
        }
        QuestDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            openDurationPicker(player);
            return;
        }
        Inventory inventory = plugin.getSocialHook().createInventory(null, "&8Editor de misión", 54, true);
        inventory.setItem(4, previewDraft(draft));

        inventory.setItem(10, item(Material.NAME_TAG, "&eID interno", List.of("&7Actual: &f" + draft.id(), "", "&eClick para cambiar."), "EDIT_ID", -1, 1, null, null, null));
        inventory.setItem(11, item(Material.OAK_SIGN, "&eNombre", List.of("&7Actual:", draft.name(), "", "&eClick para cambiar."), "EDIT_NAME", -1, 1, null, null, null));
        inventory.setItem(12, item(Material.WRITABLE_BOOK, "&eLore / descripción", lorePreview(draft.lore()), "EDIT_LORE", -1, 1, null, null, null));
        inventory.setItem(13, decorate(draft.icon(), "&eIcono", List.of("&7Toma el objeto de tu mano principal.", "", "&eClick para copiarlo."), "EDIT_ICON", -1, 1, null, null, null));
        inventory.setItem(14, item(Material.CLOCK, "&eDuración", List.of("&7Actual: &f" + draft.durationDays() + " días reales", "&7Rotación: &f" + draft.rotation(), "", "&eClick para cambiar."), "EDIT_DURATION", -1, 1, null, null, null));
        inventory.setItem(15, item(Material.CHEST, "&eArchivo YAML", List.of("&7Actual: &f" + draft.targetFile(), "", "&eClick para elegir."), "EDIT_FILE", -1, 1, null, null, null));
        inventory.setItem(16, item(Material.GOLD_NUGGET, "&ePeso de selección", List.of("&7Actual: &f" + draft.weight(), "&7Mayor peso = más probabilidad.", "", "&eClick para cambiar."), "EDIT_WEIGHT", -1, 1, null, null, null));

        inventory.setItem(19, item(Material.TARGET, "&aAñadir objetivo",
                List.of("&7Objetivos actuales: &f" + draft.objectives().size(), "", "&eClick: añadir", "&eShift + click derecho: administrar"),
                "ADD_OBJECTIVE", -1, 1, null, null, null));
        inventory.setItem(20, item(Material.MAP, "&eAdministrar objetivos",
                List.of("&7Click izquierdo: editar", "&7Click derecho: eliminar"), "MANAGE_OBJECTIVES", -1, 1, null, null, null));
        inventory.setItem(21, item(Material.CHEST_MINECART, "&dRecompensas de objetos",
                rewardSummary(draft.rewards()), "EDIT_ITEM_REWARDS", -1, 1, null, null, null));
        inventory.setItem(22, item(Material.EXPERIENCE_BOTTLE, "&bRecompensas de experiencia",
                experienceSummary(draft.rewards()), "EDIT_XP_REWARDS", -1, 1, null, null, null));
        inventory.setItem(23, item(Material.COMMAND_BLOCK, "&cComandos de recompensa",
                commandSummary(draft.rewards()), "EDIT_COMMANDS", -1, 1, null, null, null));
        inventory.setItem(24, item(draft.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                draft.enabled() ? "&aMisión habilitada" : "&7Misión deshabilitada",
                List.of("&7Click para alternar."), "TOGGLE_ENABLED", -1, 1, null, null, null));
        inventory.setItem(25, item(accessMaterial(draft.accessTier()), "&eAcceso / pool",
                List.of(
                        "&7Actual: &f" + accessDisplay(draft.accessTier()),
                        "&7Define en qué catálogo puede salir.",
                        "",
                        "&eClick para seleccionar."
                ), "EDIT_ACCESS", -1, 1, null, null, null));

        inventory.setItem(45, backHead("EDITOR_BACK", "&eVolver", List.of("&7Regresa al catálogo administrativo.")));
        inventory.setItem(48, item(Material.BARRIER, "&cCancelar", List.of("&7Descarta el borrador."), "CANCEL_DRAFT", -1, 1, null, null, null));
        inventory.setItem(49, item(Material.EMERALD_BLOCK, "&a&lGuardar misión", List.of("&7Escribe o modifica el YAML y recarga MDVQuest."), "SAVE_DRAFT", -1, 1, null, null, null));
        inventory.setItem(50, item(Material.REDSTONE_BLOCK, "&cResetear", List.of("&7Borra el contenido del borrador actual."), "RESET_DRAFT", -1, 1, null, null, null));
        inventory.setItem(53, item(Material.BARRIER, "&cCerrar", List.of(), "CLOSE", -1, 1, null, null, null));
        open(player, inventory, MenuType.EDITOR, 1, null, null);
    }

    private void openAccessPicker(Player player) {
        QuestDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            openDurationPicker(player);
            return;
        }
        Inventory inventory = plugin.getSocialHook().createInventory(null, "&8Acceso de la misión", 54, true);
        inventory.setItem(20, item(Material.PAPER, "&fNormal", List.of(
                "&7Puede salir en el pool normal.",
                "&7También puede ser elegida como extra VIP1.",
                "",
                draft.accessTier() == AccessTier.NORMAL ? "&aSeleccionada." : "&eClick para seleccionar."
        ), "SET_ACCESS", -1, 1, null, null, AccessTier.NORMAL.key()));
        inventory.setItem(22, item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&bVIP 1", List.of(
                "&7Puede salir en el pool VIP1.",
                "&7También puede ser elegida como extra VIP2.",
                "",
                draft.accessTier() == AccessTier.VIP1 ? "&aSeleccionada." : "&eClick para seleccionar."
        ), "SET_ACCESS", -1, 1, null, null, AccessTier.VIP1.key()));
        inventory.setItem(24, item(Material.YELLOW_STAINED_GLASS_PANE, "&eVIP 2", List.of(
                "&7Solo puede salir en el pool VIP2.",
                "",
                draft.accessTier() == AccessTier.VIP2 ? "&aSeleccionada." : "&eClick para seleccionar."
        ), "SET_ACCESS", -1, 1, null, null, AccessTier.VIP2.key()));
        inventory.setItem(49, backHead("EDITOR", "&eVolver", List.of("&7Regresa al editor.")));
        open(player, inventory, MenuType.ACCESS_TIER, 1, null, null);
    }

    public void openAdminCatalog(Player player) {
        if (!hasEditorPermission(player)) {
            plugin.message(player, "no-permission", Map.of());
            return;
        }
        openAdminCatalog(player, DurationGroup.ONE_DAY, 1, ALL_FILES);
    }

    private void openAdminCatalog(Player player, DurationGroup group, int requestedPage) {
        CatalogContext context = catalogContexts.get(player.getUniqueId());
        String fileFilter = context == null ? ALL_FILES : context.fileFilter();
        openAdminCatalog(player, group, requestedPage, fileFilter);
    }

    private void openAdminCatalog(Player player, DurationGroup group, int requestedPage, String requestedFileFilter) {
        List<String> fileOptions = adminFileOptions();
        String fileFilter = normalizeFileFilter(requestedFileFilter, fileOptions);
        List<MissionDefinition> filtered = plugin.getRegistry().missions().stream()
                .filter(mission -> group.accepts(plugin.getRegistry().durationDays(mission)))
                .filter(mission -> ALL_FILES.equals(fileFilter) || mission.sourceFile().equalsIgnoreCase(fileFilter))
                .sorted(Comparator.comparing(MissionDefinition::sourceFile).thenComparing(MissionDefinition::id))
                .toList();
        int pages = Math.max(1, (int) Math.ceil(filtered.size() / (double) ADMIN_MISSION_SLOTS.length));
        int page = Math.max(1, Math.min(pages, requestedPage));
        Inventory inventory = plugin.getSocialHook().createInventory(null, "&8Administrador de misiones", 54, true);

        DurationGroup[] groups = DurationGroup.values();
        for (int i = 0; i < groups.length; i++) {
            DurationGroup option = groups[i];
            long count = plugin.getRegistry().missions().stream()
                    .filter(m -> option.accepts(plugin.getRegistry().durationDays(m)))
                    .filter(m -> ALL_FILES.equals(fileFilter) || m.sourceFile().equalsIgnoreCase(fileFilter))
                    .count();
            inventory.setItem(ADMIN_CATEGORY_SLOTS[i], item(option == group ? Material.WRITABLE_BOOK : Material.BOOK,
                    (option == group ? "&a" : "&e") + option.display(), List.of("&7Misiones: &f" + count),
                    "ADMIN_GROUP", -1, 1, option, null, null));
        }

        int start = (page - 1) * ADMIN_MISSION_SLOTS.length;
        for (int i = 0; i < ADMIN_MISSION_SLOTS.length && start + i < filtered.size(); i++) {
            inventory.setItem(ADMIN_MISSION_SLOTS[i], adminMissionItem(filtered.get(start + i), group));
        }

        if (pages > 1 && page > 1) inventory.setItem(47, item(Material.ARROW, "&ePágina anterior", List.of(), "ADMIN_PAGE", -1, page - 1, group, null, null));
        inventory.setItem(49, item(Material.NETHER_STAR, "&aCrear nueva misión", List.of("&7Abre el selector de duración."), "NEW_MISSION", -1, 1, null, null, null));
        if (pages > 1 && page < pages) inventory.setItem(51, item(Material.ARROW, "&aPágina siguiente", List.of(), "ADMIN_PAGE", -1, page + 1, group, null, null));
        inventory.setItem(45, backHead("ADMIN_BACK", "&eVolver", List.of("&7Regresa al menú anterior.")));
        int filterSlot = plugin.getConfig().getInt("editor.admin-catalog.file-filter.slot", 46);
        if (filterSlot >= 0 && filterSlot < inventory.getSize()) {
            inventory.setItem(filterSlot, adminFileFilterItem(fileFilter, fileOptions));
        }
        inventory.setItem(53, item(Material.BARRIER, "&cCerrar", List.of(), "CLOSE", -1, 1, null, null, null));
        catalogContexts.put(player.getUniqueId(), new CatalogContext(group, page, fileFilter));
        open(player, inventory, MenuType.ADMIN_CATALOG, page, group, null, fileFilter);
    }

    private void openAdminPreview(Player player, MissionDefinition mission) {
        if (mission == null) {
            openAdminCatalog(player);
            return;
        }
        Inventory inventory = plugin.getSocialHook().createInventory(null, "&8Vista: " + ColorUtil.strip(mission.name()), 54, true);
        inventory.setItem(4, adminMissionItem(mission, groupFor(mission)));
        int[] objectiveSlots = {18,19,20,21,22,23,24,25,26,27,28,29,30,31};
        for (int i = 0; i < mission.objectives().size() && i < objectiveSlots.length; i++) {
            ObjectiveDefinition objective = mission.objectives().get(i);
            inventory.setItem(objectiveSlots[i], item(Material.PAPER, "&e" + objective.displayName(), objectiveLore(objective), "NONE", i, 1, null, null, null));
        }
        List<ItemStack> previews = rewardService.previewItems(mission.rewards());
        int[] rewardSlots = {37,38,39,40,41,42,43,44};
        for (int i = 0; i < previews.size() && i < rewardSlots.length; i++) inventory.setItem(rewardSlots[i], ItemUtil.hideNativeTooltip(previews.get(i)));
        inventory.setItem(45, backHead("ADMIN_CATALOG", "&eVolver", List.of("&7Regresa al catálogo.")));
        inventory.setItem(49, item(Material.ANVIL, "&aEditar esta misión", List.of("&7Abre todos sus valores en el editor."), "EDIT_MISSION", -1, 1, null, null, mission.id()));
        inventory.setItem(53, item(Material.BARRIER, "&cCerrar", List.of(), "CLOSE", -1, 1, null, null, null));
        CatalogContext context = catalogContexts.getOrDefault(player.getUniqueId(),
                new CatalogContext(groupFor(mission), 1, ALL_FILES));
        open(player, inventory, MenuType.ADMIN_PREVIEW, context.page(), context.group(), mission.id(), context.fileFilter());
    }

    private ItemStack adminFileFilterItem(String fileFilter, List<String> options) {
        int index = Math.max(0, options.indexOf(fileFilter));
        String generalName = plugin.getConfig().getString(
                "editor.admin-catalog.file-filter.general-name", "General - todos los YAML");
        String display = ALL_FILES.equals(fileFilter) ? generalName : fileFilter;
        Map<String, String> replacements = Map.of(
                "file", display,
                "index", String.valueOf(index + 1),
                "total", String.valueOf(options.size())
        );
        Material configured = Material.matchMaterial(plugin.getConfig().getString(
                "editor.admin-catalog.file-filter.material", "HOPPER"));
        if (configured == null || configured.isAir()) configured = Material.HOPPER;
        String name = replace(plugin.getConfig().getString(
                "editor.admin-catalog.file-filter.name", "&eFiltrar por archivo YAML"), replacements);
        List<String> configuredLore = plugin.getConfig().getStringList("editor.admin-catalog.file-filter.lore");
        if (configuredLore.isEmpty()) configuredLore = List.of(
                "&7Archivo actual:",
                "&f%file%",
                "",
                "&7Opción: &f%index%/%total%",
                "",
                "&eClick izquierdo: siguiente",
                "&eClick derecho: anterior",
                "&eShift-click: mostrar todos"
        );
        List<String> finalLore = configuredLore.stream().map(line -> replace(line, replacements)).toList();
        return item(configured, name, finalLore, "ADMIN_FILE_FILTER", -1, 1, null, null, fileFilter);
    }

    private void cycleAdminFileFilter(Player player, InventoryClickEvent event, EditorSession session) {
        List<String> options = adminFileOptions();
        if (event.isShiftClick()) {
            openAdminCatalog(player, session.group(), 1, ALL_FILES);
            return;
        }
        String current = normalizeFileFilter(session.fileFilter(), options);
        int index = Math.max(0, options.indexOf(current));
        int direction = event.isRightClick() ? -1 : 1;
        int next = Math.floorMod(index + direction, options.size());
        openAdminCatalog(player, session.group(), 1, options.get(next));
    }

    private List<String> adminFileOptions() {
        List<String> result = new ArrayList<>();
        result.add(ALL_FILES);
        for (String file : yamlService.files()) {
            if (file == null || file.isBlank()) continue;
            if (result.stream().noneMatch(existing -> existing.equalsIgnoreCase(file))) result.add(file);
        }
        return result;
    }

    private String normalizeFileFilter(String requested, List<String> options) {
        if (requested == null || requested.isBlank() || ALL_FILES.equals(requested)) return ALL_FILES;
        return options.stream().filter(option -> option.equalsIgnoreCase(requested)).findFirst().orElse(ALL_FILES);
    }

    private String replace(String value, Map<String, String> replacements) {
        String result = value == null ? "" : value;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    private void openObjectiveCatalog(Player player, int requestedPage) {
        List<ObjectiveType> types = Arrays.stream(ObjectiveType.values()).filter(type -> type != ObjectiveType.CLAN_KILL).toList();
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        int pages = Math.max(1, (int) Math.ceil(types.size() / (double) slots.length));
        int page = Math.max(1, Math.min(pages, requestedPage));
        Inventory inventory = plugin.getSocialHook().createInventory(null, "&8Catálogo de objetivos", 54, true);
        int start = (page - 1) * slots.length;
        for (int i = 0; i < slots.length && start + i < types.size(); i++) {
            ObjectiveType type = types.get(start + i);
            inventory.setItem(slots[i], item(objectiveMaterial(type), "&e" + objectiveDisplay(type),
                    List.of("&7" + objectiveHelp(type), "", "&eClick para configurar."), "OBJECTIVE_TYPE", -1, 1, null, type, null));
        }
        if (pages > 1 && page > 1) inventory.setItem(47, item(Material.ARROW, "&eAnterior", List.of(), "OBJECTIVE_PAGE", -1, page - 1, null, null, null));
        if (pages > 1 && page < pages) inventory.setItem(51, item(Material.ARROW, "&aSiguiente", List.of(), "OBJECTIVE_PAGE", -1, page + 1, null, null, null));
        inventory.setItem(45, backHead("EDITOR", "&eVolver", List.of("&7Regresa al editor.")));
        inventory.setItem(53, item(Material.BARRIER, "&cCerrar", List.of(), "CLOSE", -1, 1, null, null, null));
        open(player, inventory, MenuType.OBJECTIVE_CATALOG, page, null, null);
    }

    private void openObjectiveManage(Player player) {
        QuestDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) { openDurationPicker(player); return; }
        Inventory inventory = plugin.getSocialHook().createInventory(null, "&8Objetivos de la misión", 54, true);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        for (int i = 0; i < draft.objectives().size() && i < slots.length; i++) {
            ObjectiveDefinition objective = draft.objectives().get(i);
            List<String> lore = new ArrayList<>(objectiveLore(objective));
            lore.add("");
            lore.add("&eClick izquierdo: editar");
            lore.add("&cClick derecho: eliminar");
            inventory.setItem(slots[i], item(objectiveMaterial(objective.type()), "&e" + objective.displayName(), lore, "OBJECTIVE_ENTRY", i, 1, null, objective.type(), null));
        }
        inventory.setItem(45, backHead("EDITOR", "&eVolver", List.of("&7Regresa al editor.")));
        inventory.setItem(49, item(Material.LIME_DYE, "&aAñadir otro objetivo", List.of(), "ADD_OBJECTIVE", -1, 1, null, null, null));
        inventory.setItem(53, item(Material.BARRIER, "&cCerrar", List.of(), "CLOSE", -1, 1, null, null, null));
        open(player, inventory, MenuType.OBJECTIVE_MANAGE, 1, null, null);
    }

    private void openRewardItems(Player player) {
        QuestDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) { openDurationPicker(player); return; }
        Inventory inventory = plugin.getSocialHook().createInventory(null, "&8Recompensas de objetos", 54, true);
        // MDVSocial rellena el inventario con paneles decorativos. La cuadrícula de
        // recompensas debe quedar realmente vacía para poder editarla libremente.
        for (int slot : DEPOSIT_SLOTS) inventory.setItem(slot, null);

        inventory.setItem(4, item(Material.CHEST, "&eEditor de recompensas", List.of(
                "&7Los objetos de la cuadrícula representan",
                "&7la recompensa final de la misión.",
                "",
                "&7Puedes moverlos, cambiar cantidades,",
                "&7quitarlos o añadir otros desde tu inventario.",
                "&7Shift-click desde tu inventario los añade.",
                "&7Shift-click en la cuadrícula los retira.",
                "&7Los objetos usados como plantilla se devuelven."),
                "NONE", -1, 1, null, null, null));

        List<ItemStack> current = rewardService.previewItems(draft.rewards());
        int count = Math.min(DEPOSIT_SLOTS.length, current.size());
        for (int i = 0; i < count; i++) {
            inventory.setItem(DEPOSIT_SLOTS[i], markVirtualReward(current.get(i)));
        }
        if (current.size() > DEPOSIT_SLOTS.length) {
            player.sendMessage(plugin.prefix() + "§cLa recompensa tiene más de " + DEPOSIT_SLOTS.length
                    + " stacks y no puede editarse completa desde esta pantalla.");
        }

        inventory.setItem(45, backHead("REWARD_CANCEL", "&eCancelar", List.of("&7Descarta los cambios y devuelve tus plantillas.")));
        inventory.setItem(46, item(Material.LAVA_BUCKET, "&cEliminar todas las recompensas",
                List.of("&7Elimina solamente las recompensas de objetos.", "&7No afecta experiencia ni comandos."),
                "REWARD_CLEAR", -1, 1, null, null, null));
        inventory.setItem(48, item(Material.BARRIER, "&cCancelar", List.of("&7No guarda los cambios."),
                "REWARD_CANCEL", -1, 1, null, null, null));
        inventory.setItem(49, item(Material.EMERALD_BLOCK, "&aGuardar recompensas", List.of(
                "&7Reemplaza las recompensas de objetos",
                "&7por el contenido actual de la cuadrícula.",
                "",
                "&7Identifica automáticamente objetos vanilla,",
                "&7MMOItems y MythicMobs/Crucible."),
                "REWARD_ACCEPT", -1, 1, null, null, null));
        inventory.setItem(50, item(Material.REDSTONE_BLOCK, "&cRestablecer cambios", List.of(
                "&7Recupera las recompensas guardadas",
                "&7antes de abrir este editor."),
                "REWARD_RESET", -1, 1, null, null, null));
        inventory.setItem(53, item(Material.BARRIER, "&cCerrar", List.of("&7No guarda los cambios."),
                "REWARD_CANCEL", -1, 1, null, null, null));
        open(player, inventory, MenuType.REWARD_ITEMS, 1, null, null);
    }

    private void openExperienceRewards(Player player) {
        QuestDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) { openDurationPicker(player); return; }
        Inventory inventory = plugin.getSocialHook().createInventory(null, "&8Experiencia de recompensa", 54, true);
        inventory.setItem(11, item(Material.EXPERIENCE_BOTTLE, "&aAñadir EXP principal", List.of("&7Solicita la cantidad por chat."), "XP_ADD_MAIN", -1, 1, null, null, null));
        inventory.setItem(15, item(Material.ENCHANTED_BOOK, "&bAñadir EXP de profesión", List.of("&7Solicita profesión y cantidad por chat."), "XP_ADD_PROFESSION", -1, 1, null, null, null));
        int[] slots = {19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        List<RewardDefinition.ExperienceReward> experience = draft.rewards().experience();
        for (int i = 0; i < experience.size() && i < slots.length; i++) {
            RewardDefinition.ExperienceReward reward = experience.get(i);
            inventory.setItem(slots[i], item(Material.EXPERIENCE_BOTTLE, "&b" + professionDisplay(reward.profession()),
                    List.of("&7Cantidad: &f" + reward.amount(), "&7Destino: &f" + professionDisplay(reward.profession()), "", "&eClick izquierdo: editar cantidad", "&cClick derecho: eliminar"),
                    "XP_ENTRY", i, 1, null, null, null));
        }
        inventory.setItem(45, backHead("EDITOR", "&eVolver", List.of("&7Regresa al editor.")));
        inventory.setItem(53, item(Material.BARRIER, "&cCerrar", List.of(), "CLOSE", -1, 1, null, null, null));
        open(player, inventory, MenuType.XP_REWARDS, 1, null, null);
    }

    private void openFileChooser(Player player) {
        Inventory inventory = plugin.getSocialHook().createInventory(null, "&8Elegir archivo YAML", 54, true);
        List<String> files = yamlService.files();
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        for (int i = 0; i < files.size() && i < slots.length; i++) {
            inventory.setItem(slots[i], item(Material.PAPER, "&f" + files.get(i), List.of("&eClick para guardar aquí."), "FILE_SELECT", i, 1, null, null, files.get(i)));
        }
        inventory.setItem(49, item(Material.WRITABLE_BOOK, "&aCrear/usar otro YAML", List.of("&7Escribe el nombre por chat."), "FILE_NEW", -1, 1, null, null, null));
        inventory.setItem(45, backHead("EDITOR", "&eVolver", List.of("&7Regresa al editor.")));
        inventory.setItem(53, item(Material.BARRIER, "&cCerrar", List.of(), "CLOSE", -1, 1, null, null, null));
        open(player, inventory, MenuType.FILE_CHOOSER, 1, null, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) return;
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || !event.getView().getTopInventory().equals(session.inventory())) return;
        if (!hasEditorPermission(player)) {
            event.setCancelled(true);
            player.closeInventory();
            plugin.message(player, "no-permission", Map.of());
            return;
        }

        if (session.type() == MenuType.REWARD_ITEMS) {
            handleRewardInventoryClick(event, player, session);
            return;
        }

        if (event.getRawSlot() < 0 || event.getRawSlot() >= session.inventory().getSize()) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(actionKey, PersistentDataType.STRING);
        if (action == null || action.equals("NONE")) return;
        int index = Optional.ofNullable(pdc.get(indexKey, PersistentDataType.INTEGER)).orElse(-1);
        int page = Optional.ofNullable(pdc.get(pageKey, PersistentDataType.INTEGER)).orElse(1);
        DurationGroup group = parseGroup(pdc.get(groupKey, PersistentDataType.STRING), session.group());
        ObjectiveType type = parseType(pdc.get(typeKey, PersistentDataType.STRING));
        String missionId = pdc.get(missionKey, PersistentDataType.STRING);

        switch (action) {
            case "CLOSE" -> player.closeInventory();
            case "DURATION" -> selectDuration(player, index);
            case "DURATION_BACK" -> { if (drafts.containsKey(player.getUniqueId())) openEditor(player); else openAdminCatalog(player); }
            case "ADMIN_CATALOG" -> openAdminCatalog(player, group, session.page(), session.fileFilter());
            case "ADMIN_GROUP" -> openAdminCatalog(player, group, 1, session.fileFilter());
            case "ADMIN_PAGE" -> openAdminCatalog(player, group, page, session.fileFilter());
            case "ADMIN_FILE_FILTER" -> cycleAdminFileFilter(player, event, session);
            case "ADMIN_BACK" -> player.closeInventory();
            case "NEW_MISSION" -> { drafts.remove(player.getUniqueId()); openDurationPicker(player); }
            case "ADMIN_MISSION" -> {
                MissionDefinition mission = plugin.getRegistry().mission(missionId);
                if (event.isRightClick()) editMission(player, mission);
                else openAdminPreview(player, mission);
            }
            case "EDIT_MISSION" -> editMission(player, plugin.getRegistry().mission(missionId));
            case "EDITOR" -> openEditor(player);
            case "EDITOR_BACK" -> returnToAdminCatalog(player);
            case "EDIT_ID" -> promptId(player);
            case "EDIT_NAME" -> promptName(player);
            case "EDIT_LORE" -> promptLore(player);
            case "EDIT_ICON" -> changeIcon(player);
            case "EDIT_DURATION" -> openDurationPicker(player);
            case "EDIT_FILE" -> openFileChooser(player);
            case "EDIT_WEIGHT" -> promptWeight(player);
            case "EDIT_ACCESS" -> openAccessPicker(player);
            case "SET_ACCESS" -> { draft(player).setAccessTier(AccessTier.parse(missionId)); openEditor(player); }
            case "ADD_OBJECTIVE" -> { if (event.isRightClick() && event.isShiftClick()) openObjectiveManage(player); else openObjectiveCatalog(player, 1); }
            case "MANAGE_OBJECTIVES" -> openObjectiveManage(player);
            case "OBJECTIVE_PAGE" -> openObjectiveCatalog(player, page);
            case "OBJECTIVE_TYPE" -> startObjectiveWizard(player, type, -1);
            case "OBJECTIVE_ENTRY" -> { if (event.isRightClick()) deleteObjective(player, index); else startObjectiveWizard(player, type, index); }
            case "EDIT_ITEM_REWARDS" -> openRewardItems(player);
            case "EDIT_XP_REWARDS" -> openExperienceRewards(player);
            case "EDIT_COMMANDS" -> promptCommands(player);
            case "TOGGLE_ENABLED" -> { QuestDraft draft = draft(player); draft.setEnabled(!draft.enabled()); openEditor(player); }
            case "XP_ADD_MAIN" -> promptExperience(player, "main", -1);
            case "XP_ADD_PROFESSION" -> promptProfessionExperience(player);
            case "XP_ENTRY" -> { if (event.isRightClick()) deleteExperience(player, index); else editExperience(player, index); }
            case "FILE_SELECT" -> { draft(player).setTargetFile(missionId); openEditor(player); }
            case "FILE_NEW" -> promptFile(player);
            case "SAVE_DRAFT" -> saveDraft(player);
            case "RESET_DRAFT" -> { draft(player).resetKeepingDuration(); openEditor(player); }
            case "CANCEL_DRAFT" -> {
                drafts.remove(player.getUniqueId());
                returnToAdminCatalog(player);
            }
        }
    }

    private void returnToAdminCatalog(Player player) {
        CatalogContext context = catalogContexts.get(player.getUniqueId());
        if (context == null) openAdminCatalog(player);
        else openAdminCatalog(player, context.group(), context.page(), context.fileFilter());
    }

    private void handleRewardInventoryClick(InventoryClickEvent event, Player player, EditorSession session) {
        int raw = event.getRawSlot();
        if (raw < 0) {
            event.setCancelled(true);
            return;
        }

        int topSize = session.inventory().getSize();
        boolean clickedTop = raw < topSize;

        if (clickedTop && isDepositSlot(raw)) {
            // Shift-click sobre la cuadrícula: elimina una recompensa virtual o
            // devuelve al inventario una plantilla real depositada por el admin.
            if (event.isShiftClick()) {
                event.setCancelled(true);
                removeRewardGridItem(player, session.inventory(), raw);
                return;
            }

            // Evita sacar una vista virtual mediante teclas de la hotbar/creativo o
            // recolectarla junto con objetos reales. Los clicks normales y el drag
            // permanecen libres dentro de la cuadrícula.
            if (event.getClick().isKeyboardClick()
                    || event.getClick().isCreativeAction()
                    || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        if (!clickedTop) {
            // Shift-click desde el inventario del jugador: mueve el stack hacia los
            // slots editables sin permitir que Bukkit lo mande a botones del menú.
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack source = event.getCurrentItem();
                if (source == null || source.getType().isAir() || isVirtualReward(source)) return;
                event.setCurrentItem(moveIntoRewardGrid(session.inventory(), source));
                return;
            }

            // Una recompensa virtual es solo una representación editable; nunca
            // puede colocarse en el inventario real del administrador.
            if (isVirtualReward(event.getCursor()) || isVirtualReward(event.getCurrentItem())) {
                event.setCancelled(true);
            }
            return;
        }

        // El resto de los slots superiores son botones/decoración del editor.
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;
        switch (action) {
            case "REWARD_ACCEPT" -> acceptRewardItems(player, session.inventory());
            case "REWARD_CANCEL" -> {
                returnTemplateDeposits(player, session.inventory());
                cleanupVirtualRewards(player);
                openEditor(player);
            }
            case "REWARD_RESET" -> {
                returnTemplateDeposits(player, session.inventory());
                cleanupVirtualRewards(player);
                openRewardItems(player);
            }
            case "REWARD_CLEAR" -> {
                clearItemRewards(player);
                returnTemplateDeposits(player, session.inventory());
                cleanupVirtualRewards(player);
                openRewardItems(player);
            }
        }
    }

    private void removeRewardGridItem(Player player, Inventory inventory, int slot) {
        ItemStack removed = inventory.getItem(slot);
        if (removed == null || removed.getType().isAir()) return;
        inventory.setItem(slot, null);

        // Las recompensas ya configuradas son vistas virtuales: quitarlas de la
        // cuadrícula solo significa eliminarlas del borrador, no regalarlas.
        if (isVirtualReward(removed)) return;

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(removed);
        if (leftovers.isEmpty()) return;

        // Si el inventario está lleno, el objeto permanece en la cuadrícula en vez
        // de caer al suelo o perderse. Un único slot nunca produce más de un stack.
        inventory.setItem(slot, leftovers.values().iterator().next());
    }

    private ItemStack moveIntoRewardGrid(Inventory inventory, ItemStack source) {
        ItemStack remaining = source.clone();

        // Primero combina únicamente con plantillas reales. No se combina con una
        // vista virtual porque eso haría que el plugin no devolviera la parte real.
        for (int slot : DEPOSIT_SLOTS) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType().isAir() || isVirtualReward(existing)) continue;
            if (!existing.isSimilar(remaining)) continue;

            int maximum = Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize());
            int room = maximum - existing.getAmount();
            if (room <= 0) continue;

            int moved = Math.min(room, remaining.getAmount());
            existing.setAmount(existing.getAmount() + moved);
            inventory.setItem(slot, existing);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() <= 0) return null;
        }

        // Después usa los slots realmente vacíos de la cuadrícula.
        for (int slot : DEPOSIT_SLOTS) {
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && !existing.getType().isAir()) continue;

            int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            inventory.setItem(slot, placed);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() <= 0) return null;
        }

        return remaining;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) return;
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || !event.getView().getTopInventory().equals(session.inventory())) return;
        if (!hasEditorPermission(player)) {
            event.setCancelled(true);
            player.closeInventory();
            plugin.message(player, "no-permission", Map.of());
            return;
        }
        if (session.type() == MenuType.REWARD_ITEMS) {
            boolean invalidTop = event.getRawSlots().stream()
                    .anyMatch(slot -> slot < session.inventory().getSize() && !isDepositSlot(slot));
            boolean virtualIntoPlayerInventory = isVirtualReward(event.getOldCursor())
                    && event.getRawSlots().stream().anyMatch(slot -> slot >= session.inventory().getSize());
            if (invalidTop || virtualIntoPlayerInventory) event.setCancelled(true);
            return;
        }
        if (event.getRawSlots().stream().anyMatch(slot -> slot < session.inventory().getSize())) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        HumanEntity viewer = event.getPlayer();
        if (!(viewer instanceof Player player)) return;
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || !event.getInventory().equals(session.inventory())) return;
        sessions.remove(player.getUniqueId());
        if (session.type() == MenuType.REWARD_ITEMS) {
            returnTemplateDeposits(player, session.inventory());
            cleanupVirtualRewards(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cleanupVirtualRewards(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        EditorSession session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null && session.type() == MenuType.REWARD_ITEMS) {
            returnTemplateDeposits(event.getPlayer(), session.inventory());
            cleanupVirtualRewards(event.getPlayer());
        }
        drafts.remove(event.getPlayer().getUniqueId());
        catalogContexts.remove(event.getPlayer().getUniqueId());
    }

    private void selectDuration(Player player, int days) {
        String rotationId = QuestDraft.rotationForDays(days);
        String enabledPath = "rotations." + rotationId + ".enabled";
        if (!plugin.getConfig().getBoolean(enabledPath, true)) {
            plugin.getConfig().set(enabledPath, true);
            plugin.saveConfig();
            player.sendMessage(plugin.prefix() + "§eSe habilitó automáticamente la rotación §f" + rotationId + "§e.");
        }
        QuestDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            drafts.put(player.getUniqueId(), QuestDraft.create(days));
        } else draft.setDurationDays(days);
        openEditor(player);
    }

    private void editMission(Player player, MissionDefinition mission) {
        if (mission == null) { openAdminCatalog(player); return; }
        drafts.put(player.getUniqueId(), QuestDraft.from(mission, plugin.getRegistry().durationDays(mission)));
        openEditor(player);
    }

    private void promptId(Player player) {
        QuestDraft draft = draft(player);
        prompts.ask(player, "Escribe el ID interno. Actual: &f" + draft.id(), answer -> {
            draft.setId(answer); openEditor(player);
        }, () -> openEditor(player));
    }

    private void promptName(Player player) {
        QuestDraft draft = draft(player);
        prompts.ask(player, "Escribe el nombre completo con colores &. Actual: " + draft.name(), answer -> {
            draft.setName(answer); openEditor(player);
        }, () -> openEditor(player));
    }

    private void promptLore(Player player) {
        QuestDraft draft = draft(player);
        prompts.ask(player, "Escribe el lore. Separa líneas usando |. Escribe limpiar para dejarlo vacío.", answer -> {
            if (answer.equalsIgnoreCase("limpiar")) draft.setLore(Collections.emptyList());
            else draft.setLore(Arrays.stream(answer.split("\\|", -1)).map(String::trim).toList());
            openEditor(player);
        }, () -> openEditor(player));
    }

    private void changeIcon(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            prompts.ask(player, "No tienes un objeto en la mano. Escribe un Material de Minecraft, por ejemplo DIAMOND_SWORD.", answer -> {
                Material material = Material.matchMaterial(answer);
                if (material == null || material.isAir()) player.sendMessage(plugin.prefix() + "§cMaterial inválido.");
                else draft(player).setIcon(new ItemStack(material));
                openEditor(player);
            }, () -> openEditor(player));
            return;
        }
        draft(player).setIcon(hand);
        player.sendMessage(plugin.prefix() + "§aIcono copiado desde tu mano principal.");
        openEditor(player);
    }

    private void promptWeight(Player player) {
        prompts.ask(player, "Escribe el peso de selección (entero mayor a 0).", answer -> {
            try { draft(player).setWeight(Integer.parseInt(answer)); }
            catch (NumberFormatException ex) { player.sendMessage(plugin.prefix() + "§cNúmero inválido."); }
            openEditor(player);
        }, () -> openEditor(player));
    }

    private void promptCommands(Player player) {
        prompts.ask(player, "Escribe comandos de consola separados por |. Usa %player%. Escribe limpiar para quitarlos.", answer -> {
            QuestDraft draft = draft(player);
            List<String> commands = answer.equalsIgnoreCase("limpiar") ? Collections.emptyList()
                    : Arrays.stream(answer.split("\\|", -1)).map(String::trim).filter(s -> !s.isBlank()).toList();
            RewardDefinition old = draft.rewards();
            draft.setRewards(new RewardDefinition(old.displayLore(), commands, old.vanillaItems(), old.mmoItems(), old.mythicItems(), old.exactItems(), old.experience()));
            openEditor(player);
        }, () -> openEditor(player));
    }

    private void promptFile(Player player) {
        prompts.ask(player, "Escribe el nombre del YAML, por ejemplo t2.yml.", answer -> {
            draft(player).setTargetFile(answer); openEditor(player);
        }, () -> openFileChooser(player));
    }

    private void startObjectiveWizard(Player player, ObjectiveType type, int editIndex) {
        if (type == null || type == ObjectiveType.CLAN_KILL) { openObjectiveCatalog(player, 1); return; }
        QuestDraft draft = draft(player);
        int configuredMax = Math.max(1, plugin.getConfig().getInt("editor.max-objectives-per-mission", 7));
        List<Integer> visualSlots = plugin.getConfig().getIntegerList("menus.interactive.detail.objective-slots");
        if (visualSlots.isEmpty()) visualSlots = plugin.getConfig().getIntegerList("menus.detail.objective-slots");
        int visualMax = visualSlots.size();
        int maxObjectives = visualMax <= 0 ? configuredMax : Math.min(configuredMax, visualMax);
        if (editIndex < 0 && draft.objectives().size() >= maxObjectives) {
            player.sendMessage(plugin.prefix() + "§cEsta versión admite hasta §f" + maxObjectives + " §cobjetivos por misión para que todos entren en el menú de detalles.");
            plugin.getSocialHook().sound(player, "error");
            openObjectiveManage(player);
            return;
        }
        ObjectiveDefinition existing = editIndex >= 0 && editIndex < draft.objectives().size() ? draft.objectives().get(editIndex) : null;
        ObjectiveWizard wizard = new ObjectiveWizard(type, editIndex, existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing.options()), existing);
        askObjectiveTarget(player, wizard);
    }

    private void askObjectiveTarget(Player player, ObjectiveWizard wizard) {
        if (wizard.type() == ObjectiveType.KILL_ANY_HOSTILE_MOB || wizard.type() == ObjectiveType.PLAYER_KILL) {
            applyObjectiveDefaults(wizard);
            askObjectiveAmount(player, wizard);
            return;
        }
        String current = currentTarget(wizard.existing());
        String prompt = switch (wizard.type()) {
            case KILL_MOB_FAMILY -> "Escribe el ID de la familia MythicMobs.";
            case KILL_MINIBOSS -> "Escribe familia:ID o IDs de miniboss separados por coma.";
            case OBTAIN_MMOITEM, DELIVER_MMOITEM -> "Escribe TYPE:ID de MMOItems. Separa varios con coma.";
            case BREAK_CUSTOM_ORE -> "Escribe IDs de recursos MDVHeadOres separados por coma.";
            case CRAFT_RECIPE -> "Escribe IDs de recetas MDVRecetas separados por coma.";
            case CRAFT_CATEGORY -> "Escribe IDs de categorías MDVRecetas separados por coma.";
            case COMPLETE_EVENT -> "Escribe IDs de eventos separados por coma.";
            case EARN_PROFESSION_EXP -> "Escribe IDs de profesiones separados por coma.";
            default -> "Escribe IDs/materiales separados por coma. Usa * para aceptar cualquiera.";
        };
        if (!current.isBlank()) prompt += " Actual: " + current + ". Escribe mantener para conservarlo.";
        prompts.ask(player, prompt, answer -> {
            if (!answer.equalsIgnoreCase("mantener") || wizard.existing() == null) applyTargetAnswer(wizard, answer);
            applyObjectiveDefaults(wizard);
            if (wizard.type() == ObjectiveType.BREAK_CUSTOM_ORE) askResourceKind(player, wizard);
            else askObjectiveAmount(player, wizard);
        }, () -> openObjectiveManage(player));
    }

    private void askResourceKind(Player player, ObjectiveWizard wizard) {
        String current = wizard.existing() == null ? "ORE" : wizard.existing().string("resource-kind", "ORE");
        prompts.ask(player, "Escribe el tipo de recurso (ORE, TREE_NODE u otro). Actual: " + current + ".", answer -> {
            if (!answer.equalsIgnoreCase("mantener")) wizard.options().put("resource-kind", answer.trim().toUpperCase(Locale.ROOT));
            else wizard.options().putIfAbsent("resource-kind", current);
            askObjectiveAmount(player, wizard);
        }, () -> openObjectiveManage(player));
    }

    private void askObjectiveAmount(Player player, ObjectiveWizard wizard) {
        long current = wizard.existing() == null ? 1 : wizard.existing().amount();
        prompts.ask(player, "Escribe la cantidad necesaria. Actual: " + current + ". Puedes escribir mantener.", answer -> {
            long amount = current;
            if (!answer.equalsIgnoreCase("mantener")) {
                try { amount = Math.max(1L, Long.parseLong(answer)); }
                catch (NumberFormatException ex) { player.sendMessage(plugin.prefix() + "§cCantidad inválida; se mantiene " + current + "."); }
            }
            wizard.amount(amount);
            askObjectiveName(player, wizard);
        }, () -> openObjectiveManage(player));
    }

    private void askObjectiveName(Player player, ObjectiveWizard wizard) {
        String current = wizard.existing() == null ? objectiveDisplay(wizard.type()) : wizard.existing().displayName();
        prompts.ask(player, "Escribe el nombre visible del objetivo. Actual: " + current + ". Puedes escribir mantener.", answer -> {
            String name = answer.equalsIgnoreCase("mantener") ? current : answer;
            finishObjective(player, wizard, name);
        }, () -> openObjectiveManage(player));
    }

    private void finishObjective(Player player, ObjectiveWizard wizard, String displayName) {
        QuestDraft draft = draft(player);
        String id = wizard.existing() == null ? uniqueObjectiveId(draft, wizard.type()) : wizard.existing().id();
        ObjectiveDefinition objective = new ObjectiveDefinition(id, wizard.type(), wizard.amount(), displayName, wizard.options());
        draft.replaceObjective(wizard.editIndex(), objective);
        player.sendMessage(plugin.prefix() + "§aObjetivo guardado en el borrador.");
        openEditor(player);
    }

    private void applyTargetAnswer(ObjectiveWizard wizard, String answer) {
        String clean = answer.trim();
        if (wizard.type() == ObjectiveType.KILL_MOB_FAMILY) {
            wizard.options().put("family", clean.toUpperCase(Locale.ROOT));
            wizard.options().remove("targets");
            return;
        }
        if (wizard.type() == ObjectiveType.KILL_MINIBOSS && clean.toLowerCase(Locale.ROOT).startsWith("familia:")) {
            wizard.options().put("family", clean.substring(clean.indexOf(':') + 1).trim().toUpperCase(Locale.ROOT));
            wizard.options().remove("targets");
            return;
        }
        List<String> targets = Arrays.stream(clean.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).map(s -> s.toUpperCase(Locale.ROOT)).toList();
        wizard.options().put("targets", targets);
        wizard.options().remove("family");
    }

    private void applyObjectiveDefaults(ObjectiveWizard wizard) {
        switch (wizard.type()) {
            case MINE_BLOCK, CUT_LOG -> wizard.options().putIfAbsent("natural-only", true);
            case HARVEST_CROP -> {
                wizard.options().putIfAbsent("natural-only", true);
                wizard.options().putIfAbsent("mature-only", true);
            }
            case CRAFT_RECIPE, CRAFT_CATEGORY, CRAFT_VANILLA_ITEM -> wizard.options().putIfAbsent("count-produced-items", true);
            case PLAYER_KILL -> wizard.options().putIfAbsent("unique-victims", true);
            case KILL_MOB_FAMILY -> wizard.options().putIfAbsent("include-minibosses", false);
            default -> { }
        }
    }

    private void deleteObjective(Player player, int index) {
        QuestDraft draft = draft(player);
        if (index >= 0 && index < draft.objectives().size()) draft.objectives().remove(index);
        openObjectiveManage(player);
    }

    private void promptExperience(Player player, String profession, int editIndex) {
        prompts.ask(player, "Escribe la cantidad de EXP para " + profession + ".", answer -> {
            try {
                long amount = Math.max(1L, Long.parseLong(answer));
                List<RewardDefinition.ExperienceReward> list = new ArrayList<>(draft(player).rewards().experience());
                RewardDefinition.ExperienceReward reward = new RewardDefinition.ExperienceReward(profession, amount);
                if (editIndex >= 0 && editIndex < list.size()) list.set(editIndex, reward); else list.add(reward);
                replaceExperience(draft(player), list);
            } catch (NumberFormatException ex) {
                player.sendMessage(plugin.prefix() + "§cCantidad inválida.");
            }
            openExperienceRewards(player);
        }, () -> openExperienceRewards(player));
    }

    private void promptProfessionExperience(Player player) {
        prompts.ask(player, "Escribe el ID exacto de la profesión MMOCore.", profession ->
                promptExperience(player, profession.trim(), -1), () -> openExperienceRewards(player));
    }

    private void editExperience(Player player, int index) {
        List<RewardDefinition.ExperienceReward> list = draft(player).rewards().experience();
        if (index < 0 || index >= list.size()) { openExperienceRewards(player); return; }
        promptExperience(player, list.get(index).profession(), index);
    }

    private void deleteExperience(Player player, int index) {
        List<RewardDefinition.ExperienceReward> list = new ArrayList<>(draft(player).rewards().experience());
        if (index >= 0 && index < list.size()) list.remove(index);
        replaceExperience(draft(player), list);
        openExperienceRewards(player);
    }

    private void replaceExperience(QuestDraft draft, List<RewardDefinition.ExperienceReward> experience) {
        RewardDefinition old = draft.rewards();
        draft.setRewards(new RewardDefinition(old.displayLore(), old.commands(), old.vanillaItems(), old.mmoItems(), old.mythicItems(), old.exactItems(), experience));
    }

    private void acceptRewardItems(Player player, Inventory inventory) {
        List<ItemStack> configuredItems = depositedItems(inventory);
        QuestDraft draft = draft(player);
        RewardDefinition old = draft.rewards();

        Map<String, Integer> vanillaAmounts = new LinkedHashMap<>();
        Map<String, Integer> mmoAmounts = new LinkedHashMap<>();
        Map<String, Integer> mythicAmounts = new LinkedHashMap<>();
        List<RewardDefinition.ExactItemReward> exact = new ArrayList<>();

        for (ItemStack stack : configuredItems) {
            Optional<MMOItemsHook.Identity> mmoIdentity = mmoItems.identify(stack);
            if (mmoIdentity.isPresent()) {
                MMOItemsHook.Identity identity = mmoIdentity.get();
                String key = identity.type() + "\u0000" + identity.id();
                mmoAmounts.merge(key, stack.getAmount(), Integer::sum);
                continue;
            }
            Optional<String> mythicIdentity = mythicItems.identify(stack);
            if (mythicIdentity.isPresent()) {
                mythicAmounts.merge(mythicIdentity.get(), stack.getAmount(), Integer::sum);
                continue;
            }
            if (!stack.hasItemMeta()) {
                vanillaAmounts.merge(stack.getType().name(), stack.getAmount(), Integer::sum);
                continue;
            }
            mergeExactReward(exact, stack);
        }

        List<RewardDefinition.VanillaItemReward> vanilla = new ArrayList<>();
        vanillaAmounts.forEach((material, amount) ->
                vanilla.add(new RewardDefinition.VanillaItemReward(material, amount)));

        List<RewardDefinition.MmoItemReward> mmo = new ArrayList<>();
        mmoAmounts.forEach((key, amount) -> {
            int separator = key.indexOf('\u0000');
            mmo.add(new RewardDefinition.MmoItemReward(key.substring(0, separator), key.substring(separator + 1), amount));
        });

        List<RewardDefinition.MythicItemReward> mythic = new ArrayList<>();
        mythicAmounts.forEach((id, amount) ->
                mythic.add(new RewardDefinition.MythicItemReward(id, amount)));

        draft.setRewards(new RewardDefinition(old.displayLore(), old.commands(), vanilla, mmo, mythic, exact, old.experience()));
        returnTemplateDeposits(player, inventory);
        cleanupVirtualRewards(player);
        player.sendMessage(plugin.prefix() + "§aRecompensas de objetos reemplazadas correctamente.");
        openEditor(player);
    }

    private void mergeExactReward(List<RewardDefinition.ExactItemReward> exact, ItemStack stack) {
        ItemStack clean = stripVirtualReward(stack);
        for (int i = 0; i < exact.size(); i++) {
            RewardDefinition.ExactItemReward existing = exact.get(i);
            if (!existing.item().isSimilar(clean)) continue;
            exact.set(i, new RewardDefinition.ExactItemReward(existing.item(), existing.amount() + clean.getAmount()));
            return;
        }
        exact.add(new RewardDefinition.ExactItemReward(clean, clean.getAmount()));
    }

    private void clearItemRewards(Player player) {
        QuestDraft draft = draft(player);
        RewardDefinition old = draft.rewards();
        draft.setRewards(new RewardDefinition(old.displayLore(), old.commands(), null, null, null, null, old.experience()));
    }

    private void returnTemplateDeposits(Player player, Inventory inventory) {
        if (inventory == null) return;
        for (int slot : DEPOSIT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            inventory.setItem(slot, null);
            if (isVirtualReward(item)) continue;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private List<ItemStack> depositedItems(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot : DEPOSIT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) result.add(stripVirtualReward(item));
        }
        return result;
    }

    private ItemStack markVirtualReward(ItemStack source) {
        ItemStack item = source == null ? new ItemStack(Material.AIR) : source.clone();
        if (item.getType().isAir()) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(virtualRewardKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isVirtualReward(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(virtualRewardKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private ItemStack stripVirtualReward(ItemStack source) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().remove(virtualRewardKey);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void clearVirtualRewardCursor(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (isVirtualReward(cursor)) player.setItemOnCursor(null);
    }

    private void cleanupVirtualRewards(Player player) {
        clearVirtualRewardCursor(player);
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (!isVirtualReward(contents[i])) continue;
            contents[i] = null;
            changed = true;
        }
        if (changed) player.getInventory().setContents(contents);
    }

    private boolean isDepositSlot(int slot) {
        for (int candidate : DEPOSIT_SLOTS) if (candidate == slot) return true;
        return false;
    }

    private void saveDraft(Player player) {
        QuestDraft draft = draft(player);
        DurationGroup targetGroup = groupForDays(draft.durationDays());
        QuestYamlService.SaveResult result = yamlService.save(draft);
        if (!result.success()) {
            player.sendMessage(plugin.prefix() + "§c" + result.message());
            plugin.getSocialHook().sound(player, "error");
            openEditor(player);
            return;
        }
        plugin.reloadPlugin();
        drafts.remove(player.getUniqueId());
        player.sendMessage(plugin.prefix() + "§a" + result.message());
        plugin.getSocialHook().sound(player, "confirm");
        openAdminCatalog(player, targetGroup, 1, result.fileName());
    }

    private QuestDraft draft(Player player) {
        return drafts.computeIfAbsent(player.getUniqueId(), id -> QuestDraft.create(1));
    }

    private ItemStack previewDraft(QuestDraft draft) {
        List<String> lore = new ArrayList<>(draft.lore());
        lore.add("");
        lore.add("&7ID: &f" + draft.id());
        lore.add("&7Duración: &f" + draft.durationDays() + " días");
        lore.add("&7Archivo: &f" + draft.targetFile());
        lore.add("&7Acceso: &f" + accessDisplay(draft.accessTier()));
        lore.add("&7Objetivos: &f" + draft.objectives().size());
        lore.add("&7Estado: " + (draft.enabled() ? "&aHabilitada" : "&7Deshabilitada"));
        lore.addAll(rewardSummary(draft.rewards()));
        return decorate(draft.icon(), draft.name(), lore, "NONE", -1, 1, null, null, null);
    }

    private ItemStack adminMissionItem(MissionDefinition mission, DurationGroup group) {
        ItemStack icon = mission.iconItem();
        if (icon == null) {
            Material material = Material.matchMaterial(mission.icon());
            icon = new ItemStack(material == null || material.isAir() ? Material.PAPER : material);
        }
        List<String> lore = new ArrayList<>(mission.lore());
        lore.add("");
        lore.add("&7ID: &f" + mission.id());
        lore.add("&7Archivo: &f" + mission.sourceFile());
        lore.add("&7Duración: &f" + plugin.getRegistry().durationDays(mission) + " días");
        lore.add("&7Pool de definición: &f" + accessDisplay(mission.accessTier()));
        lore.add("&7Objetivos: &f" + mission.objectives().size());
        lore.add("&7Estado: " + (mission.enabled() ? "&aHabilitada" : "&7Deshabilitada"));
        lore.add("");
        lore.add("&eClick izquierdo: visualizar");
        lore.add("&aClick derecho: editar");
        return decorate(icon, mission.name(), lore, "ADMIN_MISSION", -1, 1, group, null, mission.id());
    }

    private List<String> objectiveLore(ObjectiveDefinition objective) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Tipo: &f" + objective.type().name());
        lore.add("&7Cantidad: &f" + objective.amount());
        String targets = currentTarget(objective);
        if (!targets.isBlank()) lore.add("&7Objetivo(s): &f" + targets);
        return lore;
    }

    private String currentTarget(ObjectiveDefinition objective) {
        if (objective == null) return "";
        String family = objective.string("family", "");
        if (!family.isBlank()) return "familia:" + family;
        List<String> targets = objective.strings("targets");
        return targets.isEmpty() ? "" : String.join(",", targets);
    }

    private List<String> lorePreview(List<String> lore) {
        List<String> result = new ArrayList<>();
        if (lore.isEmpty()) result.add("&7Sin descripción.");
        else result.addAll(lore.stream().limit(5).toList());
        result.add("");
        result.add("&eClick para cambiar.");
        return result;
    }

    private List<String> rewardSummary(RewardDefinition reward) {
        List<String> lore = new ArrayList<>();
        int itemTypes = reward.vanillaItems().size() + reward.mmoItems().size() + reward.mythicItems().size() + reward.exactItems().size();
        lore.add("&7Objetos configurados: &f" + itemTypes);
        lore.add("&7Entradas de EXP: &f" + reward.experience().size());
        lore.add("&7Comandos: &f" + reward.commands().size());
        lore.add("");
        lore.add("&eClick para editar.");
        return lore;
    }

    private List<String> experienceSummary(RewardDefinition reward) {
        List<String> lore = new ArrayList<>();
        if (reward.experience().isEmpty()) lore.add("&7Sin experiencia configurada.");
        for (RewardDefinition.ExperienceReward exp : reward.experience()) lore.add("&7• &b" + exp.amount() + " EXP &f" + professionDisplay(exp.profession()));
        lore.add(""); lore.add("&eClick para editar.");
        return lore;
    }

    private List<String> commandSummary(RewardDefinition reward) {
        List<String> lore = new ArrayList<>();
        if (reward.commands().isEmpty()) lore.add("&7Sin comandos.");
        else reward.commands().stream().limit(5).forEach(command -> lore.add("&7• &f" + command));
        lore.add(""); lore.add("&eClick para editar.");
        return lore;
    }

    private String professionDisplay(String id) {
        String normalized = id == null || id.isBlank() ? "main" : id.trim();
        String configured = plugin.getConfig().getString("rewards.profession-display-names." + normalized.toLowerCase(Locale.ROOT));
        if (configured != null && !configured.isBlank()) return ColorUtil.strip(configured);
        if (normalized.equalsIgnoreCase("main")) return "Nivel principal";
        String[] parts = normalized.replace('-', '_').split("_+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) result.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.isEmpty() ? normalized : result.toString();
    }

    private String accessDisplay(AccessTier tier) {
        return switch (tier == null ? AccessTier.NORMAL : tier) {
            case NORMAL -> "Normal";
            case VIP1 -> "VIP 1";
            case VIP2 -> "VIP 2";
        };
    }

    private Material accessMaterial(AccessTier tier) {
        return switch (tier == null ? AccessTier.NORMAL : tier) {
            case NORMAL -> Material.PAPER;
            case VIP1 -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case VIP2 -> Material.YELLOW_STAINED_GLASS_PANE;
        };
    }

    private String uniqueObjectiveId(QuestDraft draft, ObjectiveType type) {
        String base = type.name().toLowerCase(Locale.ROOT);
        int suffix = 1;
        while (true) {
            String candidate = base + "_" + suffix++;
            boolean exists = draft.objectives().stream()
                    .anyMatch(objective -> objective.id().equalsIgnoreCase(candidate));
            if (!exists) return candidate;
        }
    }

    private String objectiveDisplay(ObjectiveType type) {
        return switch (type) {
            case MINE_BLOCK -> "Minar bloques vanilla";
            case BREAK_CUSTOM_ORE -> "Minar recurso MDVHeadOres";
            case CUT_LOG -> "Talar troncos";
            case HARVEST_CROP -> "Cosechar cultivos";
            case KILL_VANILLA_MOB -> "Matar mobs vanilla";
            case KILL_MYTHIC_MOB -> "Matar MythicMobs";
            case KILL_MOB_FAMILY -> "Matar familia MythicMobs";
            case KILL_MINIBOSS -> "Matar minibosses";
            case KILL_ANY_HOSTILE_MOB -> "Matar cualquier mob hostil";
            case CRAFT_VANILLA_ITEM -> "Craftear objetos vanilla";
            case CRAFT_RECIPE -> "Craftear receta MDVRecetas";
            case CRAFT_CATEGORY -> "Craftear categoría MDVRecetas";
            case OBTAIN_MMOITEM -> "Obtener MMOItems";
            case DELIVER_MMOITEM -> "Entregar MMOItems";
            case DELIVER_VANILLA_ITEM -> "Entregar objetos vanilla";
            case USE_CONSUMABLE -> "Usar consumibles";
            case EARN_PROFESSION_EXP -> "Ganar EXP de profesión";
            case COMPLETE_EVENT -> "Completar evento";
            case PLAYER_KILL -> "Matar jugadores diferentes";
            case CLAN_KILL -> "Bajas de clan (V2)";
        };
    }

    private String objectiveHelp(ObjectiveType type) {
        return switch (type) {
            case KILL_ANY_HOSTILE_MOB -> "Cuenta monstruos vanilla y cualquier MythicMob.";
            case PLAYER_KILL -> "Incluye protección de víctimas diferentes y antiabuso.";
            case BREAK_CUSTOM_ORE -> "Usa el evento real de MDVHeadOres.";
            case CRAFT_RECIPE, CRAFT_CATEGORY -> "Usa el evento real de MDVRecetas.";
            default -> "Configuración guiada mediante el chat.";
        };
    }

    private Material objectiveMaterial(ObjectiveType type) {
        return switch (type) {
            case MINE_BLOCK, BREAK_CUSTOM_ORE -> Material.DIAMOND_PICKAXE;
            case CUT_LOG -> Material.IRON_AXE;
            case HARVEST_CROP -> Material.WHEAT;
            case KILL_VANILLA_MOB, KILL_MYTHIC_MOB, KILL_MOB_FAMILY, KILL_ANY_HOSTILE_MOB -> Material.IRON_SWORD;
            case KILL_MINIBOSS -> Material.WITHER_SKELETON_SKULL;
            case CRAFT_VANILLA_ITEM, CRAFT_RECIPE, CRAFT_CATEGORY -> Material.CRAFTING_TABLE;
            case OBTAIN_MMOITEM -> Material.HOPPER;
            case DELIVER_MMOITEM, DELIVER_VANILLA_ITEM -> Material.CHEST;
            case USE_CONSUMABLE -> Material.POTION;
            case EARN_PROFESSION_EXP -> Material.EXPERIENCE_BOTTLE;
            case COMPLETE_EVENT -> Material.CLOCK;
            case PLAYER_KILL, CLAN_KILL -> Material.DIAMOND_SWORD;
        };
    }

    private DurationGroup groupFor(MissionDefinition mission) { return groupForDays(plugin.getRegistry().durationDays(mission)); }
    private DurationGroup groupForDays(int days) {
        for (DurationGroup group : DurationGroup.values()) if (group.accepts(days)) return group;
        return DurationGroup.ONE_DAY;
    }

    private DurationGroup parseGroup(String raw, DurationGroup fallback) {
        if (raw == null) return fallback == null ? DurationGroup.ONE_DAY : fallback;
        try { return DurationGroup.valueOf(raw); }
        catch (IllegalArgumentException ex) { return fallback == null ? DurationGroup.ONE_DAY : fallback; }
    }

    private ObjectiveType parseType(String raw) {
        if (raw == null) return null;
        try { return ObjectiveType.valueOf(raw); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private ItemStack backHead(String action, String name, List<String> lore) {
        return tag(ItemUtil.backHead(plugin, name, lore), action, -1, 1, null, null, null);
    }

    private ItemStack item(Material material, String name, List<String> lore, String action,
                           int index, int page, DurationGroup group, ObjectiveType type, String missionId) {
        ItemStack item = plugin.getSocialHook().button(material, 1, name, lore, action, "default");
        return tag(item, action, index, page, group, type, missionId);
    }

    private ItemStack decorate(ItemStack source, String name, List<String> lore, String action,
                               int index, int page, DurationGroup group, ObjectiveType type, String missionId) {
        ItemStack item = ItemUtil.hideNativeTooltip(source == null ? new ItemStack(Material.PAPER) : source);
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(name));
            meta.setLore(ColorUtil.color(lore));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return tag(item, action, index, page, group, type, missionId);
    }

    private ItemStack tag(ItemStack item, String action, int index, int page,
                          DurationGroup group, ObjectiveType type, String missionId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(actionKey, PersistentDataType.STRING, action);
        pdc.set(indexKey, PersistentDataType.INTEGER, index);
        pdc.set(pageKey, PersistentDataType.INTEGER, page);
        if (group != null) pdc.set(groupKey, PersistentDataType.STRING, group.name());
        if (type != null) pdc.set(typeKey, PersistentDataType.STRING, type.name());
        if (missionId != null) pdc.set(missionKey, PersistentDataType.STRING, missionId);
        item.setItemMeta(meta);
        return item;
    }

    private void open(Player player, Inventory inventory, MenuType type, int page, DurationGroup group, String missionId) {
        open(player, inventory, type, page, group, missionId, ALL_FILES);
    }

    private void open(Player player, Inventory inventory, MenuType type, int page, DurationGroup group,
                      String missionId, String fileFilter) {
        sessions.put(player.getUniqueId(), new EditorSession(inventory, type, page, group, missionId,
                fileFilter == null || fileFilter.isBlank() ? ALL_FILES : fileFilter));
        InventoryView view = player.openInventory(inventory);
        if (view == null) sessions.remove(player.getUniqueId());
        else plugin.getSocialHook().sound(player, "open");
    }

    private enum MenuType {
        DURATION, EDITOR, ACCESS_TIER, OBJECTIVE_CATALOG, OBJECTIVE_MANAGE, REWARD_ITEMS,
        XP_REWARDS, FILE_CHOOSER, ADMIN_CATALOG, ADMIN_PREVIEW
    }

    private record EditorSession(Inventory inventory, MenuType type, int page, DurationGroup group,
                                 String missionId, String fileFilter) { }
    private record CatalogContext(DurationGroup group, int page, String fileFilter) { }

    private static final class ObjectiveWizard {
        private final ObjectiveType type;
        private final int editIndex;
        private final Map<String, Object> options;
        private final ObjectiveDefinition existing;
        private long amount;

        private ObjectiveWizard(ObjectiveType type, int editIndex, Map<String, Object> options, ObjectiveDefinition existing) {
            this.type = type;
            this.editIndex = editIndex;
            this.options = options;
            this.existing = existing;
            this.amount = existing == null ? 1L : existing.amount();
        }
        ObjectiveType type() { return type; }
        int editIndex() { return editIndex; }
        Map<String, Object> options() { return options; }
        ObjectiveDefinition existing() { return existing; }
        long amount() { return amount; }
        void amount(long value) { amount = Math.max(1L, value); }
    }
}
