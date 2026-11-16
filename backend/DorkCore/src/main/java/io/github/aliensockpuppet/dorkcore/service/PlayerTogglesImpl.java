package io.github.aliensockpuppet.dorkcore.service;

import org.bukkit.plugin.java.JavaPlugin;
import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;
import io.github.aliensockpuppet.dorkcore.internal.store.ToggleStore;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PlayerTogglesImpl implements PlayerToggles {
    private final JavaPlugin plugin;
    private final ToggleStore store;
    private final Map<UUID, Map<Toggle, Boolean>> loaded = new ConcurrentHashMap<>();

    public PlayerTogglesImpl(JavaPlugin plugin, ToggleStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void load(UUID player) {
        Map<Toggle, Boolean> values = new ConcurrentHashMap<>();
        Map<Toggle, Boolean> stored = Map.of();
        if (store != null) {
            try {
                stored = store.getAll(player);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not read toggles for " + player + ". Using defaults.", e);
            }
        }
        for (Toggle toggle : Toggle.values()) {
            values.put(toggle, stored.getOrDefault(toggle, toggle.defaultOn()));
        }
        loaded.put(player, values);
    }

    public void unload(UUID player) {
        loaded.remove(player);
    }

    @Override
    public boolean isOn(UUID player, Toggle toggle) {
        Map<Toggle, Boolean> values = loaded.get(player);
        if (values == null) {
            return toggle.defaultOn();
        }
        return values.getOrDefault(toggle, toggle.defaultOn());
    }

    @Override
    public void set(UUID player, Toggle toggle, boolean on) {
        loaded.computeIfAbsent(player, id -> new ConcurrentHashMap<>()).put(toggle, on);
        writeThrough(player, toggle, on);
    }

    @Override
    public boolean flip(UUID player, Toggle toggle) {
        boolean next = !isOn(player, toggle);
        set(player, toggle, next);
        return next;
    }

    private void writeThrough(UUID player, Toggle toggle, boolean on) {
        if (store == null) {
            return;
        }
        if (!plugin.isEnabled()) {
            persist(player, toggle, on);
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> persist(player, toggle, on));
    }

    private void persist(UUID player, Toggle toggle, boolean on) {
        try {
            store.set(player, toggle, on);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not save toggle " + toggle + " for " + player + ".", e);
        }
    }
}
