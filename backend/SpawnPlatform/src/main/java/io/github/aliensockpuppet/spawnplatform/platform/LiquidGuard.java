package io.github.aliensockpuppet.spawnplatform.platform;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import io.github.aliensockpuppet.spawnplatform.SpawnPlatformPlugin;

public final class LiquidGuard implements Listener {
    private static final long REFRESH_MILLIS = 5_000L;

    private final SpawnPlatformPlugin plugin;
    private volatile PlatformService.Zone zone;
    private volatile long zoneAt;

    public LiquidGuard(SpawnPlatformPlugin plugin) {
        this.plugin = plugin;
    }

    public void refresh() {
        zoneAt = 0L;
    }

    private boolean protectedBlock(Block b) {
        if (b == null || !plugin.settings().blockLiquids()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - zoneAt > REFRESH_MILLIS) {
            zone = plugin.service().zone(plugin.settings());
            zoneAt = now;
        }
        PlatformService.Zone z = zone;
        return z != null && z.contains(b);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (protectedBlock(event.getBlock()) || protectedBlock(event.getBlockClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (protectedBlock(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Material item = event.getItem().getType();
        if (item == Material.BUCKET || !item.name().endsWith("_BUCKET")) {
            return;
        }
        if (!(event.getBlock().getBlockData() instanceof Directional facing)) {
            return;
        }
        if (protectedBlock(event.getBlock().getRelative(facing.getFacing()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelt(BlockFadeEvent event) {
        if (event.getBlock().getType() == Material.ICE && protectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIceBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (b.getType() != Material.ICE || !protectedBlock(b)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (b.getType() == Material.WATER) {
                b.setType(Material.AIR, false);
            }
        });
    }
}
