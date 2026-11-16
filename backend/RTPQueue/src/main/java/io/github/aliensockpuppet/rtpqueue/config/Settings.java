package io.github.aliensockpuppet.rtpqueue.config;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import io.github.aliensockpuppet.rtpqueue.api.QueueMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record Settings(
        Set<String> allowedWorlds,
        RtpSettings rtp,
        List<QueueMode> modes,
        GearSettings gear,
        int matchmakingIntervalTicks,
        int gearCacheTicks,
        int glowSeconds,
        boolean broadcastEnabled,
        long broadcastMinIntervalMillis,
        List<String> onMatchCommands) {
    private static final Pattern MODE_ID = Pattern.compile("[a-z0-9_-]{1,24}");

    public Optional<QueueMode> mode(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String key = input.trim().toLowerCase(Locale.ROOT);
        for (QueueMode m : modes) {
            if (m.id().equals(key)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    public boolean worldAllowed(World world) {
        return allowedWorlds.isEmpty()
                || (world != null && allowedWorlds.contains(world.getName().toLowerCase(Locale.ROOT)));
    }

    public String modeIds() {
        return modes.stream().map(QueueMode::id).collect(Collectors.joining(", "));
    }

    public static Settings from(FileConfiguration c, Logger log) {
        Set<String> worlds = c.getStringList("allowed-worlds").stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());

        int minRadius = Math.max(0, c.getInt("rtp.min-radius"));
        int maxRadius = Math.max(1, c.getInt("rtp.max-radius"));
        if (minRadius > maxRadius) {
            log.warning("rtp.min-radius is bigger than rtp.max-radius. Swapping them.");
            int t = minRadius;
            minRadius = maxRadius;
            maxRadius = t;
        }
        int nearMin = Math.max(1, c.getInt("rtp.nearby-min-distance"));
        int nearMax = Math.max(1, c.getInt("rtp.nearby-max-distance"));
        if (nearMin > nearMax) {
            log.warning("rtp.nearby-min-distance is bigger than rtp.nearby-max-distance. Swapping them.");
            int t = nearMin;
            nearMin = nearMax;
            nearMax = t;
        }
        Set<Material> unsafe = EnumSet.noneOf(Material.class);
        for (String name : c.getStringList("rtp.unsafe-blocks")) {
            Material m = Material.matchMaterial(name);
            if (m == null) {
                log.warning("Unknown block in rtp.unsafe-blocks: " + name);
            } else {
                unsafe.add(m);
            }
        }
        Set<String> biomes = new HashSet<>();
        for (String name : c.getStringList("rtp.blocked-biomes")) {
            String b = name.toLowerCase(Locale.ROOT);
            biomes.add(b.startsWith("minecraft:") ? b.substring("minecraft:".length()) : b);
        }
        RtpSettings rtp = new RtpSettings(
                c.getString("rtp.world", "world"),
                c.getInt("rtp.center-x"),
                c.getInt("rtp.center-z"),
                minRadius,
                maxRadius,
                clamp(c.getInt("rtp.max-attempts"), 1, 50),
                nearMin,
                nearMax,
                clamp(c.getInt("rtp.nearby-attempts"), 1, 30),
                clamp(c.getInt("rtp.nearby-max-height-difference"), 1, 64),
                Collections.unmodifiableSet(unsafe),
                Collections.unmodifiableSet(biomes));

        List<QueueMode> modes = new ArrayList<>();
        ConfigurationSection modeSection = c.getConfigurationSection("queue.modes");
        if (modeSection != null) {
            for (String rawId : modeSection.getKeys(false)) {
                ConfigurationSection m = modeSection.getConfigurationSection(rawId);
                if (m == null) {
                    continue;
                }
                String id = rawId.toLowerCase(Locale.ROOT);
                if (!MODE_ID.matcher(id).matches()) {
                    log.warning("Skipping queue mode '" + rawId + "'. Use letters, numbers, _ or - only (24 max).");
                    continue;
                }
                int players = m.getInt("players", 2);
                if (players < 2 || players > 32) {
                    log.warning("queue.modes." + rawId + ".players must be 2 to 32. Clamping it.");
                    players = clamp(players, 2, 32);
                }
                boolean noElytra = m.getBoolean("no-elytra", false);
                boolean noEnderChest = m.getBoolean("no-ender-chest", false);
                modes.add(new QueueMode(id, m.getString("name", rawId), players, noElytra, noEnderChest));
            }
        }
        if (modes.isEmpty()) {
            log.warning("No queue modes are set in config.yml, so the queue is off.");
        }

        int helmet = Math.max(0, c.getInt("matchmaking.slot-weights.helmet"));
        int chest = Math.max(0, c.getInt("matchmaking.slot-weights.chestplate"));
        int legs = Math.max(0, c.getInt("matchmaking.slot-weights.leggings"));
        int boots = Math.max(0, c.getInt("matchmaking.slot-weights.boots"));
        if (helmet + chest + legs + boots == 0) {
            log.warning("All matchmaking.slot-weights are 0. Using 3, 8, 6, 3.");
            helmet = 3;
            chest = 8;
            legs = 6;
            boots = 3;
        }
        String mode = c.getString("matchmaking.explosives.mode", "ANY").trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("ANY") && !mode.equals("BOTH")) {
            log.warning("matchmaking.explosives.mode must be ANY or BOTH. Using ANY.");
            mode = "ANY";
        }
        GearSettings gear = new GearSettings(
                c.getBoolean("matchmaking.gear-brackets"),
                helmet, chest, legs, boots,
                clampD(c.getDouble("matchmaking.min-coverage"), 0.05, 1.0),
                c.getBoolean("matchmaking.scan-carried-containers"),
                c.getBoolean("matchmaking.count-ender-chest-when-carried"),
                c.getBoolean("matchmaking.warn-ender-chest"),
                c.getBoolean("matchmaking.explosives.enabled"),
                mode.equals("BOTH"),
                Math.max(1, c.getInt("matchmaking.explosives.min-crystals")),
                c.getBoolean("matchmaking.explosives.crystals-need-obsidian"),
                Math.max(1, c.getInt("matchmaking.explosives.min-obsidian")),
                Math.max(1, c.getInt("matchmaking.explosives.min-anchors")),
                c.getBoolean("matchmaking.explosives.anchors-need-glowstone"),
                Math.max(1, c.getInt("matchmaking.explosives.min-glowstone")),
                c.getBoolean("matchmaking.explosives.ignore-anchors-in-nether"),
                c.getBoolean("matchmaking.explosives.beds-count-outside-overworld"),
                c.getBoolean("matchmaking.match-elytra", true),
                Math.max(1, c.getInt("matchmaking.min-elytra")),
                c.getString("matchmaking.bracket-names.netherite", "Netherite"),
                c.getString("matchmaking.bracket-names.diamond", "Diamond"),
                c.getString("matchmaking.bracket-names.low", "Under diamond"),
                c.getString("matchmaking.bracket-names.open", "Open"),
                c.getString("matchmaking.bracket-names.explosives-suffix", " with crystals or anchors"),
                c.getString("matchmaking.bracket-names.elytra-suffix", " with elytra"));

        return new Settings(
                worlds,
                rtp,
                List.copyOf(modes),
                gear,
                clamp(c.getInt("queue.matchmaking-interval-ticks"), 5, 200),
                clamp(c.getInt("queue.gear-check-cache-ticks"), 0, 200),
                clamp(c.getInt("queue.glow-seconds"), 0, 600),
                c.getBoolean("queue.broadcast.enabled", true),
                (long) clamp(c.getInt("queue.broadcast.min-interval-seconds", 2), 0, 60) * 1000L,
                List.copyOf(c.getStringList("on-success-commands.match")));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clampD(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
