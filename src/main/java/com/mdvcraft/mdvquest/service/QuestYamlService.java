package com.mdvcraft.mdvquest.service;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.MissionDefinition;
import com.mdvcraft.mdvquest.model.ObjectiveDefinition;
import com.mdvcraft.mdvquest.model.QuestDraft;
import com.mdvcraft.mdvquest.model.RewardDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Escritura controlada y segura de misiones creadas desde el editor visual. */
public final class QuestYamlService {
    private final MDVQuestPlugin plugin;

    public QuestYamlService(MDVQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized SaveResult save(QuestDraft draft) {
        String validation = validate(draft);
        if (validation != null) return new SaveResult(false, validation, null);

        File folder = new File(plugin.getDataFolder(), "missions");
        if (!folder.exists() && !folder.mkdirs()) {
            return new SaveResult(false, "No se pudo crear la carpeta missions.", null);
        }
        File target = new File(folder, sanitizeFile(draft.targetFile()));

        try {
            if (draft.editingExisting()) {
                saveExistingMissionTransaction(folder, target, draft);
            } else {
                YamlConfiguration targetYaml = YamlConfiguration.loadConfiguration(target);
                removeMissionByNormalizedId(targetYaml, draft.id());
                writeMission(targetYaml, draft);
                saveAtomic(targetYaml, target);
            }

            return new SaveResult(true, "Misión guardada en " + target.getName(), target.getName());
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar la misión " + draft.id() + ": " + ex.getMessage());
            return new SaveResult(false, "No se pudo escribir el YAML: " + ex.getMessage(), null);
        }
    }

    private void writeMission(YamlConfiguration yaml, QuestDraft draft) {
        String base = "missions." + draft.id();
        yaml.set(base, null);
        yaml.set(base + ".enabled", draft.enabled());
        yaml.set(base + ".rotation", draft.rotation());
        yaml.set(base + ".access-pool", draft.accessTier().key());
        yaml.set(base + ".weight", draft.weight());
        yaml.set(base + ".name", draft.name());
        yaml.set(base + ".icon", draft.icon().getType().name());
        yaml.set(base + ".icon-item", draft.icon());
        yaml.set(base + ".lore", new ArrayList<>(draft.lore()));

        for (ObjectiveDefinition objective : draft.objectives()) {
            String objectiveBase = base + ".objectives." + objective.id();
            yaml.set(objectiveBase + ".type", objective.type().name());
            yaml.set(objectiveBase + ".amount", objective.amount());
            yaml.set(objectiveBase + ".name", objective.displayName());
            for (Map.Entry<String, Object> option : objective.options().entrySet()) {
                if (option.getKey().equalsIgnoreCase("type")
                        || option.getKey().equalsIgnoreCase("amount")
                        || option.getKey().equalsIgnoreCase("name")) continue;
                yaml.set(objectiveBase + "." + option.getKey(), option.getValue());
            }
        }

        writeRewards(yaml, base + ".rewards", draft.rewards());
    }

    private void writeRewards(YamlConfiguration yaml, String base, RewardDefinition rewards) {
        yaml.set(base, null);
        if (!rewards.displayLore().isEmpty()) yaml.set(base + ".lore", rewards.displayLore());
        if (!rewards.commands().isEmpty()) yaml.set(base + ".commands", rewards.commands());

        List<Map<String, Object>> vanilla = new ArrayList<>();
        for (RewardDefinition.VanillaItemReward reward : rewards.vanillaItems()) {
            vanilla.add(Map.of("material", reward.material(), "amount", reward.amount()));
        }
        if (!vanilla.isEmpty()) yaml.set(base + ".vanilla-items", vanilla);

        List<Map<String, Object>> mmo = new ArrayList<>();
        for (RewardDefinition.MmoItemReward reward : rewards.mmoItems()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", reward.type());
            map.put("id", reward.id());
            map.put("amount", reward.amount());
            mmo.add(map);
        }
        if (!mmo.isEmpty()) yaml.set(base + ".mmoitems", mmo);

        List<Map<String, Object>> mythic = new ArrayList<>();
        for (RewardDefinition.MythicItemReward reward : rewards.mythicItems()) {
            mythic.add(Map.of("id", reward.id(), "amount", reward.amount()));
        }
        if (!mythic.isEmpty()) yaml.set(base + ".mythic-items", mythic);

        List<ItemStack> exact = new ArrayList<>();
        for (RewardDefinition.ExactItemReward reward : rewards.exactItems()) {
            ItemStack item = reward.item().clone();
            item.setAmount(Math.max(1, reward.amount()));
            exact.add(item);
        }
        if (!exact.isEmpty()) yaml.set(base + ".exact-items", exact);

        List<Map<String, Object>> experience = new ArrayList<>();
        for (RewardDefinition.ExperienceReward reward : rewards.experience()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("profession", reward.profession());
            map.put("amount", reward.amount());
            experience.add(map);
        }
        if (!experience.isEmpty()) yaml.set(base + ".experience", experience);
    }

    private void saveExistingMissionTransaction(File folder, File target, QuestDraft draft) throws IOException {
        Map<File, YamlConfiguration> changed = new LinkedHashMap<>();
        File[] missionFiles = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (missionFiles != null) {
            List<File> ordered = new ArrayList<>(List.of(missionFiles));
            ordered.sort(Comparator.comparing(File::getName));
            for (File file : ordered) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                boolean removed = removeMissionByNormalizedId(yaml, draft.originalId());
                if (!QuestRegistry.normalizeMission(draft.originalId()).equals(QuestRegistry.normalizeMission(draft.id()))) {
                    removed |= removeMissionByNormalizedId(yaml, draft.id());
                }
                if (removed) changed.put(file.getCanonicalFile(), yaml);
            }
        }

        File canonicalTarget = target.getCanonicalFile();
        YamlConfiguration targetYaml = changed.get(canonicalTarget);
        if (targetYaml == null) targetYaml = YamlConfiguration.loadConfiguration(canonicalTarget);
        removeMissionByNormalizedId(targetYaml, draft.originalId());
        removeMissionByNormalizedId(targetYaml, draft.id());
        writeMission(targetYaml, draft);
        changed.put(canonicalTarget, targetYaml);

        // Guarda primero todos los orígenes y al final el destino. Si cualquier escritura falla,
        // restaura todos los archivos. Esto también limpia duplicados históricos del mismo ID.
        List<File> order = new ArrayList<>(changed.keySet());
        order.sort((left, right) -> {
            boolean leftTarget = left.equals(canonicalTarget);
            boolean rightTarget = right.equals(canonicalTarget);
            if (leftTarget == rightTarget) return left.getName().compareTo(right.getName());
            return leftTarget ? 1 : -1;
        });

        Map<File, byte[]> backups = new LinkedHashMap<>();
        for (File file : order) {
            backups.put(file, Files.exists(file.toPath()) ? Files.readAllBytes(file.toPath()) : null);
        }

        try {
            for (File file : order) saveAtomic(changed.get(file), file);
        } catch (IOException failure) {
            IOException rollbackFailure = null;
            for (Map.Entry<File, byte[]> backup : backups.entrySet()) {
                try {
                    restoreFile(backup.getKey(), backup.getValue());
                } catch (IOException ex) {
                    if (rollbackFailure == null) rollbackFailure = ex;
                    else rollbackFailure.addSuppressed(ex);
                }
            }
            if (rollbackFailure != null) {
                failure.addSuppressed(rollbackFailure);
                plugin.getLogger().severe("También falló la restauración de YAML tras guardar "
                        + draft.id() + ": " + rollbackFailure.getMessage());
            }
            throw failure;
        }
    }

