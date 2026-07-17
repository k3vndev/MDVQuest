package com.mdvcraft.mdvquest.service;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.hook.MMOItemsHook;
import com.mdvcraft.mdvquest.model.ObjectiveType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class IntegrationService {
    private final MDVQuestPlugin plugin;
    private final ProgressService progress;
    private final MMOItemsHook mmoItems;
    private final Listener dynamicListener = new Listener() { };

    public IntegrationService(MDVQuestPlugin plugin, ProgressService progress, MMOItemsHook mmoItems) {
        this.plugin = plugin;
        this.progress = progress;
        this.mmoItems = mmoItems;
    }

    public void register() {
        registerEvent("com.mdvcraft.mdvrecetas.api.event.MDVRecipeCraftEvent", this::handleRecipeCraft);
        registerEvent("com.mdvcraft.headores.api.event.MDVResourceBreakEvent", this::handleResourceBreak);
        registerEvent("io.lumine.mythic.bukkit.events.MythicMobDeathEvent", this::handleMythicMobDeath);
    }

    @SuppressWarnings("unchecked")
    private void registerEvent(String className, DynamicHandler handler) {
        try {
            Class<?> raw = Class.forName(className);
            if (!Event.class.isAssignableFrom(raw)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) raw;
            EventExecutor executor = (listener, event) -> {
                try { handler.handle(event); }
                catch (Throwable throwable) { throw new EventException(throwable); }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, dynamicListener, EventPriority.MONITOR, executor, plugin, true);
            plugin.getLogger().info("Integracion por evento registrada: " + className);
        } catch (ClassNotFoundException ignored) {
            plugin.getLogger().info("Integracion opcional no disponible: " + className);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("No se pudo registrar la integracion " + className + ": " + throwable.getMessage());
        }
    }

    private void handleRecipeCraft(Event event) throws Exception {
        Player player = (Player) invoke(event, "getPlayer");
        String recipeId = string(invoke(event, "getRecipeId"));
        String category = string(invoke(event, "getCategory"));
        long craftOperations = number(invoke(event, "getCraftOperations"), 1L);
        long producedAmount = number(invoke(event, "getProducedAmount"), craftOperations);
        Map<String, String> data = Map.of("craft-operations", String.valueOf(craftOperations), "source", "CRAFT");
        progress.report(player, ObjectiveType.CRAFT_RECIPE, recipeId, producedAmount, data);
        progress.report(player, ObjectiveType.CRAFT_CATEGORY, category, producedAmount, data);

        Object resultObject = invoke(event, "getResult");
        if (resultObject instanceof ItemStack result) {
            Optional<MMOItemsHook.Identity> identity = mmoItems.identify(result);
            identity.ifPresent(value -> progress.report(player, ObjectiveType.OBTAIN_MMOITEM, value.combined(), producedAmount,
                    Map.of("mmo-type", value.type(), "mmo-id", value.id(), "source", "CRAFT")));
        }
    }

    private void handleResourceBreak(Event event) throws Exception {
        Player player = (Player) invoke(event, "getPlayer");
        String resourceKey = string(invoke(event, "getResourceKey"));
        String resourceKind = string(invoke(event, "getResourceKind"));
        long dropAmount = number(invoke(event, "getDropAmount"), 1L);
        progress.report(player, ObjectiveType.BREAK_CUSTOM_ORE, resourceKey, 1L,
                Map.of("resource-kind", resourceKind));

        String dropType = string(invoke(event, "getDropType"));
        String dropId = string(invoke(event, "getDropId"));
        if (!dropType.isBlank() && !dropId.isBlank() && dropAmount > 0) {
            progress.report(player, ObjectiveType.OBTAIN_MMOITEM, dropType + ":" + dropId, dropAmount,
                    Map.of("mmo-type", dropType, "mmo-id", dropId, "source", "CUSTOM_ORE"));
        }

        String professionId = string(invoke(event, "getProfessionId"));
        long professionXp = number(invoke(event, "getProfessionXp"), 0L);
        if (!professionId.isBlank() && professionXp > 0) {
            progress.report(player, ObjectiveType.EARN_PROFESSION_EXP, professionId, professionXp);
        }
        long mainXp = number(invoke(event, "getMainXp"), 0L);
        if (mainXp > 0) progress.report(player, ObjectiveType.EARN_PROFESSION_EXP, "main", mainXp);
    }


    private void handleMythicMobDeath(Event event) throws Exception {
        Object killerObject = invoke(event, "getKiller");
        if (!(killerObject instanceof Player player)) return;
        Object entityObject = invoke(event, "getEntity");
        if (!(entityObject instanceof Entity entity)) return;
        Object mobType = invoke(event, "getMobType");
        String mythicId = extractId(mobType);
        if (!mythicId.isBlank()) progress.reportMythicKill(player, entity.getUniqueId(), mythicId);
    }

    private String extractId(Object value) {
        if (value == null) return "";
        if (value instanceof String string) return string;
        for (String methodName : new String[]{"getInternalName", "getInternalNameString", "getName", "getId"}) {
            try {
                Object result = value.getClass().getMethod(methodName).invoke(value);
                if (result != null) return String.valueOf(result);
            } catch (Throwable ignored) { }
        }
        return String.valueOf(value);
    }

    private Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long number(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(value)); }
        catch (Exception ignored) { return fallback; }
    }

    @FunctionalInterface
    private interface DynamicHandler {
        void handle(Event event) throws Exception;
    }
}
