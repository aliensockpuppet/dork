package io.github.aliensockpuppet.dorkcore.api;

import org.bukkit.entity.Player;

public interface AuthGate {
    boolean isAuthenticated(Player player);
}