    private boolean removeMissionByNormalizedId(YamlConfiguration yaml, String missionId) {
        if (yaml == null || missionId == null || missionId.isBlank()) return false;
        var missions = yaml.getConfigurationSection("missions");
        if (missions == null) return false;
        String normalized = QuestRegistry.normalizeMission(missionId);
        List<String> matches = missions.getKeys(false).stream()
                .filter(raw -> QuestRegistry.normalizeMission(raw).equals(normalized))
                .toList();
        for (String raw : matches) yaml.set("missions." + raw, null);
        return !matches.isEmpty();
    }

    private void restoreFile(File file, byte[] backup) throws IOException {
        if (backup == null) {
            Files.deleteIfExists(file.toPath());
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("No se pudo restaurar la carpeta " + parent.getAbsolutePath());
        }
        File temp = new File(parent, file.getName() + ".rollback.tmp");
        Files.write(temp.toPath(), backup);
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    private void saveAtomic(YamlConfiguration yaml, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("No se pudo crear " + parent.getAbsolutePath());
        }
        File temp = new File(parent, target.getName() + ".tmp");
        yaml.save(temp);
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (temp.exists()) Files.deleteIfExists(temp.toPath());
        }
    }

    public List<String> files() {
        File folder = new File(plugin.getDataFolder(), "missions");
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        List<String> result = new ArrayList<>();
        if (files != null) {
            for (File file : files) result.add(file.getName());
        }
        result.sort(Comparator.naturalOrder());
        if (result.isEmpty()) result.add("missions.yml");
        return result;
    }

    private String validate(QuestDraft draft) {
        if (draft == null) return "No existe un borrador activo.";
        if (draft.id() == null || draft.id().isBlank()) return "La misión necesita un ID interno.";
        if (draft.name() == null || draft.name().isBlank()) return "La misión necesita nombre.";
        if (draft.objectives().isEmpty()) return "Agrega al menos un objetivo.";
        if (plugin.getRegistry().rotation(draft.rotation()) == null) return "No existe la rotación " + draft.rotation() + ".";

        MissionDefinition collision = plugin.getRegistry().mission(draft.id());
        if (collision != null) {
            if (!draft.editingExisting()) return "Ya existe una misión con el ID " + draft.id() + ".";
            if (!collision.id().equalsIgnoreCase(draft.originalId())) {
                return "El nuevo ID ya pertenece a otra misión: " + draft.id() + ".";
            }
        }
        return null;
    }

    private String sanitizeFile(String value) {
        String file = value == null ? "missions.yml" : value.trim().replace('\\', '/');
        file = file.substring(file.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        if (!file.toLowerCase(Locale.ROOT).endsWith(".yml")) file += ".yml";
        return file.isBlank() ? "missions.yml" : file;
    }

    public record SaveResult(boolean success, String message, String fileName) { }
}
