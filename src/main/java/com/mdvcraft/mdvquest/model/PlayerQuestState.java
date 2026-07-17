package com.mdvcraft.mdvquest.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerQuestState {
    private final UUID playerId;
    private final Map<ObjectiveKey, Long> progress = new HashMap<>();
    private final Set<String> claimedInstances = new HashSet<>();
    private final Set<ObjectiveKey> dirty = new HashSet<>();

    public PlayerQuestState(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() { return playerId; }
    public Map<ObjectiveKey, Long> progress() { return progress; }
    public Set<String> claimedInstances() { return claimedInstances; }
    public Set<ObjectiveKey> dirty() { return dirty; }

    public long progress(ObjectiveKey key) { return progress.getOrDefault(key, 0L); }
    public boolean claimed(String instanceId) { return claimedInstances.contains(instanceId); }

    public void putLoadedProgress(ObjectiveKey key, long value) {
        if (value > 0) progress.put(key, value);
    }

    public void setProgress(ObjectiveKey key, long value, boolean markDirty) {
        if (value <= 0) progress.remove(key); else progress.put(key, value);
        if (markDirty) dirty.add(key);
    }
}
