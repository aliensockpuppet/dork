package io.github.aliensockpuppet.dorkcore.internal;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class CooldownSettings {
    public record Spec(long minMillis, long maxMillis) {
        public static Spec fixed(long millis) {
            long m = Math.max(0L, millis);
            return new Spec(m, m);
        }

        public static Spec range(long a, long b) {
            long lo = Math.max(0L, Math.min(a, b));
            long hi = Math.max(0L, Math.max(a, b));
            return new Spec(lo, hi);
        }

        public boolean isFixed() {
            return minMillis == maxMillis;
        }

        public long roll() {
            if (isFixed()) {
                return minMillis;
            }
            return ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1L);
        }
    }

    private volatile Snapshot snapshot = Snapshot.EMPTY;

    public long rollMillis(String key, long defaultSeconds) {
        Snapshot s = snapshot;
        Spec spec = s.byKey.get(key.toLowerCase(Locale.ROOT));
        if (spec == null) {
            spec = s.global;
        }
        if (spec == null) {
            spec = Spec.fixed(Math.max(0L, defaultSeconds) * 1000L);
        }
        return spec.roll();
    }

    public boolean isRandom(String key) {
        Snapshot s = snapshot;
        Spec spec = s.byKey.get(key.toLowerCase(Locale.ROOT));
        if (spec == null) {
            spec = s.global;
        }
        return spec != null && !spec.isFixed();
    }

    public void load(FileConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection("cooldowns");
        if (section == null) {
            this.snapshot = Snapshot.EMPTY;
            return;
        }
        Spec global = null;
        Map<String, Spec> byKey = new HashMap<>();
        for (String rawKey : section.getKeys(false)) {
            Spec spec = readSpec(section, rawKey);
            if (spec == null) {
                continue;
            }
            String key = normalize(rawKey);
            if (key.equals("global")) {
                global = spec;
            } else {
                byKey.put(key, spec);
            }
        }
        this.snapshot = new Snapshot(global, byKey);
    }

    private Spec readSpec(ConfigurationSection parent, String key) {
        if (parent.isConfigurationSection(key)) {
            ConfigurationSection sub = parent.getConfigurationSection(key);
            if (sub == null) {
                return null;
            }
            if (sub.contains("min") || sub.contains("max")) {
                long min = sub.getLong("min", sub.getLong("max", 0L));
                long max = sub.getLong("max", sub.getLong("min", 0L));
                return Spec.range(min * 1000L, max * 1000L);
            }
            if (sub.contains("seconds")) {
                return Spec.fixed(Math.max(0L, sub.getLong("seconds", 0L)) * 1000L);
            }
            return null;
        }
        if (parent.isLong(key) || parent.isInt(key)) {
            return Spec.fixed(Math.max(0L, parent.getLong(key, 0L)) * 1000L);
        }
        return null;
    }

    private static String normalize(String rawKey) {
        String key = rawKey.toLowerCase(Locale.ROOT);
        if (key.endsWith("-seconds")) {
            key = key.substring(0, key.length() - "-seconds".length());
        }
        return key;
    }

    private record Snapshot(Spec global, Map<String, Spec> byKey) {
        static final Snapshot EMPTY = new Snapshot(null, Map.of());
    }
}
