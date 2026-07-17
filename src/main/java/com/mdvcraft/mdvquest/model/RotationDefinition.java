package com.mdvcraft.mdvquest.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;

public record RotationDefinition(
        String id,
        boolean enabled,
        int durationDays,
        Map<AccessTier, MissionCountRange> missionCounts,
        LocalDate anchorDate,
        LocalTime resetTime,
        String seed
) {
    public RotationDefinition {
        durationDays = Math.max(1, Math.min(7, durationDays));
        EnumMap<AccessTier, MissionCountRange> normalized = new EnumMap<>(AccessTier.class);
        if (missionCounts != null) normalized.putAll(missionCounts);
        for (AccessTier tier : AccessTier.values()) {
            normalized.putIfAbsent(tier, new MissionCountRange(0, 0));
        }
        missionCounts = Map.copyOf(normalized);
    }

    public MissionCountRange countRange(AccessTier tier) {
        return missionCounts.getOrDefault(tier, new MissionCountRange(0, 0));
    }

    public boolean hasAnyPoolEnabled() {
        return missionCounts.values().stream().anyMatch(MissionCountRange::enabled);
    }
}
