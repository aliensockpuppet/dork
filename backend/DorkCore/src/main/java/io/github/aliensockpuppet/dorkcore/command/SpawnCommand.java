package io.github.aliensockpuppet.dorkcore.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.dorkcore.internal.CoreConfig;
import io.github.aliensockpuppet.dorkcore.internal.store.StoredLocation;

import java.sql.SQLException;

public final class SpawnCommand implements CommandExecutor {
    private final CoreServices services;
    private final Guard guard;
    private final Teleports teleports;

    public SpawnCommand(CoreServices services, Guard guard, Teleports teleports) {
        this.services = services;
        this.guard = guard;
        this.teleports = teleports;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setspawn")) {
            return setSpawn(sender);
        }
        return goToSpawn(sender);
    }

    private boolean setSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("dorkcore.setspawn")) {
            services.messages().send(player, "no-permission");
            services.sounds().play(player, "error");
            return true;
        }
        try {
            services.spawn().set(StoredLocation.of(player.getLocation()));
        } catch (SQLException e) {
            services.messages().send(player, "store-error");
            services.sounds().play(player, "error");
            services.logStoreError("set spawn", e);
            return true;
        }
        services.messages().send(player, "spawn-set");
        services.sounds().play(player, "success");
        return true;
    }

    private boolean goToSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("dorkcore.spawn")) {
            services.messages().send(player, "no-permission");
            services.sounds().play(player, "error");
            return true;
        }
        CoreConfig.Settings settings = services.config().get();
        if (!guard.readyAndOffCooldown(player, "spawn")) {
            return true;
        }
        StoredLocation stored;
        try {
            stored = services.spawn().get();
        } catch (SQLException e) {
            services.messages().send(player, "store-error");
            services.sounds().play(player, "error");
            services.logStoreError("read spawn", e);
            return true;
        }
        if (stored == null) {
            services.messages().send(player, "spawn-not-set");
            services.sounds().play(player, "error");
            return true;
        }
        Location destination = stored.toLocation();
        if (destination == null) {
            services.messages().send(player, "spawn-world-missing");
            services.sounds().play(player, "error");
            return true;
        }
        teleports.teleport(player, destination, "spawn",
                services.config().rollCooldownMillis("spawn", settings.spawnCooldownMillis() / 1000L),
                "spawn-teleported", "teleport", true);
        return true;
    }
}
