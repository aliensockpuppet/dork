package io.github.aliensockpuppet.dorkcore.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import io.github.aliensockpuppet.dorkcore.api.CombatTag;

public final class CombatTagImpl implements CombatTag {
    private CombatTag backing() {
        for (RegisteredServiceProvider<CombatTag> rsp
                : Bukkit.getServicesManager().getRegistrations(CombatTag.class)) {
            CombatTag provider = rsp.getProvider();
            if (provider != this) {
                return provider;
            }
        }
        return null;
    }

    @Override
    public boolean isTagged(Player player) {
        CombatTag backing = backing();
        return backing != null && backing.isTagged(player);
    }

    @Override
    public long remainingMillis(Player player) {
        CombatTag backing = backing();
        return backing == null ? 0L : backing.remainingMillis(player);
    }

    @Override
    public void tag(Player player, String reason) {
        CombatTag backing = backing();
        if (backing != null) {
            backing.tag(player, reason);
        }
    }

    @Override
    public void clearAll() {
        CombatTag backing = backing();
        if (backing != null) {
            backing.clearAll();
        }
    }
}
