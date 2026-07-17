package com.mdvcraft.mdvquest.model;

/** Cantidad mínima y máxima de misiones que se genera para un pool. */
public record MissionCountRange(int min, int max) {
    public MissionCountRange {
        min = Math.max(0, min);
        max = Math.max(min, max);
    }

    public boolean enabled() {
        return max > 0;
    }
}
