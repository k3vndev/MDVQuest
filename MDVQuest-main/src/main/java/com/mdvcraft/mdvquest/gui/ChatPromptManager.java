package com.mdvcraft.mdvquest.gui;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Captura una única respuesta de chat para los asistentes del editor. */
@SuppressWarnings("deprecation")
public final class ChatPromptManager implements Listener {
    private final MDVQuestPlugin plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public ChatPromptManager(MDVQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public void ask(Player player, String message, Consumer<String> answer, Runnable cancel) {
        prompts.put(player.getUniqueId(), new Prompt(answer, cancel));
        player.closeInventory();
        player.sendMessage(ColorUtil.color("&6&l[MDVQuest] &e" + message));
        player.sendMessage(ColorUtil.color("&7Escribe &c cancelar &7para volver sin guardar este paso."));
    }

    public boolean waiting(Player player) {
        return prompts.containsKey(player.getUniqueId());
    }

    public void cancel(Player player) {
        Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt != null && prompt.cancel() != null) prompt.cancel().run();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Prompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) return;
        event.setCancelled(true);
        String message = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            if (message.equalsIgnoreCase("cancelar")) {
                if (prompt.cancel() != null) prompt.cancel().run();
            } else {
                prompt.answer().accept(message);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    private record Prompt(Consumer<String> answer, Runnable cancel) { }
}
