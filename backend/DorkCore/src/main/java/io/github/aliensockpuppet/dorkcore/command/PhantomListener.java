package io.github.aliensockpuppet.dorkcore.command;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;

public final class PhantomListener implements Listener {
    private final CoreServices services;

    public PhantomListener(CoreServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPhantomPreSpawn(PhantomPreSpawnEvent event) {
        Entity trigger = event.getSpawningEntity();
        if (!(trigger instanceof Player player)) {
            return;
        }
        if (!services.playerToggles().isOn(player.getUniqueId(), PlayerToggles.Toggle.PHANTOM_SPAWNS)) {
            event.setShouldAbortSpawn(true);
            event.setCancelled(true);
        }
    }
}
