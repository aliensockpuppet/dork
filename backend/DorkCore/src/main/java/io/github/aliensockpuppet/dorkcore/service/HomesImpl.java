package io.github.aliensockpuppet.dorkcore.service;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.aliensockpuppet.dorkcore.api.Homes;
import io.github.aliensockpuppet.dorkcore.internal.store.HomeStore;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

public final class HomesImpl implements Homes {
    private final JavaPlugin plugin;
    private final HomeStore store;

    public HomesImpl(JavaPlugin plugin, HomeStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public List<String> names(Player player) {
        if (player == null) {
            return List.of();
        }
        try {
            List<String> names = store.names(player.getUniqueId());
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return names;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read homes for " + player.getName(), e);
            return List.of();
        }
    }
}
