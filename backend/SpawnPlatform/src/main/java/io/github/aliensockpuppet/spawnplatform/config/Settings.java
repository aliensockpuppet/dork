package io.github.aliensockpuppet.spawnplatform.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public record Settings(
        String worldName,
        boolean useWorldSpawn,
        int centerX,
        int centerZ,
        boolean followSpawnY,
        int platformY,
        int platformRadius,
        Material material,
        boolean clearEnabled,
        long clearIntervalTicks,
        int clearRadius,
        boolean clearToBuildHeight,
        int clearHeight,
        boolean clearDroppedItems,
        Set<EntityType> clearEntities,
        boolean rebuildPlatform,
        int columnsPerTick,
        String clearMessage,
        boolean runOnStart,
        boolean blockLiquids) {
    public static Settings from(FileConfiguration c, Logger log) {
        Material material = Material.matchMaterial(c.getString("platform.material", "BEDROCK"));
        if (material == null || !material.isBlock()) {
            log.warning("platform.material is not a valid block. Using BEDROCK.");
            material = Material.BEDROCK;
        }

        Set<EntityType> entities = EnumSet.noneOf(EntityType.class);
        for (String name : c.getStringList("clear.clear-entities")) {
            try {
                entities.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                log.warning("Unknown entity type in clear.clear-entities: " + name);
            }
        }

        return new Settings(
                c.getString("world", "world"),
                c.getBoolean("use-world-spawn", true),
                c.getInt("center-x"),
                c.getInt("center-z"),
                c.getBoolean("platform.follow-spawn-y", true),
                c.getInt("platform.y", 64),
                clamp(c.getInt("platform.radius", 8), 0, 64),
                material,
                c.getBoolean("clear.enabled", false),
                parseTicks(c.getString("clear.interval", "10m"), 12000L, log),
                clamp(c.getInt("clear.radius", 24), 0, 128),
                c.getBoolean("clear.to-build-height", true),
                clamp(c.getInt("clear.height", 64), 1, 384),
                c.getBoolean("clear.clear-dropped-items", true),
                entities,
                c.getBoolean("clear.rebuild-platform", true),
                clamp(c.getInt("clear.columns-per-tick", 256), 16, 8192),
                c.getString("clear.message", ""),
                c.getBoolean("run-on-start", false),
                c.getBoolean("clear.block-liquids", true));
    }

    public static long parseTicks(String raw, long fallbackTicks, Logger log) {
        if (raw == null || raw.isBlank()) {
            return fallbackTicks;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        try {
            char unit = s.charAt(s.length() - 1);
            if (Character.isDigit(unit)) {
                return Math.max(20L, Long.parseLong(s));
            }
            long value = Long.parseLong(s.substring(0, s.length() - 1).trim());
            long seconds = switch (unit) {
                case 's' -> value;
                case 'm' -> value * 60L;
                case 'h' -> value * 3600L;
                case 'd' -> value * 86400L;
                default -> throw new NumberFormatException("bad unit " + unit);
            };
            return Math.max(20L, seconds * 20L);
        } catch (RuntimeException e) {
            log.warning("Could not read a time value from '" + raw + "'. Using " + (fallbackTicks / 20) + "s.");
            return fallbackTicks;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
