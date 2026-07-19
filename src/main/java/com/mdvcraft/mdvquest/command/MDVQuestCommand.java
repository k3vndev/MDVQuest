package com.mdvcraft.mdvquest.command;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.hook.MMOItemsHook;
import com.mdvcraft.mdvquest.model.MissionInstance;
import com.mdvcraft.mdvquest.model.ObjectiveType;
import com.mdvcraft.mdvquest.service.RotationService;
import com.mdvcraft.mdvquest.util.ColorUtil;
import com.mdvcraft.mdvquest.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MDVQuestCommand implements CommandExecutor, TabCompleter {
    private final MDVQuestPlugin plugin;

    public MDVQuestCommand(MDVQuestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Usa /mdvquest status desde consola.");
                return true;
            }
            if (!player.hasPermission("mdvquest.use")) {
                plugin.message(player, "no-permission", Map.of());
                return true;
            }
            plugin.getMenuManager().openMain(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean editorCommand = sub.equals("admin") || sub.equals("gestionar")
                || sub.equals("crear") || sub.equals("editor") || sub.equals("new");

        if (editorCommand) {
            if (!sender.hasPermission("mdvquest.editor") && !sender.hasPermission("mdvquest.admin")) {
                sender.sendMessage(plugin.prefix() + ColorUtil.color("&cNo tienes permiso."));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("El editor visual solo puede abrirse dentro del juego.");
                return true;
            }
            if (sub.equals("crear") || sub.equals("editor") || sub.equals("new")
                    || (args.length >= 2 && (args[1].equalsIgnoreCase("crear") || args[1].equalsIgnoreCase("new")))) {
                plugin.getEditorManager().openDurationPicker(player);
            } else {
                plugin.getEditorManager().openAdminCatalog(player);
            }
            return true;
        }

        if (!sender.hasPermission("mdvquest.admin")) {
            sender.sendMessage(plugin.prefix() + ColorUtil.color("&cNo tienes permiso."));
            return true;
        }

        switch (sub) {
            case "reload" -> {
                plugin.reloadPlugin();
                plugin.message(sender, "reload", Map.of());
            }
            case "status" -> showStatus(sender);
            case "force", "forzar" -> handleForce(sender, args);
            case "rotate", "reroll" -> handleReroll(sender, args);
            case "event" -> reportBridge(sender, args, ObjectiveType.COMPLETE_EVENT);
            case "profexp" -> reportBridge(sender, args, ObjectiveType.EARN_PROFESSION_EXP);
            case "report" -> reportGeneric(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void handleForce(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mdvquest.admin.force")) {
            plugin.message(sender, "no-permission", Map.of());
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.color("&e/mdvquest force <id-de-mision>"));
            return;
        }

        RotationService.ForceResult result = plugin.forceMission(args[1]);
        MissionInstance instance = result.instance();
        switch (result.status()) {
            case ADDED -> plugin.message(sender, "force-mission-success", Map.of(
                    "mission", instance.definition().id(),
                    "rotation", instance.rotationId(),
                    "remaining", TimeUtil.remaining(instance.expiresAt(), System.currentTimeMillis())
            ));
            case ALREADY_ACTIVE -> plugin.message(sender, "force-mission-already-active", Map.of(
                    "mission", instance.definition().id(),
                    "rotation", instance.rotationId()
            ));
            case ROTATION_DISABLED -> plugin.message(sender, "force-mission-rotation-disabled", Map.of(
                    "mission", args[1]
            ));
            case ROTATION_NOT_FOUND -> plugin.message(sender, "force-mission-rotation-missing", Map.of(
                    "mission", args[1]
            ));
            case NOT_FOUND -> plugin.message(sender, "force-mission-not-found", Map.of(
                    "mission", args[1]
            ));
            case DATABASE_ERROR -> plugin.message(sender, "force-mission-database-error", Map.of(
                    "mission", args[1]
            ));
        }
    }

    private void handleReroll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mdvquest.admin.reroll")) {
            plugin.message(sender, "no-permission", Map.of());
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.color("&e/mdvquest reroll <rotacion|all> confirmar"));
            return;
        }
        String target = args[1].toLowerCase(Locale.ROOT);
        boolean all = target.equals("all") || target.equals("todas") || target.equals("todo");
        if (!all && plugin.getRegistry().rotation(target) == null) {
            sender.sendMessage(plugin.prefix() + ColorUtil.color("&cRotacion desconocida: &f" + target));
            return;
        }
        boolean confirmed = args.length >= 3 && (args[2].equalsIgnoreCase("confirmar") || args[2].equalsIgnoreCase("confirm"));
        if (!confirmed) {
            plugin.message(sender, all ? "reroll-all-warning" : "reroll-warning", Map.of(
                    "rotation", all ? "todas" : target,
                    "command", "/mdvquest reroll " + (all ? "all" : target) + " confirmar"
            ));
            return;
        }
        if (all) {
            int count = plugin.forceRotateAll();
            if (count > 0) plugin.message(sender, "reroll-all-success", Map.of("count", String.valueOf(count)));
            else sender.sendMessage(plugin.prefix() + ColorUtil.color("&cNo se pudo regenerar ninguna rotacion."));
            return;
        }
        boolean success = plugin.forceRotate(target);
        if (success) plugin.message(sender, "reroll-success", Map.of("rotation", target));
        else sender.sendMessage(plugin.prefix() + ColorUtil.color("&cRotacion desconocida o deshabilitada."));
    }

    private void reportBridge(CommandSender sender, String[] args, ObjectiveType type) {
        if (args.length < 3) {
            sender.sendMessage(type == ObjectiveType.COMPLETE_EVENT
                    ? "/mdvquest event <jugador> <evento> [cantidad]"
                    : "/mdvquest profexp <jugador> <profesion> <cantidad>");
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage(plugin.prefix() + ColorUtil.color("&cJugador desconectado o inexistente."));
            return;
        }
        long amount = parseLong(args.length >= 4 ? args[3] : "1", 1L);
        int changed = plugin.getProgressService().report(player, type, args[2], amount);
        sender.sendMessage(plugin.prefix() + ColorUtil.color("&aReporte aplicado a &f" + changed + " &aobjetivos."));
    }

    private void reportGeneric(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("/mdvquest report <jugador> <tipo> <objetivo> <cantidad>");
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage(plugin.prefix() + ColorUtil.color("&cJugador desconectado o inexistente."));
            return;
        }
        ObjectiveType type;
        try { type = ObjectiveType.parse(args[2]); }
        catch (Exception ex) {
            sender.sendMessage(plugin.prefix() + ColorUtil.color("&cTipo de objetivo invalido."));
            return;
        }
        long amount = parseLong(args[4], 1L);
        Map<String, String> data = Collections.emptyMap();
        if ((type == ObjectiveType.OBTAIN_MMOITEM || type == ObjectiveType.USE_CONSUMABLE) && args[3].contains(":")) {
            String[] split = args[3].split(":", 2);
            data = Map.of("mmo-type", MMOItemsHook.normalize(split[0]), "mmo-id", MMOItemsHook.normalize(split[1]), "source", "ADMIN");
        }
        int changed = plugin.getProgressService().report(player, type, args[3], amount, data);
        sender.sendMessage(plugin.prefix() + ColorUtil.color("&aReporte aplicado a &f" + changed + " &aobjetivos."));
    }

    private void showStatus(CommandSender sender) {
        List<MissionInstance> active = plugin.getRotationService().activeInstances();
        sender.sendMessage(ColorUtil.color("&6--- MDVQuest " + plugin.getDescription().getVersion() + " ---"));
        sender.sendMessage(ColorUtil.color("&7Misiones cargadas: &f" + plugin.getRegistry().missions().size()));
        sender.sendMessage(ColorUtil.color("&7Instancias activas: &f" + active.size()));
        long now = System.currentTimeMillis();
        for (MissionInstance instance : active) {
            sender.sendMessage(ColorUtil.color("&8- &f" + instance.definition().id()
                    + " &7[" + instance.rotationId() + "/" + instance.accessTier().key() + "] expira en "
                    + TimeUtil.remaining(instance.expiresAt(), now)));
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&e/mdvquest &7- abre el menu"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest admin &7- catálogo y editor visual"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest crear &7- crea una misión"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest reload"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest status"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest force <id-de-mision>"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest reroll <rotacion|all> confirmar"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest event <jugador> <evento> [cantidad]"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest profexp <jugador> <profesion> <cantidad>"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest report <jugador> <tipo> <objetivo> <cantidad>"));
    }

    private long parseLong(String raw, long fallback) {
        try { return Math.max(1L, Long.parseLong(raw)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean editor = sender.hasPermission("mdvquest.editor") || sender.hasPermission("mdvquest.admin");
        boolean admin = sender.hasPermission("mdvquest.admin");
        if (!editor && !admin) return Collections.emptyList();
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (editor) options.addAll(List.of("admin", "crear"));
            if (admin) options.addAll(List.of("reload", "status", "force", "reroll", "rotate", "event", "profexp", "report"));
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && editor) return filter(List.of("crear"), args[1]);
        if (!admin) return Collections.emptyList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("force") || args[0].equalsIgnoreCase("forzar"))) {
            return filter(plugin.getRegistry().missions().stream().map(mission -> mission.id()).toList(), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("event") || args[0].equalsIgnoreCase("profexp") || args[0].equalsIgnoreCase("report"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("rotate") || args[0].equalsIgnoreCase("reroll"))) {
            List<String> rotations = new ArrayList<>(plugin.getRegistry().rotations().stream().map(r -> r.id()).toList());
            rotations.add("all");
            return filter(rotations, args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("rotate") || args[0].equalsIgnoreCase("reroll"))) {
            return filter(List.of("confirmar"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("report")) {
            return filter(Arrays.stream(ObjectiveType.values()).map(Enum::name).toList(), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
