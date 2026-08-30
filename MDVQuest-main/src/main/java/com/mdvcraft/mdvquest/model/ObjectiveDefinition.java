package com.mdvcraft.mdvquest.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ObjectiveDefinition {
    private final String id;
    private final ObjectiveType type;
    private final long amount;
    private final String displayName;
    private final Map<String, Object> options;

    public ObjectiveDefinition(String id, ObjectiveType type, long amount, String displayName, Map<String, Object> options) {
        this.id = normalize(id);
        this.type = Objects.requireNonNull(type, "type");
        this.amount = Math.max(1L, amount);
        this.displayName = displayName == null || displayName.isBlank() ? this.id : displayName;
        this.options = Collections.unmodifiableMap(new LinkedHashMap<>(options == null ? Collections.emptyMap() : options));
    }

    public String id() { return id; }
    public ObjectiveType type() { return type; }
    public long amount() { return amount; }
    public String displayName() { return displayName; }
    public Map<String, Object> options() { return options; }

    public String string(String key, String fallback) {
        Object value = options.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public boolean bool(String key, boolean fallback) {
        Object value = options.get(key);
        if (value instanceof Boolean b) return b;
        if (value == null) return fallback;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public long number(String key, long fallback) {
        Object value = options.get(key);
        if (value instanceof Number number) return number.longValue();
        if (value == null) return fallback;
        try { return Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    public List<String> strings(String key) {
        Object value = options.get(key);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object entry : list) if (entry != null) result.add(normalizeTarget(String.valueOf(entry)));
            return result;
        }
        if (value == null || String.valueOf(value).isBlank()) return Collections.emptyList();
        return List.of(normalizeTarget(String.valueOf(value)));
    }

    public boolean targetMatches(String value) {
        if (value == null) return false;
        List<String> targets = strings("targets");
        if (targets.isEmpty()) return true;
        String normalized = normalizeTarget(value);
        return targets.contains(normalized) || targets.contains("*");
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public static String normalizeTarget(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
