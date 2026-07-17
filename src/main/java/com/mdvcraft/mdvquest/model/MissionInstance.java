package com.mdvcraft.mdvquest.model;

public record MissionInstance(
        String id,
        String cycleKey,
        String rotationId,
        MissionDefinition definition,
        long startsAt,
        long expiresAt
) {
    public boolean isActive(long now) {
        return startsAt <= now && expiresAt > now;
    }
}
