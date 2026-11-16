package io.github.aliensockpuppet.dorkcore.command;

import org.bukkit.entity.Player;

import java.util.UUID;

public final class Guard {
    private final CoreServices services;

    public Guard(CoreServices services) {
        this.services = services;
    }

    public boolean ready(Player player) {
        if (!services.authGate().isAuthenticated(player)) {
            services.messages().send(player, "not-authenticated");
            services.sounds().play(player, "error");
            return false;
        }
        if (!services.config().get().worldAllowed(player.getWorld())) {
            services.messages().send(player, "world-not-allowed");
            services.sounds().play(player, "error");
            return false;
        }
        if (services.combatTag().isTagged(player)) {
            long seconds = (services.combatTag().remainingMillis(player) + 999L) / 1000L;
            services.messages().send(player, "combat-tagged", "%seconds%", String.valueOf(seconds));
            services.sounds().play(player, "error");
            return false;
        }
        return true;
    }

    public boolean offCooldown(Player player, String key) {
        UUID id = player.getUniqueId();
        long left = services.cooldowns().remaining(id, key);
        if (left > 0L) {
            long seconds = (left + 999L) / 1000L;
            services.messages().send(player, "cooldown", "%seconds%", String.valueOf(seconds));
            services.sounds().play(player, "error");
            return false;
        }
        return true;
    }

    public boolean readyAndOffCooldown(Player player, String cooldownKey) {
        return ready(player) && offCooldown(player, cooldownKey);
    }
}
