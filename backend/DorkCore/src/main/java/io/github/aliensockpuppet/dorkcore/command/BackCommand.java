package io.github.aliensockpuppet.dorkcore.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

public final class BackCommand implements CommandExecutor {
    private final CoreServices services;
    private final Guard guard;
    private final Teleports teleports;

    public BackCommand(CoreServices services, Guard guard, Teleports teleports) {
        this.services = services;
        this.guard = guard;
        this.teleports = teleports;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("dorkcore.back")) {
            services.messages().send(player, "no-permission");
            services.sounds().play(player, "error");
            return true;
        }
        if (!guard.readyAndOffCooldown(player, "back")) {
            return true;
        }
        Location destination = services.back().get(player.getUniqueId());
        if (destination == null) {
            services.messages().send(player, "back-none");
            services.sounds().play(player, "error");
            return true;
        }
        if (destination.getWorld() == null) {
            services.messages().send(player, "back-world-missing");
            services.sounds().play(player, "error");
            return true;
        }
        teleports.teleport(player, destination, "back",
                services.config().rollCooldownMillis("back", services.config().get().backCooldownMillis() / 1000L),
                "back-teleported", "teleport");
        return true;
    }
}
