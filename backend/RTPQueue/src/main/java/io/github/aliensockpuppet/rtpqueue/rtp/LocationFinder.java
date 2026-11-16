package io.github.aliensockpuppet.rtpqueue.rtp;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import io.github.aliensockpuppet.rtpqueue.RTPQueuePlugin;
import io.github.aliensockpuppet.rtpqueue.config.RtpSettings;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class LocationFinder {
    private final RTPQueuePlugin plugin;

    public LocationFinder(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Location> findRandom(World world, RtpSettings rs) {
        CompletableFuture<Location> out = new CompletableFuture<>();
        attemptRandom(world, rs, rs.maxAttempts(), out);
        return out;
    }

    public CompletableFuture<Location> findNear(Location anchor, RtpSettings rs) {
        CompletableFuture<Location> out = new CompletableFuture<>();
        attemptNear(anchor, rs, rs.nearbyAttempts(), out);
        return out;
    }

    private void attemptRandom(World world, RtpSettings rs, int left, CompletableFuture<Location> out) {
        if (left <= 0) {
            out.complete(null);
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double min = rs.minRadius();
        double max = rs.maxRadius();
        double distance = Math.sqrt(r.nextDouble(min * min, max * max + 1.0));
        double angle = r.nextDouble(0, Math.PI * 2);
        int x = rs.centerX() + (int) Math.round(Math.cos(angle) * distance);
        int z = rs.centerZ() + (int) Math.round(Math.sin(angle) * distance);
        loadThen(world, x, z, out, loaded -> {
            Location spot = loaded ? randomSpot(world, x, z, rs) : null;
            if (spot != null) {
                out.complete(spot);
            } else {
                attemptRandom(world, rs, left - 1, out);
            }
        });
    }

    private void attemptNear(Location anchor, RtpSettings rs, int left, CompletableFuture<Location> out) {
        World world = anchor.getWorld();
        if (left <= 0 || world == null) {
            out.complete(anchor.clone());
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double distance = rs.nearbyMin() >= rs.nearbyMax()
                ? rs.nearbyMin()
                : r.nextDouble(rs.nearbyMin(), rs.nearbyMax());
        double angle = r.nextDouble(0, Math.PI * 2);
        int x = anchor.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = anchor.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        loadThen(world, x, z, out, loaded -> {
            Location spot = loaded ? nearSpot(world, x, z, anchor.getBlockY(), rs) : null;
            if (spot != null) {
                out.complete(spot);
            } else {
                attemptNear(anchor, rs, left - 1, out);
            }
        });
    }

    private void loadThen(World world, int x, int z, CompletableFuture<Location> out, Consumer<Boolean> onMain) {
        try {
            world.getChunkAtAsync(x >> 4, z >> 4).whenComplete((chunk, error) -> {
                boolean scheduled = plugin.sync(() -> {
                    if (out.isDone()) {
                        return;
                    }
                    try {
                        onMain.accept(error == null && chunk != null);
                    } catch (Throwable t) {
                        out.complete(null);
                        plugin.getLogger().warning("Spot check failed: " + t.getMessage());
                    }
                });
                if (!scheduled) {
                    out.complete(null);
                }
            });
        } catch (Throwable t) {
            out.complete(null);
        }
    }

    private Location randomSpot(World world, int x, int z, RtpSettings rs) {
        if (!insideBorder(world, x, z)) {
            return null;
        }
        if (world.getEnvironment() == World.Environment.NETHER) {
            int top = Math.min(world.getLogicalHeight(), world.getMaxHeight()) - 3;
            return scanDown(world, x, z, top, world.getMinHeight() + 1, rs);
        }
        Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
        return standable(world, x, ground.getY(), z, rs);
    }

    private Location nearSpot(World world, int x, int z, int anchorY, RtpSettings rs) {
        if (!insideBorder(world, x, z)) {
            return null;
        }
        int diff = rs.nearbyMaxHeightDifference();
        if (world.getEnvironment() == World.Environment.NETHER) {
            return scanDown(world, x, z, anchorY + diff, anchorY - diff - 1, rs);
        }
        Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
        int feetY = ground.getY() + 1;
        if (Math.abs(feetY - anchorY) > diff) {
            return null;
        }
        return standable(world, x, ground.getY(), z, rs);
    }

    private Location scanDown(World world, int x, int z, int fromY, int toY, RtpSettings rs) {
        int start = Math.min(fromY, world.getMaxHeight() - 3);
        int end = Math.max(toY, world.getMinHeight() + 1);
        for (int y = start; y >= end; y--) {
            Location spot = standable(world, x, y, z, rs);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    private Location standable(World world, int x, int groundY, int z, RtpSettings rs) {
        if (groundY <= world.getMinHeight() || groundY + 2 >= world.getMaxHeight()) {
            return null;
        }
        Block ground = world.getBlockAt(x, groundY, z);
        Material groundType = ground.getType();
        if (!groundType.isSolid() || rs.unsafeBlocks().contains(groundType)) {
            return null;
        }
        if (!clear(world.getBlockAt(x, groundY + 1, z), rs) || !clear(world.getBlockAt(x, groundY + 2, z), rs)) {
            return null;
        }
        String biome = ground.getBiome().getKey().getKey().toLowerCase(Locale.ROOT);
        if (rs.blockedBiomes().contains(biome)) {
            return null;
        }
        return new Location(world, x + 0.5, groundY + 1, z + 0.5);
    }

    public boolean isStillSafe(Location spot, RtpSettings rs) {
        World world = spot == null ? null : spot.getWorld();
        if (world == null || !world.isChunkLoaded(spot.getBlockX() >> 4, spot.getBlockZ() >> 4)) {
            return false;
        }
        return standable(world, spot.getBlockX(), spot.getBlockY() - 1, spot.getBlockZ(), rs) != null;
    }

    private static boolean clear(Block block, RtpSettings rs) {
        return block.isPassable() && !block.isLiquid() && !rs.unsafeBlocks().contains(block.getType());
    }

    private static boolean insideBorder(World world, int x, int z) {
        return world.getWorldBorder().isInside(new Location(world, x + 0.5, 0, z + 0.5));
    }
}
