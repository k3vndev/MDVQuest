package com.mdvcraft.mdvquest.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record RotationDefinition(
        String id,
        boolean enabled,
        int durationDays,
        int missionCount,
        LocalDate anchorDate,
        LocalTime resetTime,
        String seed
) {
    public RotationDefinition {
        durationDays = Math.max(1, Math.min(7, durationDays));
        missionCount = Math.max(0, missionCount);
    }
}
