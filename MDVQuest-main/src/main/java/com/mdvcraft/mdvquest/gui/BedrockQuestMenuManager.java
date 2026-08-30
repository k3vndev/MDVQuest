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
import com.mdvcraft.mdvquest.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adaptación nativa Bedrock de los dos menús públicos de MDVQuest.
 *
 * VIEWER: sólo consulta los contratos ya aceptados y su progreso.
 * NPC: catálogo interactivo para aceptar, entregar, reclamar y cancelar.
 *
 * Java conserva exactamente los inventarios de QuestMenuManager.
 */
public final class BedrockQuestMenuManager {
    private static final String VIEWER_RESOURCE = "MenusBedrock/quest_viewer.yml";
    private static final String NPC_RESOURCE = "MenusBedrock/quest_npc.yml";

    private final MDVQuestPlugin plugin;
    private final RotationService rotations;
    private final ProgressService progress;
    private final RewardService rewards;
    private final DeliveryService deliveries;
    private final AccessService access;

    private final AtomicLong sessionSequence = new AtomicLong();
    private final Map<UUID, Long> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> consumedSessions = new ConcurrentHashMap<>();
    private final Map<Mode, YamlConfiguration> menus = new EnumMap<>(Mode.class);

    private boolean floodgateAvailable;

    public BedrockQuestMenuManager(MDVQuestPlugin plugin, RotationService rotations, ProgressService progress,
                                   RewardService rewards, DeliveryService deliveries, AccessService access) {
        this.plugin = plugin;
        this.rotations = rotations;
        this.progress = progress;
        this.rewards = rewards;
        this.deliveries = deliveries;
        this.access = access;
        reload();
    }

