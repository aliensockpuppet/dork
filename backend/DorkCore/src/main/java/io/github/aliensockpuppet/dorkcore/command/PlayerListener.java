package io.github.aliensockpuppet.dorkcore.command;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;
import io.github.aliensockpuppet.dorkcore.internal.store.StoredLocation;

import java.sql.SQLException;
import java.util.UUID;

public final class PlayerListener implements Listener {
    private final CoreServices services;
    private final TpaManager tpa;
    private final MessageCommand messages;

    public PlayerListener(CoreServices services, TpaManager tpa, MessageCommand messages) {
        this.services = services;
        this.tpa = tpa;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (services.config().get().backRecordOnDeath()) {
            services.back().record(id, event.getEntity().getLocation());
        }

        net.kyori.adventure.text.Component line = event.deathMessage();
        event.deathMessage(null);
        if (line != null) {
            sendToViewers(line, PlayerToggles.Toggle.DEATH_MESSAGES, null);
        }
    }

    private void sendToViewers(net.kyori.adventure.text.Component line,
                               PlayerToggles.Toggle toggle, UUID exclude) {
        for (org.bukkit.entity.Player viewer : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (exclude != null && viewer.getUniqueId().equals(exclude)) {
                continue;
            }
            if (services.playerToggles().isOn(viewer.getUniqueId(), toggle)) {
                viewer.sendMessage(line);
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!services.config().get().respawnAtSpawn()) {
            return;
        }
        if (event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH
                || event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        Location spawn = spawnLocation();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinLoadToggles(PlayerJoinEvent event) {
        services.playerToggles().load(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinBroadcast(PlayerJoinEvent event) {
        net.kyori.adventure.text.Component line = event.joinMessage();
        event.joinMessage(null);
        if (line != null) {
            sendToViewers(line, PlayerToggles.Toggle.JOIN_LEAVE_MESSAGES, null);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!services.config().get().firstJoinAtSpawn() || event.getPlayer().hasPlayedBefore()) {
            return;
        }
        Location spawn = spawnLocation();
        if (spawn != null) {
            event.getPlayer().teleport(spawn);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        net.kyori.adventure.text.Component line = event.quitMessage();
        event.quitMessage(null);
        if (line != null) {
            sendToViewers(line, PlayerToggles.Toggle.JOIN_LEAVE_MESSAGES, id);
        }
        services.cooldowns().forget(id);
        services.back().forget(id);
        tpa.forget(id);
        messages.forget(id);
        services.playerToggles().unload(id);
    }

    private Location spawnLocation() {
        try {
            StoredLocation stored = services.spawn().get();
            return stored == null ? null : stored.toLocation();
        } catch (SQLException e) {
            services.logStoreError("read spawn on respawn or join", e);
            return null;
        }
    }
}
