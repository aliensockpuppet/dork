package io.github.aliensockpuppet.spawnplatform.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;
import io.github.aliensockpuppet.spawnplatform.config.Settings;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;

public final class PlatformService {
    private final JavaPlugin plugin;
    private final DorkCoreApi core;
    private final Logger log;
    private boolean clearing;

    public PlatformService(JavaPlugin plugin, DorkCoreApi core) {
        this.plugin = plugin;
        this.core = core;
        this.log = plugin.getLogger();
    }

    private record Anchor(World world, int cx, int cz, int floorY) {
    }

    private Anchor resolve(Settings s) {
        World world = Bukkit.getWorld(s.worldName());
        if (world == null) {
            log.warning("World '" + s.worldName() + "' is not loaded. SpawnPlatform did nothing.");
            return null;
        }
        Location spawn = world.getSpawnLocation();
        int cx = s.useWorldSpawn() ? spawn.getBlockX() : s.centerX();
        int cz = s.useWorldSpawn() ? spawn.getBlockZ() : s.centerZ();
        int floorY = s.followSpawnY() ? spawn.getBlockY() - 1 : s.platformY();
        floorY = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, floorY));
        return new Anchor(world, cx, cz, floorY);
    }

    public record Zone(World world, int minX, int maxX, int minZ, int maxZ, int minY) {
        public boolean contains(Block b) {
            return b.getWorld().equals(world)
                    && b.getX() >= minX && b.getX() <= maxX
                    && b.getZ() >= minZ && b.getZ() <= maxZ
                    && b.getY() >= minY;
        }
    }

    public Zone zone(Settings s) {
        World world = Bukkit.getWorld(s.worldName());
        if (world == null) {
            return null;
        }
        Location spawn = world.getSpawnLocation();
        int cx = s.useWorldSpawn() ? spawn.getBlockX() : s.centerX();
        int cz = s.useWorldSpawn() ? spawn.getBlockZ() : s.centerZ();
        int floorY = s.followSpawnY() ? spawn.getBlockY() - 1 : s.platformY();
        floorY = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, floorY));
        int r = Math.max(s.clearRadius(), s.platformRadius());
        return new Zone(world, cx - r, cx + r, cz - r, cz + r, floorY + 1);
    }

    public boolean buildPlatform(Settings s) {
        Anchor a = resolve(s);
        if (a == null) {
            return false;
        }
        layFloor(s, a);
        return true;
    }

    public boolean clearArea(Settings s) {
        if (clearing) {
            return false;
        }
        Anchor a = resolve(s);
        if (a == null) {
            return false;
        }
        int r = Math.max(s.clearRadius(), s.platformRadius());
        int topY = s.clearToBuildHeight()
                ? a.world().getMaxHeight() - 1
                : Math.min(a.world().getMaxHeight() - 1, a.floorY() + s.clearHeight());
        int bottomY = a.floorY() + 1;
        if (topY < bottomY) {
            if (s.rebuildPlatform()) {
                layFloor(s, a);
            }
            clearEntities(s, a, r);
            announce(s, a, r);
            return true;
        }

        Deque<long[]> columns = new ArrayDeque<>();
        for (int x = a.cx() - r; x <= a.cx() + r; x++) {
            for (int z = a.cz() - r; z <= a.cz() + r; z++) {
                columns.add(new long[]{x, z});
            }
        }
        clearing = true;
        new ClearTask(s, a, bottomY, topY, r, columns).runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private final class ClearTask extends BukkitRunnable {
        private final Settings s;
        private final Anchor a;
        private final int bottomY;
        private final int topY;
        private final int r;
        private final Deque<long[]> columns;

        ClearTask(Settings s, Anchor a, int bottomY, int topY, int r, Deque<long[]> columns) {
            this.s = s;
            this.a = a;
            this.bottomY = bottomY;
            this.topY = topY;
            this.r = r;
            this.columns = columns;
        }

        @Override
        public void run() {
            int budget = s.columnsPerTick();
            World world = a.world();
            for (int done = 0; done < budget && !columns.isEmpty(); done++) {
                long[] col = columns.poll();
                int x = (int) col[0];
                int z = (int) col[1];
                for (int y = bottomY; y <= topY; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
            if (columns.isEmpty()) {
                finish();
            }
        }

        private void finish() {
            cancel();
            clearing = false;
            clearEntities(s, a, r);
            if (s.rebuildPlatform()) {
                layFloor(s, a);
            }
            announce(s, a, r);
        }
    }

    private void layFloor(Settings s, Anchor a) {
        Material mat = s.material();
        for (int x = a.cx() - s.platformRadius(); x <= a.cx() + s.platformRadius(); x++) {
            for (int z = a.cz() - s.platformRadius(); z <= a.cz() + s.platformRadius(); z++) {
                Block b = a.world().getBlockAt(x, a.floorY(), z);
                if (b.getType() != mat) {
                    b.setType(mat, false);
                }
            }
        }
    }

    private void clearEntities(Settings s, Anchor a, int r) {
        if (!s.clearDroppedItems() && s.clearEntities().isEmpty()) {
            return;
        }
        int topY = s.clearToBuildHeight()
                ? a.world().getMaxHeight() - 1
                : Math.min(a.world().getMaxHeight() - 1, a.floorY() + s.clearHeight());
        double height = Math.max(1, topY - a.floorY());
        double halfHeight = height / 2.0 + 1;
        Location center = new Location(a.world(), a.cx() + 0.5, a.floorY() + halfHeight, a.cz() + 0.5);
        for (Entity e : a.world().getNearbyEntities(center, r + 1, halfHeight + 1, r + 1)) {
            if (e instanceof Player) {
                continue;
            }
            if (e instanceof Item && s.clearDroppedItems()) {
                e.remove();
            } else if (s.clearEntities().contains(e.getType())) {
                e.remove();
            }
        }
    }

    private void announce(Settings s, Anchor a, int r) {
        String msg = s.clearMessage();
        if (msg == null || msg.isBlank()) {
            return;
        }
        double reach = r + 2;
        for (Player p : a.world().getPlayers()) {
            Location l = p.getLocation();
            if (Math.abs(l.getX() - (a.cx() + 0.5)) <= reach && Math.abs(l.getZ() - (a.cz() + 0.5)) <= reach) {
                boolean modern = core.playerToggles().isOn(p.getUniqueId(),
                        io.github.aliensockpuppet.dorkcore.api.PlayerToggles.Toggle.MESSAGE_STYLE);
                if (modern) {
                    p.sendActionBar(core.colors().render(msg));
                } else {
                    p.sendMessage(core.colors().render(msg));
                }
            }
        }
    }
}
