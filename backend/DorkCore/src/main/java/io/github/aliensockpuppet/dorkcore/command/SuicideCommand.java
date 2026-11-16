package io.github.aliensockpuppet.dorkcore.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

public final class SuicideCommand implements CommandExecutor {
    private final CoreServices services;
    private final Guard guard;

    public SuicideCommand(CoreServices services, Guard guard) {
        this.services = services;
        this.guard = guard;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("dorkcore.suicide")) {
            services.messages().send(player, "no-permission");
            services.sounds().play(player, "error");
            return true;
        }
        if (!services.authGate().isAuthenticated(player)) {
            services.messages().send(player, "not-authenticated");
            services.sounds().play(player, "error");
            return true;
        }
        if (player.isDead() || player.getHealth() <= 0.0) {
            return true;
        }
        if (!guard.offCooldown(player, "suicide")) {
            return true;
        }

        services.cooldowns().start(player.getUniqueId(), "suicide",
                services.config().rollCooldownMillis("suicide", 3));

        EntityDamageEvent cause = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.SUICIDE, Float.MAX_VALUE);
        player.setLastDamageCause(cause);
        player.setHealth(0.0);
        return true;
    }
}
