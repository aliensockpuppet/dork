package io.github.aliensockpuppet.dorkcore.service;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BackTracker {
    private final Map<UUID, Location> previous = new ConcurrentHashMap<>();

    public void record(UUID player, Location location) {
        if (location != null && location.getWorld() != null) {
            previous.put(player, location.clone());
        }
    }

    public Location get(UUID player) {
        Location loc = previous.get(player);
        return loc == null ? null : loc.clone();
    }

    public boolean has(UUID player) {
        return previous.containsKey(player);
    }

    public void forget(UUID player) {
        previous.remove(player);
    }
}
