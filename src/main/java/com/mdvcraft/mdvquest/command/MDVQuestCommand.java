package com.mdvcraft.mdvquest.command;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.hook.MMOItemsHook;
import com.mdvcraft.mdvquest.model.MissionInstance;
import com.mdvcraft.mdvquest.model.ObjectiveType;
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
        if (!sender.hasPermission("mdvquest.admin")) {
            sender.sendMessage(plugin.prefix() + ColorUtil.color("&cNo tienes permiso."));
            return true;
        }

        switch (sub) {
            case "admin", "gestionar" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("El editor visual solo puede abrirse dentro del juego.");
                    return true;
                }
                if (args.length >= 2 && (args[1].equalsIgnoreCase("crear") || args[1].equalsIgnoreCase("new"))) {
                    plugin.getEditorManager().openDurationPicker(player);
                } else {
                    plugin.getEditorManager().openAdminCatalog(player);
                }
            }
            case "crear", "editor", "new" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("El editor visual solo puede abrirse dentro del juego.");
                    return true;
                }
                plugin.getEditorManager().openDurationPicker(player);
            }
            case "reload" -> {
                plugin.reloadPlugin();
                plugin.message(sender, "reload", Map.of());
            }
            case "status" -> showStatus(sender);
            case "rotate", "reroll" -> {
                if (args.length < 2) {
                    sender.sendMessage("/mdvquest rotate <rotacion>");
                    return true;
                }
                boolean success = plugin.forceRotate(args[1]);
                if (success) plugin.message(sender, "forced-rotation", Map.of("rotation", args[1]));
                else sender.sendMessage(plugin.prefix() + ColorUtil.color("&cRotacion desconocida o deshabilitada."));
            }
            case "event" -> reportBridge(sender, args, ObjectiveType.COMPLETE_EVENT);
            case "profexp" -> reportBridge(sender, args, ObjectiveType.EARN_PROFESSION_EXP);
            case "report" -> reportGeneric(sender, args);
            default -> help(sender);
        }
        return true;
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
            sender.sendMessage(ColorUtil.color("&8- &f" + instance.definition().id() + " &7[" + instance.rotationId() + "] expira en " + TimeUtil.remaining(instance.expiresAt(), now)));
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&e/mdvquest &7- abre el menu"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest admin &7- catálogo y editor visual"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest crear &7- crea una misión"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest reload"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest status"));
        sender.sendMessage(ColorUtil.color("&e/mdvquest rotate <rotacion>"));
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
        if (!sender.hasPermission("mdvquest.admin")) return Collections.emptyList();
        if (args.length == 1) return filter(List.of("admin", "crear", "reload", "status", "rotate", "event", "profexp", "report"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return filter(List.of("crear"), args[1]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("event") || args[0].equalsIgnoreCase("profexp") || args[0].equalsIgnoreCase("report"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rotate")) {
            return filter(plugin.getRegistry().rotations().stream().map(r -> r.id()).toList(), args[1]);
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
