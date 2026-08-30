package com.mdvcraft.mdvquest.util;

import java.time.Duration;

public final class TimeUtil {
    private TimeUtil() { }

    public static String remaining(long expiresAt, long now) {
        long millis = Math.max(0L, expiresAt - now);
        Duration duration = Duration.ofMillis(millis);
        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        long minutes = duration.minusDays(days).minusHours(hours).toMinutes();
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1, minutes) + "m";
    }
}
