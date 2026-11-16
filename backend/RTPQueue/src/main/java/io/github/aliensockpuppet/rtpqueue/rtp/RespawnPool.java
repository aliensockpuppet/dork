package io.github.aliensockpuppet.rtpqueue.rtp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;
import io.github.aliensockpuppet.rtpqueue.RTPQueuePlugin;
import io.github.aliensockpuppet.rtpqueue.config.RtpSettings;

import java.util.ArrayDeque;
import java.util.Deque;

public final class RespawnPool implements Listener {
    private record Spot(Location location, long foundAt) {
    }

    private final RTPQueuePlugin plugin;
    private final LocationFinder finder;
    private final Deque<Spot> pool = new ArrayDeque<>();

    private boolean enabled;
    private int size;
    private long maxAgeMillis;
    private int searching;
    private BukkitTask refillTask;

    public RespawnPool(RTPQueuePlugin plugin, LocationFinder finder) {
        this.plugin = plugin;
        this.finder = finder;
    }

    public void configure(FileConfiguration c) {
        this.enabled = c.getBoolean("respawn.random-enabled", true);
        this.size = Math.max(1, Math.min(32, c.getInt("respawn.pool-size", 6)));
        this.maxAgeMillis = Math.max(1L, c.getLong("respawn.max-age-minutes", 10L)) * 60_000L;
        if (refillTask != null) {
            refillTask.cancel();
            refillTask = null;
        }
        if (!enabled) {
            clear();
            return;
        }
        refillTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refill, 40L, 600L);
    }

    public void clear() {
        for (Spot s : pool) {
            release(s);
        }
        pool.clear();
    }

    public void shutdown() {
        if (refillTask != null) {
            refillTask.cancel();
            refillTask = null;
        }
        clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!enabled || event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH
                || event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        Location spot = take();
        if (spot != null) {
            event.setRespawnLocation(spot);
        }
        refill();
    }

    private Location take() {
        RtpSettings rs = plugin.settings().rtp();
        long now = System.currentTimeMillis();
        while (!pool.isEmpty()) {
            Spot s = pool.pollFirst();
            release(s);
            if (now - s.foundAt() > maxAgeMillis) {
                continue;
            }
            if (finder.isStillSafe(s.location(), rs)) {
                return s.location().clone();
            }
        }
        return null;
    }

    private void refill() {
        if (!enabled || !plugin.isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        pool.removeIf(s -> {
            boolean old = now - s.foundAt() > maxAgeMillis;
            if (old) {
                release(s);
            }
            return old;
        });
        RtpSettings rs = plugin.settings().rtp();
        World world = Bukkit.getWorld(rs.world());
        if (world == null) {
            return;
        }
        while (pool.size() + searching < size) {
            searching++;
            finder.findRandom(world, rs).whenComplete((spot, error) -> plugin.sync(() -> {
                searching = Math.max(0, searching - 1);
                if (spot == null || !enabled || pool.size() >= size) {
                    return;
                }
                World w = spot.getWorld();
                if (w == null) {
                    return;
                }
                w.addPluginChunkTicket(spot.getBlockX() >> 4, spot.getBlockZ() >> 4, plugin);
                pool.addLast(new Spot(spot, System.currentTimeMillis()));
            }));
        }
    }

    private void release(Spot s) {
        World w = s.location().getWorld();
        if (w != null) {
            w.removePluginChunkTicket(s.location().getBlockX() >> 4, s.location().getBlockZ() >> 4, plugin);
        }
    }
}
