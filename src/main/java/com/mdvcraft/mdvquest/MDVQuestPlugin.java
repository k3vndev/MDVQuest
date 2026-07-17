package com.mdvcraft.mdvquest;

import com.mdvcraft.mdvquest.command.MDVQuestCommand;
import com.mdvcraft.mdvquest.gui.QuestMenuManager;
import com.mdvcraft.mdvquest.hook.MDVSocialHook;
import com.mdvcraft.mdvquest.hook.MMOItemsHook;
import com.mdvcraft.mdvquest.hook.MythicMobsHook;
import com.mdvcraft.mdvquest.hook.PlaceholderHook;
import com.mdvcraft.mdvquest.listener.GameplayListener;
import com.mdvcraft.mdvquest.service.DeliveryService;
import com.mdvcraft.mdvquest.service.IntegrationService;
import com.mdvcraft.mdvquest.service.MDVSocialMenuInstaller;
import com.mdvcraft.mdvquest.service.PlacedBlockService;
import com.mdvcraft.mdvquest.service.ProgressService;
import com.mdvcraft.mdvquest.service.QuestRegistry;
import com.mdvcraft.mdvquest.service.RewardService;
import com.mdvcraft.mdvquest.service.RotationService;
import com.mdvcraft.mdvquest.storage.QuestDatabase;
import com.mdvcraft.mdvquest.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class MDVQuestPlugin extends JavaPlugin {
    private QuestDatabase database;
    private QuestRegistry registry;
    private RotationService rotationService;
    private ProgressService progressService;
    private PlacedBlockService placedBlockService;
    private RewardService rewardService;
    private DeliveryService deliveryService;
    private QuestMenuManager menuManager;
    private IntegrationService integrationService;
    private final MDVSocialHook socialHook = new MDVSocialHook();
    private final MMOItemsHook mmoItemsHook = new MMOItemsHook();
    private final MythicMobsHook mythicMobsHook = new MythicMobsHook();
    private final PlaceholderHook placeholderHook = new PlaceholderHook();
    private ExecutorService databaseExecutor;
    private BukkitTask flushTask;
    private BukkitTask rotationTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("families.yml");
        saveResourceIfMissing("missions/examples.yml");
        databaseExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MDVQuest-Database");
            thread.setDaemon(true);
            return thread;
        });

        try {
            database = new QuestDatabase(this);
            database.open();
            registry = new QuestRegistry(this);
            int definitions = registry.reload();
            rotationService = new RotationService(this, registry, database);
            rotationService.initialize();
            progressService = new ProgressService(this, registry, rotationService, database);
            placedBlockService = new PlacedBlockService(this, registry, database);
            placedBlockService.initialize();
            rewardService = new RewardService(this, progressService, database, mmoItemsHook);
            deliveryService = new DeliveryService(this, progressService, mmoItemsHook);
            menuManager = new QuestMenuManager(this, rotationService, progressService, rewardService, deliveryService);
            integrationService = new IntegrationService(this, progressService, mmoItemsHook);

            Bukkit.getPluginManager().registerEvents(new GameplayListener(this, progressService, placedBlockService, mmoItemsHook), this);
            Bukkit.getPluginManager().registerEvents(menuManager, this);
            integrationService.register();

            MDVQuestCommand executor = new MDVQuestCommand(this);
            PluginCommand command = getCommand("mdvquest");
            if (command != null) {
                command.setExecutor(executor);
                command.setTabCompleter(executor);
            }

            for (Player player : Bukkit.getOnlinePlayers()) progressService.preload(player);
            scheduleTasks();
            new MDVSocialMenuInstaller(this).installIfNeeded();
            getLogger().info("MDVQuest 1.0.0 habilitado. Definiciones: " + definitions + ", activas: " + rotationService.activeInstances().size());
        } catch (Exception ex) {
            getLogger().severe("No se pudo iniciar MDVQuest: " + ex.getMessage());
            ex.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (flushTask != null) flushTask.cancel();
        if (rotationTask != null) rotationTask.cancel();
        if (progressService != null) progressService.flushAll();
        if (databaseExecutor != null) {
            databaseExecutor.shutdown();
            try { databaseExecutor.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        if (database != null) database.close();
    }

    private void scheduleTasks() {
        long flushTicks = Math.max(5L, getConfig().getLong("database.flush-interval-seconds", 30L)) * 20L;
        flushTask = Bukkit.getScheduler().runTaskTimer(this, progressService::flushOnline, flushTicks, flushTicks);

        long cleanupTicks = Math.max(10L, getConfig().getLong("database.cleanup-interval-seconds", 60L)) * 20L;
        rotationTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            try {
                boolean changed = rotationService.refresh(now);
                int cleaned = database.cleanupExpired(now);
                if (changed || cleaned > 0) progressService.rebuildIndex();
                if (cleaned > 0) database.incrementalVacuum();
            } catch (SQLException ex) {
                getLogger().severe("Error actualizando rotaciones: " + ex.getMessage());
            }
        }, 20L, cleanupTicks);
    }

    public void reloadPlugin() {
        progressService.flushAll();
        reloadConfig();
        registry.reload();
        try {
            rotationService.initialize();
            progressService.rebuildIndex();
            if (flushTask != null) flushTask.cancel();
            if (rotationTask != null) rotationTask.cancel();
            scheduleTasks();
            new MDVSocialMenuInstaller(this).installIfNeeded();
        } catch (SQLException ex) {
            getLogger().severe("No se pudieron recargar las rotaciones: " + ex.getMessage());
        }
    }

    public boolean forceRotate(String rotationId) {
        try {
            progressService.flushAll();
            boolean result = rotationService.forceRotate(rotationId, System.currentTimeMillis());
            if (result) {
                progressService.rebuildIndex();
                database.cleanupExpired(System.currentTimeMillis());
            }
            return result;
        } catch (SQLException ex) {
            getLogger().severe("No se pudo forzar la rotacion: " + ex.getMessage());
            return false;
        }
    }

    public void runDatabaseAsync(Runnable task) {
        if (databaseExecutor == null || databaseExecutor.isShutdown()) return;
        databaseExecutor.execute(task);
    }

    public String prefix() {
        return ColorUtil.color(getConfig().getString("messages.prefix", "&6[MDVQuest] &f"));
    }

    public void message(CommandSender sender, String key, Map<String, String> replacements) {
        String value = getConfig().getString("messages." + key, key);
        if (value == null || value.isBlank()) return;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            value = value.replace("%" + replacement.getKey() + "%", replacement.getValue());
        }
        sender.sendMessage(prefix() + ColorUtil.color(value));
    }

    private void saveResourceIfMissing(String path) {
        File file = new File(getDataFolder(), path);
        if (file.exists()) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        saveResource(path, false);
    }

    public QuestDatabase getDatabase() { return database; }
    public QuestRegistry getRegistry() { return registry; }
    public RotationService getRotationService() { return rotationService; }
    public ProgressService getProgressService() { return progressService; }
    public QuestMenuManager getMenuManager() { return menuManager; }
    public MDVSocialHook getSocialHook() { return socialHook; }
    public MMOItemsHook getMmoItemsHook() { return mmoItemsHook; }
    public MythicMobsHook getMythicMobsHook() { return mythicMobsHook; }
    public PlaceholderHook getPlaceholderHook() { return placeholderHook; }
}
