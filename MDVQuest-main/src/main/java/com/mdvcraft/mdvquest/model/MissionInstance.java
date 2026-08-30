package com.mdvcraft.mdvquest.model;

public record MissionInstance(
        String id,
        String cycleKey,
        String rotationId,
        AccessTier accessTier,
        MissionDefinition definition,
        long startsAt,
        long expiresAt
) {
    public MissionInstance {
        accessTier = accessTier == null ? AccessTier.NORMAL : accessTier;
    }

    public boolean isActive(long now) {
        return startsAt <= now && expiresAt > now;
    }
}
