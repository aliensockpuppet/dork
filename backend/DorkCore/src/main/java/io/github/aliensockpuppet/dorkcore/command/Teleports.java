package io.github.aliensockpuppet.dorkcore.command;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class Teleports {
    private final CoreServices services;

    public Teleports(CoreServices services) {
        this.services = services;
    }

    public void teleport(Player player, Location destination, String cooldownKey,
                         long cooldownMillis, String doneKey, String soundKey, String... pairs) {
        teleport(player, destination, cooldownKey, cooldownMillis, doneKey, soundKey, false, pairs);
    }

    public void teleport(Player player, Location destination, String cooldownKey,
                         long cooldownMillis, String doneKey, String soundKey,
                         boolean clearPocket, String... pairs) {
        if (services.config().get().backRecordOnTeleport()) {
            services.back().record(player.getUniqueId(), player.getLocation());
        }
        player.teleport(destination);
        if (clearPocket) {
            clearPocket(destination);
        }
        services.cooldowns().start(player.getUniqueId(), cooldownKey, cooldownMillis);
        if (doneKey != null) {
            services.messages().send(player, doneKey, pairs);
        }
        if (soundKey != null) {
            services.sounds().play(player, soundKey);
        }
    }

    private void clearPocket(Location center) {
        org.bukkit.World world = center.getWorld();
        if (world == null) {
            return;
        }
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                for (int y = cy; y <= cy + 2; y++) {
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == org.bukkit.Material.BEDROCK || block.getType().isAir()) {
                        continue;
                    }
                    block.setType(org.bukkit.Material.AIR, false);
                }
            }
        }
    }
}
