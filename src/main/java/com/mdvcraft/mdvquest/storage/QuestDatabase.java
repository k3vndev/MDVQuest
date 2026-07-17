package com.mdvcraft.mdvquest.storage;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.AccessTier;
import com.mdvcraft.mdvquest.model.MissionInstance;
import com.mdvcraft.mdvquest.model.ObjectiveKey;
import com.mdvcraft.mdvquest.model.PlayerQuestState;
import com.mdvcraft.mdvquest.service.QuestRegistry;
import com.mdvcraft.mdvquest.util.BlockPosition;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class QuestDatabase implements AutoCloseable {
    private final MDVQuestPlugin plugin;
    private Connection connection;

    public QuestDatabase(MDVQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void open() throws SQLException {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        File file = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file", "mdvquest.db"));
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("No se encontro sqlite-jdbc dentro del jar", exception);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA temp_store=MEMORY");
            statement.execute("PRAGMA busy_timeout=" + Math.max(1000, plugin.getConfig().getInt("database.busy-timeout-ms", 5000)));
            statement.execute("PRAGMA auto_vacuum=INCREMENTAL");
        }
        createSchema();
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mission_instances (
                      id TEXT PRIMARY KEY,
                      cycle_key TEXT NOT NULL,
                      rotation_id TEXT NOT NULL,
                      access_pool TEXT NOT NULL DEFAULT 'normal',
                      definition_id TEXT NOT NULL,
                      starts_at INTEGER NOT NULL,
                      expires_at INTEGER NOT NULL,
                      created_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mission_instances_expiry ON mission_instances(expires_at)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mission_instances_cycle ON mission_instances(cycle_key)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_progress (
                      player_uuid TEXT NOT NULL,
                      instance_id TEXT NOT NULL,
                      objective_id TEXT NOT NULL,
                      progress INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL,
                      PRIMARY KEY(player_uuid, instance_id, objective_id)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_progress_instance ON player_progress(instance_id)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_claims (
                      player_uuid TEXT NOT NULL,
                      instance_id TEXT NOT NULL,
                      claimed_at INTEGER NOT NULL,
                      PRIMARY KEY(player_uuid, instance_id)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_claims_instance ON player_claims(instance_id)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pvp_unique_victims (
                      player_uuid TEXT NOT NULL,
                      instance_id TEXT NOT NULL,
                      objective_id TEXT NOT NULL,
                      victim_uuid TEXT NOT NULL,
                      counted_at INTEGER NOT NULL,
                      PRIMARY KEY(player_uuid, instance_id, objective_id, victim_uuid)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pvp_instance ON pvp_unique_victims(instance_id)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pvp_kill_history (
                      player_uuid TEXT NOT NULL,
                      victim_uuid TEXT NOT NULL,
                      counted_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pvp_history_pair ON pvp_kill_history(player_uuid, victim_uuid, counted_at)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pvp_history_time ON pvp_kill_history(counted_at)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS placed_blocks (
                      world_uuid TEXT NOT NULL,
                      x INTEGER NOT NULL,
                      y INTEGER NOT NULL,
                      z INTEGER NOT NULL,
                      material TEXT NOT NULL,
                      placed_at INTEGER NOT NULL,
                      PRIMARY KEY(world_uuid, x, y, z)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS metadata (
                      meta_key TEXT PRIMARY KEY,
                      meta_value TEXT NOT NULL
                    )
                    """);
        }
        ensureColumn("mission_instances", "access_pool", "TEXT NOT NULL DEFAULT 'normal'");
    }

    private void ensureColumn(String table, String column, String definition) throws SQLException {
        boolean exists = false;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }

    public synchronized List<StoredInstance> loadUnexpiredInstances(long now) throws SQLException {
        List<StoredInstance> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, cycle_key, rotation_id, access_pool, definition_id, starts_at, expires_at FROM mission_instances WHERE expires_at > ? ORDER BY expires_at, id")) {
            statement.setLong(1, now);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new StoredInstance(
                            rs.getString("id"), rs.getString("cycle_key"), rs.getString("rotation_id"),
                            AccessTier.parse(rs.getString("access_pool")), rs.getString("definition_id"),
                            rs.getLong("starts_at"), rs.getLong("expires_at")
                    ));
                }
            }
        }
        return result;
    }

    public synchronized boolean cycleExists(String cycleKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM mission_instances WHERE cycle_key = ? LIMIT 1")) {
            statement.setString(1, cycleKey);
            try (ResultSet rs = statement.executeQuery()) { return rs.next(); }
        }
    }

    public synchronized void insertInstances(Collection<MissionInstance> instances) throws SQLException {
        if (instances.isEmpty()) return;
        boolean previous = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO mission_instances(id, cycle_key, rotation_id, access_pool, definition_id, starts_at, expires_at, created_at) VALUES(?,?,?,?,?,?,?,?)")) {
            long now = System.currentTimeMillis();
            for (MissionInstance instance : instances) {
                statement.setString(1, instance.id());
                statement.setString(2, instance.cycleKey());
                statement.setString(3, instance.rotationId());
                statement.setString(4, instance.accessTier().key());
                statement.setString(5, instance.definition().id());
                statement.setLong(6, instance.startsAt());
                statement.setLong(7, instance.expiresAt());
                statement.setLong(8, now);
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previous);
        }
    }

    public synchronized PlayerQuestState loadPlayer(UUID playerId, Set<String> activeInstanceIds) throws SQLException {
        PlayerQuestState state = new PlayerQuestState(playerId);
        if (activeInstanceIds.isEmpty()) return state;
        String placeholders = String.join(",", java.util.Collections.nCopies(activeInstanceIds.size(), "?"));
        String progressSql = "SELECT instance_id, objective_id, progress FROM player_progress WHERE player_uuid = ? AND instance_id IN (" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(progressSql)) {
            int index = 1;
            statement.setString(index++, playerId.toString());
            for (String id : activeInstanceIds) statement.setString(index++, id);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) state.putLoadedProgress(new ObjectiveKey(rs.getString(1), rs.getString(2)), rs.getLong(3));
            }
        }
        String claimsSql = "SELECT instance_id FROM player_claims WHERE player_uuid = ? AND instance_id IN (" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(claimsSql)) {
            int index = 1;
            statement.setString(index++, playerId.toString());
            for (String id : activeInstanceIds) statement.setString(index++, id);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) state.claimedInstances().add(rs.getString(1));
            }
        }
        return state;
    }

    public synchronized void flushPlayer(PlayerQuestState state) throws SQLException {
        if (state == null) return;
        flushPlayers(List.of(state));
    }

    /**
     * Guarda todos los jugadores sucios dentro de una unica transaccion.
     * Esto evita abrir una transaccion SQLite por jugador durante el guardado periodico.
     */
    public synchronized void flushPlayers(Collection<PlayerQuestState> states) throws SQLException {
        if (states == null || states.isEmpty()) return;
        Map<PlayerQuestState, Set<ObjectiveKey>> snapshots = new LinkedHashMap<>();
        for (PlayerQuestState state : states) {
            if (state == null || state.dirty().isEmpty()) continue;
            snapshots.put(state, new HashSet<>(state.dirty()));
        }
        if (snapshots.isEmpty()) return;

        boolean previous = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_progress(player_uuid, instance_id, objective_id, progress, updated_at)
                VALUES(?,?,?,?,?)
                ON CONFLICT(player_uuid, instance_id, objective_id)
                DO UPDATE SET progress=excluded.progress, updated_at=excluded.updated_at
                """)) {
            long now = System.currentTimeMillis();
            for (Map.Entry<PlayerQuestState, Set<ObjectiveKey>> entry : snapshots.entrySet()) {
                PlayerQuestState state = entry.getKey();
                for (ObjectiveKey key : entry.getValue()) {
                    statement.setString(1, state.playerId().toString());
                    statement.setString(2, key.instanceId());
                    statement.setString(3, key.objectiveId());
                    statement.setLong(4, state.progress(key));
                    statement.setLong(5, now);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
            connection.commit();
            snapshots.forEach((state, dirty) -> state.dirty().removeAll(dirty));
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previous);
        }
    }

    public synchronized boolean claim(UUID playerId, String instanceId, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO player_claims(player_uuid, instance_id, claimed_at) VALUES(?,?,?)")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, instanceId);
            statement.setLong(3, now);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized boolean registerUniqueVictim(UUID playerId, String instanceId, String objectiveId, UUID victimId, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO pvp_unique_victims(player_uuid, instance_id, objective_id, victim_uuid, counted_at) VALUES(?,?,?,?,?)")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, instanceId);
            statement.setString(3, objectiveId);
            statement.setString(4, victimId.toString());
            statement.setLong(5, now);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized long lastVictimCount(UUID playerId, UUID victimId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT MAX(counted_at) FROM pvp_kill_history WHERE player_uuid = ? AND victim_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, victimId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }


    public synchronized void recordVictimKill(UUID playerId, UUID victimId, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pvp_kill_history(player_uuid, victim_uuid, counted_at) VALUES(?,?,?)")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, victimId.toString());
            statement.setLong(3, now);
            statement.executeUpdate();
        }
    }

    public synchronized Set<BlockPosition> loadPlacedBlocks() throws SQLException {
        Set<BlockPosition> positions = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT world_uuid, x, y, z FROM placed_blocks");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                try {
                    positions.add(new BlockPosition(UUID.fromString(rs.getString(1)), rs.getInt(2), rs.getInt(3), rs.getInt(4)));
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return positions;
    }

    public synchronized void addPlacedBlock(BlockPosition position, String material, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO placed_blocks(world_uuid,x,y,z,material,placed_at) VALUES(?,?,?,?,?,?)")) {
            statement.setString(1, position.world().toString());
            statement.setInt(2, position.x());
            statement.setInt(3, position.y());
            statement.setInt(4, position.z());
            statement.setString(5, material);
            statement.setLong(6, now);
            statement.executeUpdate();
        }
    }

    public synchronized void removePlacedBlock(BlockPosition position) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM placed_blocks WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
            statement.setString(1, position.world().toString());
            statement.setInt(2, position.x());
            statement.setInt(3, position.y());
            statement.setInt(4, position.z());
            statement.executeUpdate();
        }
    }

    public synchronized int cleanupExpired(long now) throws SQLException {
        long cooldownHours = Math.max(1L, plugin.getConfig().getLong("anti-exploit.pvp.victim-repeat-cooldown-hours", 24L));
        long cutoff = now - Math.max(24L, cooldownHours) * 3_600_000L;
        try (PreparedStatement history = connection.prepareStatement("DELETE FROM pvp_kill_history WHERE counted_at < ?")) {
            history.setLong(1, cutoff);
            history.executeUpdate();
        }
        List<String> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM mission_instances WHERE expires_at <= ?")) {
            statement.setLong(1, now);
            try (ResultSet rs = statement.executeQuery()) { while (rs.next()) ids.add(rs.getString(1)); }
        }
        if (ids.isEmpty()) return 0;
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        boolean previous = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (String table : List.of("player_progress", "player_claims", "pvp_unique_victims")) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE instance_id IN (" + placeholders + ")")) {
                    int i = 1; for (String id : ids) statement.setString(i++, id); statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM mission_instances WHERE id IN (" + placeholders + ")")) {
                int i = 1; for (String id : ids) statement.setString(i++, id); statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previous);
        }
        return ids.size();
    }

    public synchronized void deleteCycle(String cycleKey) throws SQLException {
        List<String> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM mission_instances WHERE cycle_key=?")) {
            statement.setString(1, cycleKey);
            try (ResultSet rs = statement.executeQuery()) { while (rs.next()) ids.add(rs.getString(1)); }
        }
        if (ids.isEmpty()) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        boolean previous = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (String table : List.of("player_progress", "player_claims", "pvp_unique_victims")) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE instance_id IN (" + placeholders + ")")) {
                    int i = 1; for (String id : ids) statement.setString(i++, id); statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM mission_instances WHERE cycle_key=?")) {
                statement.setString(1, cycleKey); statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previous);
        }
    }

    public synchronized void incrementalVacuum() {
        int pages = Math.max(0, plugin.getConfig().getInt("database.incremental-vacuum-pages", 64));
        if (pages <= 0 || connection == null) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA incremental_vacuum(" + pages + ")");
        } catch (SQLException ex) {
            plugin.getLogger().warning("No se pudo ejecutar incremental_vacuum: " + ex.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        if (connection == null) return;
        try { connection.close(); }
        catch (SQLException ex) { plugin.getLogger().warning("Error cerrando SQLite: " + ex.getMessage()); }
        connection = null;
    }

    public record StoredInstance(String id, String cycleKey, String rotationId, AccessTier accessTier, String definitionId, long startsAt, long expiresAt) { }
}