    public void reload() {
        floodgateAvailable = Bukkit.getPluginManager().isPluginEnabled("floodgate");
        ensureMenuFile(VIEWER_RESOURCE);
        ensureMenuFile(NPC_RESOURCE);
        menus.put(Mode.VIEWER, YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), VIEWER_RESOURCE)));
        menus.put(Mode.NPC, YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), NPC_RESOURCE)));
        plugin.getLogger().info("Menus Bedrock de MDVQuest cargados"
                + (floodgateAvailable ? " (Floodgate detectado)." : " (Floodgate no detectado; Java sin cambios)."));
    }

    public void clear(Player player) {
        if (player == null) return;
        activeSessions.remove(player.getUniqueId());
        consumedSessions.remove(player.getUniqueId());
    }

    public void shutdown() {
        activeSessions.clear();
        consumedSessions.clear();
    }

    /** Devuelve true si el jugador es Bedrock y el menú fue manejado como Form. */
    public boolean openViewer(Player player) {
        if (!isBedrock(player)) return false;
        openCategorySelector(player, Mode.VIEWER);
        return true;
    }

    /** Devuelve true si el jugador es Bedrock y el menú fue manejado como Form. */
    public boolean openInteractive(Player player) {
        if (!isBedrock(player)) return false;
        openCategorySelector(player, Mode.NPC);
        return true;
    }

    private boolean isBedrock(Player player) {
        if (player == null || !plugin.getConfig().getBoolean("bedrock.enabled", true)) return false;
        if (!floodgateAvailable) floodgateAvailable = Bukkit.getPluginManager().isPluginEnabled("floodgate");
        if (!floodgateAvailable) return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void openCategorySelector(Player player, Mode mode) {
        plugin.synchronizeRotations();
        List<MissionInstance> all = rotations.activeInstances();
        YamlConfiguration yaml = menu(mode);

        Map<String, String> base = Map.of(
                "total", String.valueOf(all.size()),
                "mode", mode == Mode.NPC ? "NPC" : "VIEWER"
        );
        String title = text(yaml, "category.title", mode == Mode.NPC ? "&8&lEncargado de Misiones" : "&8&lMis Contratos", base);
        List<String> contentLines = lines(yaml, "category.content", mode == Mode.NPC
                ? List.of("&7Selecciona una duración para consultar los encargos disponibles.")
                : List.of("&7Selecciona una duración para consultar tus contratos aceptados."), base);

        List<FormButton> buttons = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (DurationGroup group : DurationGroup.values()) {
            List<MissionInstance> category = all.stream()
                    .filter(instance -> group.accepts(plugin.getRegistry().durationDays(instance.definition())))
                    .toList();
            long visibleTotal = mode == Mode.VIEWER
                    ? category.stream().filter(instance -> progress.accepted(player, instance)).count()
                    : category.size();
            long completed = category.stream()
                    .filter(instance -> progress.accepted(player, instance))
                    .filter(instance -> progress.isMissionComplete(player, instance))
                    .count();
            int accepted = progress.acceptedCount(player, group);
            int limit = progress.contractLimit(player, group);
            long resetAt = nextResetAt(group, now);

            Map<String, String> values = values(base,
                    "category", group.display(),
                    "accepted", String.valueOf(accepted),
                    "limit", String.valueOf(limit),
                    "completed", String.valueOf(completed),
                    "total", String.valueOf(visibleTotal),
                    "remaining", resetAt <= 0 ? "Sin rotación" : TimeUtil.remaining(resetAt, now));
            String fallback = "&e&l{category}\n&r&7Contratos: &f{accepted}/{limit} &8• &7Misiones: &f{total}";
            String buttonText = text(yaml, "category.categories." + group.configKey() + ".text", fallback, values);
            buttons.add(new FormButton(buttonText, () -> openMissionList(player, mode, group, 1)));
        }

        String backText = text(yaml, "category.back.text", "&6&lVolver", base);
        buttons.add(new FormButton(backText, () -> runBackCommand(player, yaml)));
        sendForm(player, title, join(contentLines), buttons);
    }

    private void openMissionList(Player player, Mode mode, DurationGroup group, int requestedPage) {
        plugin.synchronizeRotations();
        YamlConfiguration yaml = menu(mode);
        long now = System.currentTimeMillis();

        List<MissionInstance> filtered = rotations.activeInstances().stream()
                .filter(instance -> group.accepts(plugin.getRegistry().durationDays(instance.definition())))
                .filter(instance -> mode == Mode.NPC || progress.accepted(player, instance))
                .sorted(Comparator
                        .comparingInt((MissionInstance instance) -> instance.accessTier().level())
                        .thenComparingLong(MissionInstance::expiresAt)
                        .thenComparing(instance -> instance.definition().id()))
                .toList();

        int pageSize = pageSize(player, yaml);
        int pages = Math.max(1, (int) Math.ceil(filtered.size() / (double) pageSize));
        int page = Math.max(1, Math.min(pages, requestedPage));
        int accepted = progress.acceptedCount(player, group);
        int limit = progress.contractLimit(player, group);
        Map<String, String> base = Map.of(
                "category", group.display(),
                "accepted", String.valueOf(accepted),
                "limit", String.valueOf(limit),
                "page", String.valueOf(page),
                "pages", String.valueOf(pages),
                "total", String.valueOf(filtered.size())
        );

        String title = text(yaml, "mission-list.title", "&8&l{category}", base);
        List<String> content = lines(yaml, "mission-list.content", List.of(
                "&7Contratos: &f{accepted}/{limit}",
                "&7Página: &f{page}/{pages}"
        ), base);
        if (filtered.isEmpty()) {
            content.add(text(yaml, "mission-list.empty", mode == Mode.NPC
                    ? "&7No hay misiones activas en esta categoría."
                    : "&7No tienes contratos aceptados en esta categoría.", base));
        }

        List<FormButton> buttons = new ArrayList<>();
        int start = (page - 1) * pageSize;
        int end = Math.min(filtered.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            MissionInstance instance = filtered.get(index);
            String state = stateKey(player, instance);
            long completeObjectives = instance.definition().objectives().stream()
                    .filter(objective -> progress.progress(player, instance, objective) >= objective.amount())
                    .count();
            Map<String, String> values = missionValues(player, mode, instance, group, now);
            values.put("objectives_done", String.valueOf(completeObjectives));
            values.put("objectives_total", String.valueOf(instance.definition().objectives().size()));
            String fallback = switch (state) {
                case "claimed" -> "&8&l{mission}\n&r&7Recompensa reclamada";
                case "completed" -> "&a&l{mission}\n&r&aCompletada &8• &7{objectives_done}/{objectives_total}";
                case "locked" -> "&b&l{mission}\n&r&cRequiere {rank}";
                case "accepted" -> "&e&l{mission}\n&r&7Progreso: &f{objectives_done}/{objectives_total}";
                default -> "&f&l{mission}\n&r&eDisponible para aceptar";
            };
            String buttonText = text(yaml, "mission-list.mission." + state, fallback, values);
            buttons.add(new FormButton(buttonText, () -> openDetail(player, mode, group, instance.id(), page)));
        }

        if (page > 1) {
            buttons.add(new FormButton(text(yaml, "mission-list.previous.text", "&ePágina anterior", base),
                    () -> openMissionList(player, mode, group, page - 1)));
        }
        if (page < pages) {
            buttons.add(new FormButton(text(yaml, "mission-list.next.text", "&aPágina siguiente", base),
                    () -> openMissionList(player, mode, group, page + 1)));
        }
        buttons.add(new FormButton(text(yaml, "mission-list.back.text", "&6Volver a categorías", base),
                () -> openCategorySelector(player, mode)));
        sendForm(player, title, join(content), buttons);
    }

    private void openDetail(Player player, Mode mode, DurationGroup group, String instanceId, int returnPage) {
        plugin.synchronizeRotations();
        MissionInstance instance = rotations.instance(instanceId);
        if (instance == null || !instance.isActive(System.currentTimeMillis())) {
            plugin.message(player, "mission-expired", Map.of());
            openMissionList(player, mode, group, returnPage);
            return;
        }
        if (mode == Mode.VIEWER && !progress.accepted(player, instance)) {
            openMissionList(player, mode, group, returnPage);
            return;
        }

        YamlConfiguration yaml = menu(mode);
        long now = System.currentTimeMillis();
        Map<String, String> values = missionValues(player, mode, instance, group, now);
        String title = text(yaml, "detail.title", "&8&l{mission}", values);

        List<String> description = new ArrayList<>();
        if (instance.definition().lore().isEmpty()) {
            description.add(text(yaml, "detail.no-description", "&8Sin descripción adicional.", values));
        } else {
            for (String line : instance.definition().lore()) description.add(render(line, values));
        }
        List<String> objectiveLines = objectiveLines(player, instance, yaml, values);
        List<String> rewardLines = rewardLines(instance.definition().rewards(), yaml, values);

        List<String> templates = yaml.getStringList("detail.content");
        if (templates.isEmpty()) {
            templates = List.of(
                    "&7Estado: {state}",
                    "&7Expira en: &f{remaining}",
                    "",
                    "&e&lDescripción",
                    "{description}",
                    "",
                    "&e&lObjetivos",
                    "{objectives}",
                    "",
                    "&a&lRecompensas",
                    "{rewards}"
            );
        }
        List<String> content = expandDetailTemplates(templates, values, description, objectiveLines, rewardLines);

        List<FormButton> buttons = new ArrayList<>();
        boolean acceptedMission = progress.accepted(player, instance);
        boolean complete = progress.isMissionComplete(player, instance);
        boolean claimed = progress.claimed(player, instance);
        boolean locked = !access.hasAccess(player, instance.accessTier());

        if (mode == Mode.NPC) {
            if (!acceptedMission && !claimed && !locked) {
                buttons.add(new FormButton(text(yaml, "detail.actions.accept.text",
                        "&a&lAceptar contrato\n&r&7Añadir a mis contratos.", values), () -> {
                    progress.acceptMission(player, instance);
                    openDetail(player, mode, group, instance.id(), returnPage);
                }));
            }
            if (acceptedMission && !complete && hasPendingDelivery(player, instance)) {
                buttons.add(new FormButton(text(yaml, "detail.actions.deliver-all.text",
                        "&6&lEntregar objetos\n&r&7Entrega todo lo disponible.", values), () -> {
                    deliveries.deliverAll(player, instance);
                    openDetail(player, mode, group, instance.id(), returnPage);
                }));
            }
            if (acceptedMission && complete && !claimed && !locked) {
                buttons.add(new FormButton(text(yaml, "detail.actions.claim.text",
                        "&a&lReclamar recompensa", values), () -> {
                    boolean started = rewards.claim(player, instance);
                    if (started) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (player.isOnline()) openDetail(player, mode, group, instance.id(), returnPage);
                        }, 3L);
                    } else {
                        openDetail(player, mode, group, instance.id(), returnPage);
                    }
                }));
            }
            if (acceptedMission && !complete && !claimed) {
                buttons.add(new FormButton(text(yaml, "detail.actions.cancel.text",
                        "&c&lCancelar contrato\n&r&7Perderás su progreso.", values),
                        () -> openCancelConfirm(player, group, instance.id(), returnPage)));
            }
        }

        buttons.add(new FormButton(text(yaml, "detail.actions.back.text", "&6Volver", values),
                () -> openMissionList(player, mode, group, returnPage)));
        sendForm(player, title, join(content), buttons);
    }

    private void openCancelConfirm(Player player, DurationGroup group, String instanceId, int returnPage) {
        MissionInstance instance = rotations.instance(instanceId);
        if (instance == null) {
            openMissionList(player, Mode.NPC, group, returnPage);
            return;
        }
        YamlConfiguration yaml = menu(Mode.NPC);
        Map<String, String> values = missionValues(player, Mode.NPC, instance, group, System.currentTimeMillis());
        String title = text(yaml, "cancel-confirm.title", "&c&lCancelar contrato", values);
        List<String> content = lines(yaml, "cancel-confirm.content", List.of(
                "&7¿Seguro que quieres cancelar &f{mission}&7?",
                "&cPerderás todo el progreso de este contrato."
        ), values);
        List<FormButton> buttons = List.of(
                new FormButton(text(yaml, "cancel-confirm.confirm.text", "&c&lSí, cancelar", values), () -> {
                    progress.cancelMission(player, instance);
                    openMissionList(player, Mode.NPC, group, returnPage);
                }),
                new FormButton(text(yaml, "cancel-confirm.back.text", "&6Volver", values),
                        () -> openDetail(player, Mode.NPC, group, instance.id(), returnPage))
        );
        sendForm(player, title, join(content), buttons);
    }

    private List<String> objectiveLines(Player player, MissionInstance instance, YamlConfiguration yaml,
                                        Map<String, String> base) {
        List<String> result = new ArrayList<>();
        for (ObjectiveDefinition objective : instance.definition().objectives()) {
            long current = progress.progress(player, instance, objective);
            boolean complete = current >= objective.amount();
            Map<String, String> values = values(base,
                    "objective", objective.displayName(),
                    "progress", String.valueOf(current),
                    "required", String.valueOf(objective.amount()),
                    "objective_state", complete ? "complete" : "pending",
                    "icon", complete
                            ? yaml.getString("detail.objectives.complete-icon", "&a✔")
                            : yaml.getString("detail.objectives.pending-icon", "&7•"),
                    "delivery", objective.type().isDelivery() ? "true" : "false");
            String path = complete ? "detail.objectives.complete-format"
                    : objective.type().isDelivery() ? "detail.objectives.delivery-format" : "detail.objectives.pending-format";
            String fallback = complete
                    ? "{icon} &f{objective} &7({progress}/{required})"
                    : objective.type().isDelivery()
                    ? "{icon} &f{objective} &7({progress}/{required}) &6[Entrega]"
                    : "{icon} &f{objective} &7({progress}/{required})";
            result.add(text(yaml, path, fallback, values));
        }
        if (result.isEmpty()) result.add(text(yaml, "detail.objectives.empty", "&7Sin objetivos.", base));
        return result;
    }

    private List<String> rewardLines(RewardDefinition reward, YamlConfiguration yaml, Map<String, String> base) {
        List<String> result = new ArrayList<>();
        for (String line : reward.displayLore()) result.add(render(line, base));

        for (RewardDefinition.ExperienceReward exp : reward.experience()) {
            Map<String, String> values = values(base,
                    "amount", String.valueOf(exp.amount()),
                    "target", professionDisplay(exp.profession()));
            result.add(text(yaml, "detail.rewards.experience-format", "&7• &b{amount} EXP &f{target}", values));
        }
        for (RewardDefinition.VanillaItemReward configured : reward.vanillaItems()) {
            Map<String, String> values = values(base,
                    "amount", String.valueOf(configured.amount()),
                    "item", ItemDisplayUtil.prettify(configured.material()));
            result.add(text(yaml, "detail.rewards.vanilla-format", "&7• &f{amount}x {item}", values));
        }
        for (RewardDefinition.MmoItemReward configured : reward.mmoItems()) {
            ItemStack item = plugin.getMmoItemsHook().build(configured.type(), configured.id(), 1);
            Map<String, String> values = values(base,
                    "amount", String.valueOf(configured.amount()),
                    "item", item == null ? ItemDisplayUtil.prettify(configured.id()) : ItemDisplayUtil.plainName(item));
            result.add(text(yaml, "detail.rewards.mmoitem-format", "&7• &d{amount}x {item}", values));
        }
        for (RewardDefinition.MythicItemReward configured : reward.mythicItems()) {
            ItemStack item = plugin.getMythicItemsHook().build(configured.id(), 1);
            Map<String, String> values = values(base,
                    "amount", String.valueOf(configured.amount()),
                    "item", item == null ? ItemDisplayUtil.prettify(configured.id()) : ItemDisplayUtil.plainName(item));
            result.add(text(yaml, "detail.rewards.mythic-item-format", "&7• &5{amount}x {item}", values));
        }
        for (RewardDefinition.ExactItemReward configured : reward.exactItems()) {
            Map<String, String> values = values(base,
                    "amount", String.valueOf(configured.amount()),
                    "item", ItemDisplayUtil.plainName(configured.item()));
            result.add(text(yaml, "detail.rewards.exact-item-format", "&7• &f{amount}x {item}", values));
        }
        if (result.isEmpty()) {
            String fallback = reward.commands().isEmpty()
                    ? "&7• Sin recompensa configurada"
                    : "&7• Recompensa entregada por el servidor";
            result.add(text(yaml, "detail.rewards.empty", fallback, base));
        }
        return result;
    }

    private Map<String, String> missionValues(Player player, Mode mode, MissionInstance instance, DurationGroup group, long now) {
        Map<String, String> values = new LinkedHashMap<>();
        boolean acceptedMission = progress.accepted(player, instance);
        boolean complete = progress.isMissionComplete(player, instance);
        boolean claimed = progress.claimed(player, instance);
        boolean locked = !access.hasAccess(player, instance.accessTier());
        int acceptedCount = progress.acceptedCount(player, group);
        int limit = progress.contractLimit(player, group);
        String stateKey = claimed ? "claimed" : complete ? "completed" : locked ? "locked" : acceptedMission ? "accepted" : "available";
        String stateLabel = menu(mode).getString("states." + stateKey, defaultStateLabel(stateKey));

        values.put("mission", instance.definition().name());
        values.put("mission_plain", ColorUtil.strip(instance.definition().name()));
        values.put("state", stateLabel);
        values.put("state_key", stateKey);
        values.put("remaining", TimeUtil.remaining(instance.expiresAt(), now));
        values.put("category", group.display());
        values.put("accepted", String.valueOf(acceptedCount));
        values.put("limit", String.valueOf(limit));
        values.put("rank", access.displayName(instance.accessTier()));
        values.put("access", instance.accessTier().key());
        values.put("player", player.getName());
        return values;
    }

    private String stateKey(Player player, MissionInstance instance) {
        if (progress.claimed(player, instance)) return "claimed";
        if (progress.isMissionComplete(player, instance)) return "completed";
        if (!access.hasAccess(player, instance.accessTier())) return "locked";
        if (progress.accepted(player, instance)) return "accepted";
        return "available";
    }

    private String defaultStateLabel(String key) {
        return switch (key) {
            case "claimed" -> "&8Reclamada";
            case "completed" -> "&aCompletada";
            case "locked" -> "&cBloqueada";
            case "accepted" -> "&eEn progreso";
            default -> "&fDisponible";
        };
    }

    private boolean hasPendingDelivery(Player player, MissionInstance instance) {
        for (ObjectiveDefinition objective : instance.definition().objectives()) {
            if (objective.type().isDelivery() && progress.progress(player, instance, objective) < objective.amount()) return true;
        }
        return false;
    }

    private DurationGroup groupFor(MissionInstance instance) {
        int days = plugin.getRegistry().durationDays(instance.definition());
        for (DurationGroup group : DurationGroup.values()) if (group.accepts(days)) return group;
        return DurationGroup.ONE_DAY;
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

    private String professionDisplay(String id) {
        String normalized = id == null || id.isBlank() ? "main" : id.trim();
        String configured = plugin.getConfig().getString("rewards.profession-display-names." + normalized.toLowerCase(Locale.ROOT));
        if (configured != null && !configured.isBlank()) return ColorUtil.strip(configured);
        if (normalized.equalsIgnoreCase("main")) return "Nivel principal";
        return ItemDisplayUtil.prettify(normalized);
    }

    private void runBackCommand(Player player, YamlConfiguration yaml) {
        String command = yaml.getString("back-command", modeFallbackBackCommand());
        if (command == null || command.isBlank()) return;
        player.performCommand(command.startsWith("/") ? command.substring(1) : command);
    }

    private String modeFallbackBackCommand() {
        return plugin.getConfig().getString("menus.viewer.back-command", "social");
    }

    private int pageSize(Player player, YamlConfiguration yaml) {
        int standard = clamp(yaml.getInt("mission-list.page-size",
                plugin.getConfig().getInt("bedrock.page-size", 6)), 3, 12);
        if (!isTouch(player)) return standard;
        return clamp(yaml.getInt("mission-list.mobile-page-size",
                plugin.getConfig().getInt("bedrock.mobile-page-size", 5)), 3, 8);
    }

    private boolean isTouch(Player player) {
        if (!isBedrock(player)) return false;
        try {
            Object floodgatePlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (floodgatePlayer == null) return false;
            for (String methodName : List.of("getInputMode", "getDeviceOs", "getDeviceOS")) {
                try {
                    Method method = floodgatePlayer.getClass().getMethod(methodName);
                    Object value = method.invoke(floodgatePlayer);
                    String name = value == null ? "" : value.toString().toUpperCase(Locale.ROOT);
                    if (name.contains("TOUCH") || name.contains("ANDROID") || name.contains("IOS")
                            || name.contains("FIRE_OS") || name.contains("FIREOS")) return true;
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void sendForm(Player player, String title, String content, List<FormButton> buttons) {
        if (!player.isOnline()) return;
        long session = beginSession(player);
        List<FormButton> safeButtons = buttons == null ? List.of() : List.copyOf(buttons);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(ColorUtil.color(title))
                .content(ColorUtil.color(content));
        for (FormButton button : safeButtons) builder.button(ColorUtil.color(button.text()));
        if (safeButtons.isEmpty()) builder.button(ColorUtil.color("&6Volver"));

        builder.closedResultHandler(() -> { });
        builder.validResultHandler(response -> {
            int index = response.clickedButtonId();
            runFormAction(player, session, () -> {
                if (safeButtons.isEmpty()) return;
                if (index < 0 || index >= safeButtons.size()) return;
                safeButtons.get(index).action().run();
            });
        });

        try {
            FloodgatePlayer floodgatePlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (floodgatePlayer == null || !floodgatePlayer.sendForm(builder.build())) {
                plugin.getLogger().warning("No se pudo enviar Form Bedrock de MDVQuest a " + player.getName() + ".");
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Error enviando Form Bedrock a " + player.getName() + ": " + ex.getMessage());
        }
    }

    private long beginSession(Player player) {
        long token = sessionSequence.incrementAndGet();
        activeSessions.put(player.getUniqueId(), token);
        consumedSessions.remove(player.getUniqueId());
        return token;
    }

    private void runFormAction(Player player, long expectedSession, Runnable action) {
        if (player == null || action == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        synchronized (this) {
            Long active = activeSessions.get(uuid);
            if (active == null || active.longValue() != expectedSession) return;
            Long consumed = consumedSessions.get(uuid);
            if (consumed != null && consumed.longValue() == expectedSession) return;
            consumedSessions.put(uuid, expectedSession);
        }
        long delay = Math.max(0L, Math.min(5L, plugin.getConfig().getLong("bedrock.navigation-delay-ticks", 1L)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Long active = activeSessions.get(uuid);
            if (active == null || active.longValue() != expectedSession) return;
            action.run();
        }, delay);
    }

    private List<String> expandDetailTemplates(List<String> templates, Map<String, String> values,
                                               List<String> description, List<String> objectives,
                                               List<String> rewardLines) {
        List<String> result = new ArrayList<>();
        for (String raw : templates) {
            String marker = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            switch (marker) {
                case "{description}" -> result.addAll(description);
                case "{objectives}" -> result.addAll(objectives);
                case "{rewards}" -> result.addAll(rewardLines);
                default -> result.add(render(raw, values));
            }
        }
        return result;
    }

    private String text(YamlConfiguration yaml, String path, String fallback, Map<String, String> values) {
        return render(yaml.getString(path, fallback), values);
    }

    private List<String> lines(YamlConfiguration yaml, String path, List<String> fallback, Map<String, String> values) {
        List<String> configured = yaml.getStringList(path);
        if (configured.isEmpty()) configured = fallback;
        List<String> result = new ArrayList<>();
        for (String line : configured) result.add(render(line, values));
        return result;
    }

    private String render(String raw, Map<String, String> values) {
        String out = raw == null ? "" : raw;
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                out = out.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return out;
    }

    private String join(List<String> lines) {
        return String.join("\n", lines == null ? List.of() : lines);
    }

    private Map<String, String> values(Map<String, String> base, String... entries) {
        Map<String, String> result = new LinkedHashMap<>();
        if (base != null) result.putAll(base);
        for (int i = 0; i + 1 < entries.length; i += 2) result.put(entries[i], entries[i + 1]);
        return result;
    }

    private YamlConfiguration menu(Mode mode) {
        return menus.getOrDefault(mode, new YamlConfiguration());
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void ensureMenuFile(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try {
            if (!file.exists()) {
                plugin.saveResource(resourcePath, false);
                return;
            }
            // Añade claves nuevas sin reemplazar ninguna personalización existente.
            try (InputStream input = plugin.getResource(resourcePath)) {
                if (input == null) return;
                YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(input, StandardCharsets.UTF_8));
                current.setDefaults(defaults);
                current.options().copyDefaults(true);
                current.save(file);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo preparar " + resourcePath + ": " + ex.getMessage());
        }
    }

    private enum Mode { VIEWER, NPC }

    private record FormButton(String text, Runnable action) { }
}
