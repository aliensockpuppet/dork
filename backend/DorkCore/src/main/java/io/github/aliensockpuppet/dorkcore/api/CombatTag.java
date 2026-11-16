package io.github.aliensockpuppet.dorkcore.api;

import org.bukkit.entity.Player;

public interface CombatTag {
    boolean isTagged(Player player);

    long remainingMillis(Player player);

    void tag(Player player, String reason);

    void clearAll();
}
