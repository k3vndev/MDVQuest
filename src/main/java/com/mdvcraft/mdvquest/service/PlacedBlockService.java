package com.mdvcraft.mdvquest.service;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.storage.QuestDatabase;
import com.mdvcraft.mdvquest.util.BlockPosition;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public final class PlacedBlockService {
    private final MDVQuestPlugin plugin;
    private final QuestRegistry registry;
    private final QuestDatabase database;
    private final Set<BlockPosition> placed = new HashSet<>();

    public PlacedBlockService(MDVQuestPlugin plugin, QuestRegistry registry, QuestDatabase database) {
        this.plugin = plugin;
        this.registry = registry;
        this.database = database;
    }

    public void initialize() {
        if (!enabled() || !plugin.getConfig().getBoolean("anti-exploit.placed-blocks.persist", true)) return;
        try {
            placed.addAll(database.loadPlacedBlocks());
            plugin.getLogger().info("Bloques colocados protegidos cargados: " + placed.size());
        } catch (SQLException ex) {
            plugin.getLogger().warning("No se pudieron cargar bloques colocados: " + ex.getMessage());
        }
    }

    public boolean shouldTrack(Material material) {
        return enabled() && material != null && registry.naturalTrackedMaterials().contains(material);
    }

    public void placed(Block block) {
        if (block == null || !shouldTrack(block.getType())) return;
        BlockPosition position = BlockPosition.of(block);
        placed.add(position);
        if (plugin.getConfig().getBoolean("anti-exploit.placed-blocks.persist", true)) {
            plugin.runDatabaseAsync(() -> {
                try { database.addPlacedBlock(position, block.getType().name(), System.currentTimeMillis()); }
                catch (SQLException ex) { plugin.getLogger().warning("No se pudo registrar bloque colocado: " + ex.getMessage()); }
            });
        }
    }

    public boolean consumeIfPlaced(Block block) {
        if (block == null || !enabled()) return false;
        BlockPosition position = BlockPosition.of(block);
        boolean wasPlaced = placed.remove(position);
        if (wasPlaced && plugin.getConfig().getBoolean("anti-exploit.placed-blocks.persist", true)) {
            plugin.runDatabaseAsync(() -> {
                try { database.removePlacedBlock(position); }
                catch (SQLException ex) { plugin.getLogger().warning("No se pudo limpiar bloque colocado: " + ex.getMessage()); }
            });
        }
        return wasPlaced;
    }

    public void remove(Block block) {
        if (block == null || !enabled()) return;
        BlockPosition position = BlockPosition.of(block);
        if (!placed.remove(position)) return;
        if (plugin.getConfig().getBoolean("anti-exploit.placed-blocks.persist", true)) {
            plugin.runDatabaseAsync(() -> {
                try { database.removePlacedBlock(position); }
                catch (SQLException ex) { plugin.getLogger().warning("No se pudo limpiar bloque colocado: " + ex.getMessage()); }
            });
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("anti-exploit.placed-blocks.enabled", true);
    }
}
