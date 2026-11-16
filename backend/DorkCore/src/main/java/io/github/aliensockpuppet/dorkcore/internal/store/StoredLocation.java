package io.github.aliensockpuppet.dorkcore.internal.store;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Bukkit;

import java.util.UUID;

public record StoredLocation(UUID worldUid, double x, double y, double z, float yaw, float pitch) {
    public static StoredLocation of(Location loc) {
        World world = loc.getWorld();
        return new StoredLocation(
                world == null ? null : world.getUID(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch());
    }

    public Location toLocation() {
        if (worldUid == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldUid);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }
}
