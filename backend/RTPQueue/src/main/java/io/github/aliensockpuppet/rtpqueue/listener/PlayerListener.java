package io.github.aliensockpuppet.rtpqueue.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import io.github.aliensockpuppet.rtpqueue.RTPQueuePlugin;

public final class PlayerListener implements Listener {
    private final RTPQueuePlugin plugin;

    public PlayerListener(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.queue().onQuit(event.getPlayer().getUniqueId());
    }
}
