package io.github.aliensockpuppet.dorkcore.command;

import org.bukkit.plugin.java.JavaPlugin;
import io.github.aliensockpuppet.dorkcore.internal.CoreConfig;
import io.github.aliensockpuppet.dorkcore.internal.store.HomeStore;
import io.github.aliensockpuppet.dorkcore.internal.store.IgnoreStore;
import io.github.aliensockpuppet.dorkcore.internal.store.SpawnStore;
import io.github.aliensockpuppet.dorkcore.service.AuthGateImpl;
import io.github.aliensockpuppet.dorkcore.service.BackTracker;
import io.github.aliensockpuppet.dorkcore.service.ColorsImpl;
import io.github.aliensockpuppet.dorkcore.service.CombatTagImpl;
import io.github.aliensockpuppet.dorkcore.service.CooldownsImpl;
import io.github.aliensockpuppet.dorkcore.service.MessagesImpl;
import io.github.aliensockpuppet.dorkcore.service.PlayerTogglesImpl;
import io.github.aliensockpuppet.dorkcore.service.SoundsImpl;

import java.util.logging.Level;

public record CoreServices(
        JavaPlugin plugin,
        MessagesImpl messages,
        SoundsImpl sounds,
        ColorsImpl colors,
        CooldownsImpl cooldowns,
        CombatTagImpl combatTag,
        AuthGateImpl authGate,
        PlayerTogglesImpl playerToggles,
        CoreConfig config,
        HomeStore homes,
        SpawnStore spawn,
        IgnoreStore ignores,
        BackTracker back) {
    public void logStoreError(String action, Throwable cause) {
        plugin.getLogger().log(Level.SEVERE, "Could not " + action + " in the data file.", cause);
    }
}
