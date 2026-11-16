package io.github.aliensockpuppet.dorkcombat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import io.github.aliensockpuppet.dorkcore.api.CombatTag;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatManager implements CombatTag {
    private final JavaPlugin plugin;
    private final DorkCoreApi core;
    private final long tagMillis;
    private final boolean tagExplosions;
    private final boolean exemptKicks;

    private final Map<UUID, Long> tagged = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> opponents = new ConcurrentHashMap<>();
    private BukkitTask countdown;

    public CombatManager(JavaPlugin plugin, DorkCoreApi core, long tagSeconds,
                         boolean tagExplosions, boolean exemptKicks) {
        this.plugin = plugin;
        this.core = core;
        this.tagMillis = Math.max(1L, tagSeconds) * 1000L;
        this.tagExplosions = tagExplosions;
        this.exemptKicks = exemptKicks;
    }

    public boolean tagExplosions() {
        return tagExplosions;
    }

    public boolean exemptKicks() {
        return exemptKicks;
    }

    public void start() {
        this.countdown = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
        tagged.clear();
    }

    public void tagPair(Player one, Player two) {
        if (one == null || two == null || one.equals(two)) {
            return;
        }
        tagOne(one);
        tagOne(two);
        if (isTagged(one) && isTagged(two)) {
            opponents.put(one.getUniqueId(), two.getUniqueId());
            opponents.put(two.getUniqueId(), one.getUniqueId());
        }
    }

    private void tagOne(Player player) {
        if (player.hasPermission("dorkcombat.bypass")) {
            return;
        }
        long expiry = System.currentTimeMillis() + tagMillis;
        boolean wasTagged = isTagged(player);
        tagged.merge(player.getUniqueId(), expiry, Math::max);
        if (!wasTagged) {
            core.messages().send(player, "combat-started");
            core.sounds().play(player, "combat-start");
        }
    }

    public UUID opponentOf(UUID player) {
        if (player == null) {
            return null;
        }
        Long expiry = tagged.get(player);
        if (expiry == null || System.currentTimeMillis() >= expiry) {
            return null;
        }
        return opponents.get(player);
    }

    public void clear(UUID player) {
        tagged.remove(player);
        opponents.remove(player);
    }

    @Override
    public void clearAll() {
        tagged.clear();
        opponents.clear();
    }

    @Override
    public boolean isTagged(Player player) {
        if (player == null) {
            return false;
        }
        Long expiry = tagged.get(player.getUniqueId());
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiry) {
            tagged.remove(player.getUniqueId(), expiry);
            opponents.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    @Override
    public long remainingMillis(Player player) {
        if (player == null) {
            return 0L;
        }
        Long expiry = tagged.get(player.getUniqueId());
        if (expiry == null) {
            return 0L;
        }
        long left = expiry - System.currentTimeMillis();
        return Math.max(0L, left);
    }

    @Override
    public void tag(Player player, String reason) {
        if (player != null) {
            tagOne(player);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : tagged.entrySet()) {
            UUID id = entry.getKey();
            Player player = Bukkit.getPlayer(id);
            if (now >= entry.getValue()) {
                tagged.remove(id, entry.getValue());
                opponents.remove(id);
                if (player != null && player.isOnline()) {
                    core.messages().send(player, "combat-ended");
                }
                continue;
            }
            if (player != null && player.isOnline()) {
                long seconds = (entry.getValue() - now + 999L) / 1000L;
                core.messages().send(player, "combat-countdown", "%seconds%", String.valueOf(seconds));
            }
        }
    }
}
