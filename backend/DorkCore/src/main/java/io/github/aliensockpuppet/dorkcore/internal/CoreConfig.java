package io.github.aliensockpuppet.dorkcore.internal;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CoreConfig {
    private volatile Settings settings = Settings.DEFAULTS;
    private final CooldownSettings cooldowns = new CooldownSettings();

    public Settings get() {
        return settings;
    }

    public CooldownSettings cooldownSettings() {
        return cooldowns;
    }

    public long rollCooldownMillis(String key, long defaultSeconds) {
        return cooldowns.rollMillis(key, defaultSeconds);
    }

    public void load(FileConfiguration yaml) {
        cooldowns.load(yaml);

        long tpaCooldown = clampMillis(yaml, "cooldowns.tpa-seconds", 5);
        long homeCooldown = clampMillis(yaml, "cooldowns.home-seconds", 3);
        long spawnCooldown = clampMillis(yaml, "cooldowns.spawn-seconds", 3);
        long backCooldown = clampMillis(yaml, "cooldowns.back-seconds", 3);

        long tpaExpiry = Math.max(5L, yaml.getLong("tpa.expiry-seconds", 60)) * 1000L;

        int defaultHomeCap = Math.max(0, yaml.getInt("homes.default-cap", 3));
        int maxHomeNameLength = clampInt(yaml.getInt("homes.max-name-length", 16), 1, 32);

        boolean respawnAtSpawn = yaml.getBoolean("spawn.respawn-at-spawn", true);
        boolean firstJoinAtSpawn = yaml.getBoolean("spawn.first-join-at-spawn", true);
        boolean autoSetOnFirstLoad = yaml.getBoolean("spawn.auto-set-on-first-load", true);

        Set<String> allowedWorlds = lowerSet(yaml.getStringList("worlds.teleport-allowed"));

        boolean failClosed = yaml.getBoolean("gate.fail-closed-when-auth-missing", true);

        boolean backRecordOnDeath = yaml.getBoolean("back.record-on-death", false);

        boolean backRecordOnTeleport = yaml.getBoolean("back.record-on-teleport", false);

        this.settings = new Settings(
                tpaCooldown, homeCooldown, spawnCooldown, backCooldown,
                tpaExpiry, defaultHomeCap, maxHomeNameLength,
                respawnAtSpawn, firstJoinAtSpawn, autoSetOnFirstLoad, allowedWorlds, failClosed,
                backRecordOnDeath, backRecordOnTeleport);
    }

    private static long clampMillis(FileConfiguration yaml, String path, long defaultSeconds) {
        long seconds = yaml.getLong(path, defaultSeconds);
        return Math.max(0L, seconds) * 1000L;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Set<String> lowerSet(List<String> list) {
        Set<String> out = new HashSet<>();
        for (String s : list) {
            if (s != null && !s.isBlank()) {
                out.add(s.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    public record Settings(
            long tpaCooldownMillis,
            long homeCooldownMillis,
            long spawnCooldownMillis,
            long backCooldownMillis,
            long tpaExpiryMillis,
            int defaultHomeCap,
            int maxHomeNameLength,
            boolean respawnAtSpawn,
            boolean firstJoinAtSpawn,
            boolean autoSetSpawnOnFirstLoad,
            Set<String> teleportAllowedWorlds,
            boolean failClosedWhenAuthMissing,
            boolean backRecordOnDeath,
            boolean backRecordOnTeleport) {
        static final Settings DEFAULTS = new Settings(
                5000L, 3000L, 3000L, 3000L,
                60000L, 3, 16,
                true, true, true, Set.of(), true,
                false, false);

        public boolean worldAllowed(World world) {
            if (teleportAllowedWorlds.isEmpty()) {
                return true;
            }
            return teleportAllowedWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
        }
    }
}
